package com.example.cancello_iot.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.cancello_iot.MainActivity;
import com.example.cancello_iot.R;
import com.example.cancello_iot.adapter.UtentiAdapter;
import com.example.cancello_iot.api.ApiService;
import com.example.cancello_iot.databinding.FragmentUtentiBinding;
import com.example.cancello_iot.model.Utente;
import com.example.cancello_iot.mqtt.MqttManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UtentiFragment extends Fragment implements MqttManager.Listener {

    private static final String TAG = "UtentiFragment";

    private FragmentUtentiBinding b;
    private UtentiAdapter adapter;
    private List<Utente> allData = new ArrayList<>();
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MqttManager mqtt;
    private boolean mqttResponseReceived = false;

    private final Runnable mqttTimeoutRunnable = () -> {
        if (!mqttResponseReceived && isAdded()) {
            Log.w(TAG, "Timeout MQTT — fallback REST");
            loadDataRest();
        }
    };

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    public View onCreateView(@NonNull LayoutInflater inf, ViewGroup c, Bundle s) {
        b = FragmentUtentiBinding.inflate(inf, c, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        super.onViewCreated(view, s);

        if (getActivity() instanceof MainActivity) {
            mqtt = ((MainActivity) getActivity()).getMqtt();
        }

        adapter = new UtentiAdapter();
        adapter.setListener(new UtentiAdapter.OnActionListener() {
            @Override public void onModifica(Utente u) { showDialogUtente(u); }
            @Override public void onElimina(Utente u)  { confirmDelete(u); }
        });

        b.rvUtenti.setLayoutManager(new LinearLayoutManager(getContext()));
        b.rvUtenti.setAdapter(adapter);
        b.rvUtenti.setNestedScrollingEnabled(false);

        b.fabNuovo.setOnClickListener(v -> showDialogUtente(null));

        b.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b2, int a) {
                filterList(s.toString().trim().toLowerCase(Locale.ITALY));
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        b.swipeRefresh.setColorSchemeColors(0xFF1352A0);
        b.swipeRefresh.setOnRefreshListener(this::loadData);

        // Attende 300ms che la transazione fragment sia completata, poi carica
        handler.postDelayed(this::loadData, 300);
    }

    /**
     * onResume: registra questo fragment come listener diretto di MqttManager.
     * In questo modo i messaggi arrivano direttamente qui, senza passare per
     * il meccanismo findFragmentById della MainActivity (che può fallire per timing).
     */
    @Override
    public void onResume() {
        super.onResume();
        if (mqtt != null) {
            mqtt.addListener(this);
            Log.d(TAG, "Registrato come MQTT listener");
        }
    }

    /**
     * onPause: deregistra il listener per non ricevere messaggi quando il fragment
     * non è visibile ed evitare memory leak.
     */
    @Override
    public void onPause() {
        super.onPause();
        if (mqtt != null) {
            mqtt.removeListener(this);
            Log.d(TAG, "Deregistrato come MQTT listener");
        }
        handler.removeCallbacks(mqttTimeoutRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(mqttTimeoutRunnable);
        b = null;
    }

    // ─── Caricamento dati ────────────────────────────────────────────────────

    private void loadData() {
        mqttResponseReceived = false;
        if (mqtt != null && mqtt.isConnected()) {
            if (b != null) b.swipeRefresh.setRefreshing(true);
            Log.d(TAG, "Richiesta utenti via MQTT");
            mqtt.publish(MqttManager.TOPIC_SYNC_REQ, "{\"action\":\"fetch_users\"}");
            handler.removeCallbacks(mqttTimeoutRunnable);
            handler.postDelayed(mqttTimeoutRunnable, 8000);
        } else {
            Log.w(TAG, "MQTT non connesso — uso REST direttamente");
            loadDataRest();
        }
    }

    private void loadDataRest() {
        if (b != null) b.swipeRefresh.setRefreshing(true);
        exec.submit(() -> {
            try {
                List<Utente> list = new ApiService().getUtenti();
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    allData = list != null ? list : new ArrayList<>();
                    updateStats();
                    adapter.setData(allData);
                    if (b != null) b.swipeRefresh.setRefreshing(false);
                });
            } catch (Exception e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (b != null) b.swipeRefresh.setRefreshing(false);
                    Toast.makeText(getContext(), "Errore REST: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // ─── MqttManager.Listener ────────────────────────────────────────────────

    @Override
    public void onMessage(String topic, String payload) {
        if (!MqttManager.TOPIC_SYNC_RES.equals(topic)) return;

        Log.d(TAG, "onMessage ricevuto su TOPIC_SYNC_RES");

        try {
            JsonObject root = JsonParser.parseString(payload).getAsJsonObject();
            String type = root.has("type") ? root.get("type").getAsString() : "";

            if ("users_list".equals(type)) {
                JsonArray usersArray = root.getAsJsonArray("users");
                Type listType = new TypeToken<List<Utente>>() {}.getType();
                List<Utente> lista = new Gson().fromJson(usersArray, listType);

                if (!isAdded()) return;
                mqttResponseReceived = true;
                handler.removeCallbacks(mqttTimeoutRunnable);

                Log.d(TAG, "users_list ricevuta: " + (lista != null ? lista.size() : 0) + " utenti");

                requireActivity().runOnUiThread(() -> {
                    allData = lista != null ? lista : new ArrayList<>();
                    updateStats();
                    adapter.setData(allData);
                    if (b != null) b.swipeRefresh.setRefreshing(false);
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Errore parsing JSON utenti: " + e.getMessage());
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (b != null) b.swipeRefresh.setRefreshing(false);
                Toast.makeText(getContext(), "Errore parsing: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }

    @Override public void onConnected() {
        // Se MQTT si connette mentre il fragment è aperto, carica subito i dati
        Log.d(TAG, "MQTT connesso mentre fragment è attivo — carico dati");
        handler.post(this::loadData);
    }
    @Override public void onDisconnected() {}
    @Override public void onError(String message) {}

    // ─── Statistiche ─────────────────────────────────────────────────────────

    private void updateStats() {
        long attivi   = allData.stream().filter(u -> u.attivo).count();
        long rfid     = allData.stream().filter(Utente::hasRfid).count();
        long impronte = allData.stream().filter(Utente::hasImpronta).count();
        b.tvStatTotali.setText(String.valueOf(allData.size()));
        b.tvStatAttivi.setText(String.valueOf(attivi));
        b.tvStatRfid.setText(String.valueOf(rfid));
        b.tvStatImp.setText(String.valueOf(impronte));
    }

    // ─── Ricerca locale ───────────────────────────────────────────────────────

    private void filterList(String q) {
        if (q.isEmpty()) { adapter.setData(allData); return; }
        List<Utente> filtered = new ArrayList<>();
        for (Utente u : allData)
            if ((u.name  != null && u.name.toLowerCase(Locale.ITALY).contains(q))
             || (u.email != null && u.email.toLowerCase(Locale.ITALY).contains(q)))
                filtered.add(u);
        adapter.setData(filtered);
    }

    // ─── Dialog crea / modifica ───────────────────────────────────────────────

    private void showDialogUtente(@Nullable Utente utente) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_utente, null);
        EditText etName     = dialogView.findViewById(R.id.etName);
        EditText etEmail    = dialogView.findViewById(R.id.etEmail);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);
        EditText etRfid     = dialogView.findViewById(R.id.etRfid);
        EditText etImpronta = dialogView.findViewById(R.id.etImpronta);
        Switch  swAttivo    = dialogView.findViewById(R.id.switchAttivo);

        if (utente != null) {
            etName.setText(utente.name);
            etEmail.setText(utente.email);
            etRfid.setText(utente.uid_rfid != null ? utente.uid_rfid : "");
            etImpronta.setText(utente.id_impronta != null ? String.valueOf(utente.id_impronta) : "");
            swAttivo.setChecked(utente.attivo);
        }

        String title = utente == null ? "Nuovo Utente" : "Modifica Utente";
        String btn   = utente == null ? "Crea Utente"  : "Salva modifiche";

        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(dialogView)
                .setPositiveButton(btn, (d, w) -> {
                    String name   = etName.getText().toString().trim();
                    String email  = etEmail.getText().toString().trim();
                    String pass   = etPassword.getText().toString().trim();
                    String rfid   = etRfid.getText().toString().trim();
                    String impStr = etImpronta.getText().toString().trim();
                    Integer imp   = impStr.isEmpty() ? null : Integer.parseInt(impStr);
                    boolean att   = swAttivo.isChecked();

                    exec.submit(() -> {
                        try {
                            ApiService api = new ApiService();
                            boolean ok = utente == null
                                    ? api.createUtente(name, email, pass, rfid, imp, att)
                                    : api.updateUtente(utente.id, name, email, pass, rfid, imp, att);
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(),
                                        ok ? "Salvato!" : "Errore salvataggio",
                                        Toast.LENGTH_SHORT).show();
                                if (ok) loadData();
                            });
                        } catch (Exception ex) {
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(getContext(), "Errore: " + ex.getMessage(),
                                            Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    // ─── Elimina utente ───────────────────────────────────────────────────────

    private void confirmDelete(Utente u) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Elimina utente")
                .setMessage("Sei sicuro di voler eliminare " + u.name + "?\nQuesta azione non può essere annullata.")
                .setPositiveButton("Sì, elimina", (d, w) -> {
                    exec.submit(() -> {
                        try {
                            boolean ok = new ApiService().deleteUtente(u.id);
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(),
                                        ok ? "Utente eliminato" : "Errore eliminazione",
                                        Toast.LENGTH_SHORT).show();
                                if (ok) loadData();
                            });
                        } catch (Exception e) {
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(getContext(), "Errore: " + e.getMessage(),
                                            Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton("Annulla", null)
                .show();
    }
}
