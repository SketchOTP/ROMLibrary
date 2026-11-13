package com.animus.romshelf;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.math.BigInteger;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Browser-based OpenAI OSS dynamic-client OAuth, using no Codex CLI credential copies. */
final class ChatGPTAuth {
    interface Completion { void done(String error, String email); }
    private static final String AUTH = "https://auth.openai.com";
    private static final String RESOURCE = "https://api.openai.com/v1";
    private static final String ALIAS = "rom_shelf_chatgpt_oauth_v03";
    private final Context context;
    private final SharedPreferences preferences;
    private final SecureRandom secureRandom = new SecureRandom();

    ChatGPTAuth(Context ctx) {
        context = ctx.getApplicationContext();
        preferences = context.getSharedPreferences("chatgpt_connection", Context.MODE_PRIVATE);
    }
    synchronized boolean connected() {
        try { JSONObject c = load(); return c.has("refresh_token") && hasScope(c.optString("scope", ""), "chatgpt.tokens.use.direct"); }
        catch (Exception e) { return false; }
    }
    synchronized String account() {
        try { return load().optString("email", ""); } catch (Exception e) { return ""; }
    }
    private String hostId() {
        String id = preferences.getString("host", "");
        if (!id.isEmpty()) return id;
        id = "urn:uuid:" + UUID.randomUUID();
        preferences.edit().putString("host", id).commit();
        return id;
    }
    private String randomToken(int bytes) {
        byte[] b = new byte[bytes]; secureRandom.nextBytes(b);
        return Base64.encodeToString(b, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }
    private static String urlencode(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
    private static String form(String[][] fields) throws Exception {
        StringBuilder text = new StringBuilder();
        for (String[] pair : fields) {
            if (text.length() > 0) text.append('&');
            text.append(urlencode(pair[0])).append('=').append(urlencode(pair[1]));
        }
        return text.toString();
    }
    private static String read(InputStream input) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) != -1) {
                out.write(b, 0, n);
                if (out.size() > 2 * 1024 * 1024) throw new IllegalStateException("Response too large");
            }
            return out.toString("UTF-8");
        }
    }
    private static JSONObject requestJson(String url, String encodedForm) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(25000);
        c.setRequestProperty("Accept", "application/json");
        try {
            if (encodedForm != null) {
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (OutputStream output = c.getOutputStream()) { output.write(encodedForm.getBytes(StandardCharsets.UTF_8)); }
            }
            int status = c.getResponseCode();
            InputStream input = status >= 400 ? c.getErrorStream() : c.getInputStream();
            String result = input == null ? "{}" : read(input);
            JSONObject body = new JSONObject(result);
            if (status >= 400) throw new IllegalStateException("OpenAI authentication failed (HTTP " + status + "): " + body.optString("error", "OAuth error"));
            return body;
        } finally { c.disconnect(); }
    }
    private SecretKey key() throws Exception {
        KeyStore k = KeyStore.getInstance("AndroidKeyStore"); k.load(null);
        if (k.containsAlias(ALIAS)) return (SecretKey) k.getKey(ALIAS, null);
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return gen.generateKey();
    }
    private synchronized JSONObject load() throws Exception {
        String encrypted = preferences.getString("encrypted", "");
        if (encrypted.isEmpty()) return new JSONObject();
        String[] parts = encrypted.split("\\.");
        if (parts.length != 2) throw new IllegalStateException("Invalid stored session");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new JSONObject(new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8));
    }
    private synchronized void save(JSONObject credentials) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key());
        byte[] ciphertext = c.doFinal(credentials.toString().getBytes(StandardCharsets.UTF_8));
        String value = Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + "." + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
        if (!preferences.edit().putString("encrypted", value).commit()) throw new IllegalStateException("Cannot save protected session");
    }
    private static byte[] decode(String b64) { return Base64.decode(b64, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP); }
    private static boolean constantEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
    private static boolean hasScope(String scopes, String required) {
        if (scopes == null || scopes.trim().isEmpty()) return false;
        for (String scope : scopes.trim().split("\\s+")) if (required.equals(scope)) return true;
        return false;
    }
    private JSONObject verifiedIdToken(String jwt, String expectedClient, String expectedNonce) throws Exception {
        String[] x = jwt.split("\\.");
        if (x.length != 3) throw new IllegalStateException("Invalid identity token");
        JSONObject header = new JSONObject(new String(decode(x[0]), StandardCharsets.UTF_8));
        JSONObject claims = new JSONObject(new String(decode(x[1]), StandardCharsets.UTF_8));
        if (!"RS256".equals(header.optString("alg"))) throw new IllegalStateException("Unsupported ID token signature");
        String kid = header.optString("kid", "");
        if (kid.isEmpty()) throw new IllegalStateException("Missing token key identifier");
        JSONObject discovery = requestJson(AUTH + "/.well-known/openid-configuration", null);
        String issuer = discovery.getString("issuer");
        if (!AUTH.equals(issuer) || !issuer.equals(claims.optString("iss"))) throw new IllegalStateException("Invalid identity token issuer");
        String jwksUri = discovery.getString("jwks_uri");
        if (!jwksUri.startsWith(AUTH + "/")) throw new IllegalStateException("Untrusted signing-key location");
        JSONArray keys = requestJson(jwksUri, null).getJSONArray("keys");
        boolean validSignature = false;
        for (int i = 0; i < keys.length(); i++) {
            JSONObject jwk = keys.getJSONObject(i);
            if (!kid.equals(jwk.optString("kid")) || !"RSA".equals(jwk.optString("kty"))) continue;
            BigInteger n = new BigInteger(1, decode(jwk.getString("n")));
            BigInteger e = new BigInteger(1, decode(jwk.getString("e")));
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n,e)));
            verifier.update((x[0] + "." + x[1]).getBytes(StandardCharsets.US_ASCII));
            validSignature = verifier.verify(decode(x[2]));
            break;
        }
        if (!validSignature) throw new IllegalStateException("Identity token signature invalid");
        boolean validAudience = expectedClient.equals(claims.optString("aud"));
        if (claims.optJSONArray("aud") != null) {
            JSONArray audiences = claims.getJSONArray("aud");
            for (int i = 0; i < audiences.length(); i++) if (expectedClient.equals(audiences.optString(i))) validAudience = true;
        }
        if (!validAudience || !constantEquals(expectedNonce, claims.optString("nonce", "")))
            throw new IllegalStateException("Identity token audience/nonce mismatch");
        if (claims.optLong("exp", 0) <= System.currentTimeMillis()/1000 || claims.optString("sub", "").isEmpty())
            throw new IllegalStateException("Expired or incomplete identity token");
        return claims;
    }
    private void exchange(String code, String client, String verifier, String redirect, String nonce) throws Exception {
        JSONObject current = load();
        JSONObject response = requestJson(AUTH + "/api/accounts/oauth/token", form(new String[][] {
            {"grant_type", "authorization_code"}, {"client_id", client}, {"code", code},
            {"code_verifier", verifier}, {"redirect_uri", redirect}, {"resource", RESOURCE}
        }));
        String idToken = response.getString("id_token");
        JSONObject claims = verifiedIdToken(idToken, client, nonce);
        // Prevent account swapping during reauthorization.
        if (!current.optString("client_id", "").isEmpty() && client.equals(current.optString("client_id")) &&
            !claims.optString("sub").equals(current.optString("subject")))
            throw new IllegalStateException("Different ChatGPT account selected");
        String scope = response.optString("scope", "");
        if (!hasScope(scope, "chatgpt.tokens.use.direct")) throw new IllegalStateException("ChatGPT plan usage permission not granted");
        JSONObject creds = new JSONObject()
            .put("client_id", client).put("subject", claims.getString("sub"))
            .put("email", claims.optString("email", "Connected account"))
            .put("id_token", idToken).put("access_token", response.getString("access_token"))
            .put("refresh_token", response.getString("refresh_token"))
            .put("scope", scope).put("expires_at", System.currentTimeMillis()/1000 + response.optLong("expires_in", 3600));
        save(creds);
    }
    void signIn(Completion callback) {
        new Thread(() -> {
            try {
                JSONObject existing = load();
                String savedClient = existing.optString("client_id", "");
                String client = savedClient.isEmpty() ? "dynamic_agent_client" : savedClient;
                String state = randomToken(32), nonce = randomToken(32), verifier = randomToken(64);
                String challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)),
                    Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
                try (ServerSocket server = new ServerSocket()) {
                    server.setReuseAddress(false);
                    server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
                    server.setSoTimeout(180000);
                    String redirect = "http://127.0.0.1:" + server.getLocalPort() + "/auth/callback";
                    String[][] fields = new String[][] {
                        {"client_id", client}, {"ext_agent_host_id", hostId()}, {"response_type", "code"},
                        {"redirect_uri", redirect}, {"scope", "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"},
                        {"resource", RESOURCE}, {"state", state}, {"nonce", nonce},
                        {"code_challenge_method", "S256"}, {"code_challenge", challenge}
                    };
                    String params = form(fields);
                    if (savedClient.isEmpty()) params += "&agent_name_hint=" + urlencode("ROM Shelf");
                    else if (!existing.optString("id_token", "").isEmpty()) params += "&id_token_hint=" + urlencode(existing.getString("id_token"));
                    Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(AUTH + "/api/accounts/authorize?" + params));
                    browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(browser);
                    boolean received = false;
                    String authCode = "", returnedClient = savedClient;
                    for (int attempts = 0; attempts < 12 && !received; attempts++) {
                        try (Socket socket = server.accept()) {
                            socket.setSoTimeout(5000);
                            InputStream input = socket.getInputStream();
                            ByteArrayOutputStream line = new ByteArrayOutputStream(); int ch;
                            while ((ch = input.read()) != -1 && ch != '\n' && line.size() < 4096) line.write(ch);
                            String firstLine = line.toString("UTF-8");
                            boolean matchingPath = firstLine.startsWith("GET /auth/callback?");
                            String query = matchingPath ? firstLine.substring("GET /auth/callback?".length(), firstLine.indexOf(' ', 5)) : "";
                            Uri url = Uri.parse("http://127.0.0.1/?" + query);
                            boolean stateMatches = matchingPath && constantEquals(state, url.getQueryParameter("state") == null ? "" : url.getQueryParameter("state"));
                            String reply = stateMatches ? "Sign-in received. Return to ROM Shelf." : "This callback was not accepted.";
                            byte[] contents = reply.getBytes(StandardCharsets.UTF_8);
                            socket.getOutputStream().write(("HTTP/1.1 " + (stateMatches ? "200 OK" : "400 Bad Request") + "\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: " + contents.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                            socket.getOutputStream().write(contents); socket.getOutputStream().flush();
                            if (!stateMatches) continue;
                            String error = url.getQueryParameter("error");
                            if (error != null) throw new IllegalStateException("Authorization declined: " + error);
                            authCode = url.getQueryParameter("code");
                            if (authCode == null || authCode.length() < 8) throw new IllegalStateException("Missing authorization code");
                            String receivedClient = url.getQueryParameter("client_id");
                            if (savedClient.isEmpty()) {
                                if (receivedClient == null || !receivedClient.startsWith("oaiapp_")) throw new IllegalStateException("Missing issued client ID");
                                returnedClient = receivedClient;
                            } else if (receivedClient != null && !receivedClient.equals(savedClient)) {
                                throw new IllegalStateException("Unexpected client ID in callback");
                            }
                            received = true;
                        }
                    }
                    if (!received) throw new IllegalStateException("No valid browser callback received");
                    exchange(authCode, returnedClient, verifier, redirect, nonce);
                }
                callback.done(null, account());
            } catch (Exception e) { callback.done(e.getMessage() == null ? "Sign-in failed" : e.getMessage(), null); }
        }, "romshelf-oauth").start();
    }
    synchronized String accessToken() throws Exception {
        JSONObject c = load();
        if (!hasScope(c.optString("scope", ""), "chatgpt.tokens.use.direct")) throw new IllegalStateException("Connect ChatGPT in Settings first");
        if (c.optLong("expires_at",0) > System.currentTimeMillis()/1000 + 120) return c.getString("access_token");
        JSONObject refreshed = requestJson(AUTH + "/api/accounts/oauth/token", form(new String[][] {
            {"grant_type", "refresh_token"}, {"client_id", c.getString("client_id")},
            {"refresh_token", c.getString("refresh_token")}, {"resource", RESOURCE}
        }));
        c.put("access_token", refreshed.getString("access_token"));
        if (refreshed.has("refresh_token")) c.put("refresh_token", refreshed.getString("refresh_token"));
        if (refreshed.has("scope")) c.put("scope", refreshed.getString("scope"));
        c.put("expires_at", System.currentTimeMillis()/1000 + refreshed.optLong("expires_in", 3600));
        save(c); return c.getString("access_token");
    }
    String disconnect() {
        JSONObject prior;
        try { prior = load(); } catch (Exception e) { prior = new JSONObject(); }
        String result = "Disconnected from this device";
        try {
            if (!prior.optString("refresh_token", "").isEmpty()) {
                JSONObject oidc = requestJson(AUTH + "/.well-known/openid-configuration", null);
                String revoke = oidc.optString("revocation_endpoint", "");
                if (!revoke.startsWith(AUTH + "/")) throw new IllegalStateException("Invalid revocation endpoint");
                requestJson(revoke, form(new String[][] { {"token", prior.getString("refresh_token")}, {"client_id", prior.getString("client_id")}, {"token_type_hint", "refresh_token"} }));
            }
        } catch (Exception e) { result = "Disconnected locally; remote token revocation unconfirmed. Check ChatGPT Settings."; }
        preferences.edit().remove("encrypted").commit();
        return result;
    }
}
