package com.example.cancello_iot.api;

import com.example.cancello_iot.model.Accesso;
import com.example.cancello_iot.model.DashboardStats;
import com.example.cancello_iot.model.Utente;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiService {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient http = ApiClient.getClient();
    private final Gson gson = new Gson();

    public DashboardStats getDashboard() throws IOException {
        return gson.fromJson(get("/api/dashboard"), DashboardStats.class);
    }

    public List<Accesso> getAccessi() throws IOException {
        Type t = new TypeToken<List<Accesso>>(){}.getType();
        return gson.fromJson(get("/api/accessi"), t);
    }

    public List<Utente> getUtenti() throws IOException {
        Type t = new TypeToken<List<Utente>>(){}.getType();
        return gson.fromJson(get("/api/utenti"), t);
    }

    public boolean createUtente(String name, String email, String password,
                                String uid_rfid, Integer id_impronta, boolean attivo) throws IOException {
        String json = buildPayload(name, email, password, uid_rfid, id_impronta, attivo);
        return post("/api/utenti", json).contains("\"ok\":true");
    }

    public boolean updateUtente(int id, String name, String email, String password,
                                String uid_rfid, Integer id_impronta, boolean attivo) throws IOException {
        String json = buildPayload(name, email, password, uid_rfid, id_impronta, attivo);
        return put("/api/utenti/" + id, json).contains("\"ok\":true");
    }

    public boolean deleteUtente(int id) throws IOException {
        return delete("/api/utenti/" + id).contains("\"ok\":true");
    }

    // ─── Helpers ─────────────────────────────────────────────
    private String buildPayload(String name, String email, String password,
                                String uid_rfid, Integer id_impronta, boolean attivo) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"name\":\"").append(esc(name)).append("\",");
        sb.append("\"email\":\"").append(esc(email)).append("\",");
        if (password != null && !password.isEmpty())
            sb.append("\"password\":\"").append(esc(password)).append("\",");
        if (uid_rfid != null && !uid_rfid.isEmpty())
            sb.append("\"uid_rfid\":\"").append(esc(uid_rfid)).append("\",");
        if (id_impronta != null)
            sb.append("\"id_impronta\":").append(id_impronta).append(",");
        sb.append("\"attivo\":").append(attivo).append("}");
        return sb.toString();
    }

    private String esc(String s) { return s == null ? "" : s.replace("\"", "\\\""); }

    private String get(String path) throws IOException {
        Request req = new Request.Builder().url(ApiClient.baseUrl() + path).get().build();
        try (Response r = http.newCall(req).execute()) {
            return r.body() != null ? r.body().string() : "{}";
        }
    }

    private String post(String path, String json) throws IOException {
        Request req = new Request.Builder()
                .url(ApiClient.baseUrl() + path)
                .post(RequestBody.create(json, JSON)).build();
        try (Response r = http.newCall(req).execute()) {
            return r.body() != null ? r.body().string() : "{}";
        }
    }

    private String put(String path, String json) throws IOException {
        Request req = new Request.Builder()
                .url(ApiClient.baseUrl() + path)
                .put(RequestBody.create(json, JSON)).build();
        try (Response r = http.newCall(req).execute()) {
            return r.body() != null ? r.body().string() : "{}";
        }
    }

    private String delete(String path) throws IOException {
        Request req = new Request.Builder()
                .url(ApiClient.baseUrl() + path).delete().build();
        try (Response r = http.newCall(req).execute()) {
            return r.body() != null ? r.body().string() : "{}";
        }
    }
}
