package com.example.cancello_iot.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.cancello_iot.MainActivity;
import com.example.cancello_iot.adapter.AccessiAdapter;
import com.example.cancello_iot.databinding.FragmentAccessiBinding;
import com.example.cancello_iot.model.Accesso;
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

public class AccessiFragment extends Fragment implements MqttManager.Listener {

    private static final String TAG = "AccessiFragment";

    private FragmentAccessiBinding b;
    private AccessiAdapter adapter;
    private List<Accesso> allData = new ArrayList<>();
    private MqttManager mqtt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean mqttResponseReceived = false;

    private String filterSearch = "", filterMetodo = "", filterEsito = "", filterPeriodo = "";

    private final Runnable mqttTimeoutRunnable = () -> {
        if (!mqttResponseReceived && isAdded()) {
            Log.w(TAG, "Timeout MQTT — nessun dato ricevuto");
            if (b != null) {
                b.swipeRefresh.setRefreshing(false);
                Toast.makeText(getContext(), "Nessuna risposta dal server", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inf, ViewGroup c, Bundle s) {
        b = FragmentAccessiBinding.inflate(inf, c, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        super.onViewCreated(view, s);

        if (getActivity() instanceof MainActivity) {
            mqtt = ((MainActivity) getActivity()).getMqtt();
        }

        adapter = new AccessiAdapter();
        b.rvAccessi.setLayoutManager(new LinearLayoutManager(getContext()));
        b.rvAccessi.setAdapter(adapter);

        // Spinner metodo
        ArrayAdapter<String> metodoAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"Tutti i metodi", "Badge RFID", "Impronta", "Pulsante"});
        metodoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        b.spinnerMetodo.setAdapter(metodoAdapter);
        b.spinnerMetodo.setOnItemSelectedListener(new SimpleItemSelected() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                filterMetodo = pos == 0 ? "" : new String[]{"","rfid","fp","btn"}[pos];
                applyFilters();
            }
        });

        // Spinner esito
        ArrayAdapter<String> esitoAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"Tutti gli esiti", "Riuscito", "Rifiutato"});
        esitoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        b.spinnerEsito.setAdapter(esitoAdapter);
        b.spinnerEsito.setOnItemSelectedListener(new SimpleItemSelected() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                filterEsito = pos == 0 ? "" : (pos == 1 ? "ok" : "err");
                applyFilters();
            }
        });

        // Spinner periodo
        ArrayAdapter<String> periodoAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"Tutto il periodo", "Oggi", "Ultimi 7 gg", "Ultimi 30 gg"});
        periodoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        b.spinnerPeriodo.setAdapter(periodoAdapter);
        b.spinnerPeriodo.setOnItemSelectedListener(new SimpleItemSelected() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                filterPeriodo = pos == 0 ? "" : new String[]{"","oggi","7","30"}[pos];
                applyFilters();
            }
        });

        b.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b2, int a) {
                filterSearch = s.toString().trim().toLowerCase(Locale.ITALY);
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        b.swipeRefresh.setColorSchemeColors(0xFF1352A0);
        b.swipeRefresh.setOnRefreshListener(this::loadData);

        handler.postDelayed(this::loadData, 300);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mqtt != null) {
            mqtt.addListener(this);
            Log.d(TAG, "Registrato come MQTT listener");
        }
    }

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
            Log.d(TAG, "Richiesta accessi via MQTT");
            mqtt.publish(MqttManager.TOPIC_SYNC_REQ, "{\"action\":\"fetch_accesses\"}");
            handler.removeCallbacks(mqttTimeoutRunnable);
            handler.postDelayed(mqttTimeoutRunnable, 8000);
        } else {
            Log.w(TAG, "MQTT non connesso");
            if (b != null) {
                b.swipeRefresh.setRefreshing(false);
                Toast.makeText(getContext(), "MQTT non connesso, riprova tra poco", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ─── MqttManager.Listener ────────────────────────────────────────────────

    @Override
    public void onMessage(String topic, String payload) {
        if (!MqttManager.TOPIC_SYNC_RES.equals(topic)) return;

        try {
            JsonObject root = JsonParser.parseString(payload).getAsJsonObject();
            String type = root.has("type") ? root.get("type").getAsString() : "";

            if ("accesses_list".equals(type)) {
                JsonArray arr = root.getAsJsonArray("accessi");
                Type listType = new TypeToken<List<Accesso>>() {}.getType();
                List<Accesso> lista = new Gson().fromJson(arr, listType);

                if (!isAdded()) return;
                mqttResponseReceived = true;
                handler.removeCallbacks(mqttTimeoutRunnable);

                Log.d(TAG, "accesses_list ricevuta: " + (lista != null ? lista.size() : 0) + " accessi");

                requireActivity().runOnUiThread(() -> {
                    allData = lista != null ? lista : new ArrayList<>();
                    applyFilters();
                    if (b != null) b.swipeRefresh.setRefreshing(false);
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Errore parsing JSON accessi: " + e.getMessage());
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (b != null) b.swipeRefresh.setRefreshing(false);
                Toast.makeText(getContext(), "Errore dati: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }

    @Override public void onConnected() {
        Log.d(TAG, "MQTT connesso — carico accessi");
        handler.post(this::loadData);
    }
    @Override public void onDisconnected() {}
    @Override public void onError(String message) {}

    // ─── Filtri ───────────────────────────────────────────────────────────────

    private void applyFilters() {
        long now = System.currentTimeMillis() / 1000;
        long soglia = switch (filterPeriodo) {
            case "oggi" -> now - (now % 86400);
            case "7"   -> now - 7  * 86400L;
            case "30"  -> now - 30 * 86400L;
            default    -> 0L;
        };

        List<Accesso> filtered = new ArrayList<>();
        int ok = 0, err = 0;

        for (Accesso a : allData) {
            boolean match = (filterSearch.isEmpty() || (a.nome != null && a.nome.toLowerCase(Locale.ITALY).contains(filterSearch)))
                    && (filterMetodo.isEmpty() || filterMetodo.equals(a.metodo))
                    && (filterEsito.isEmpty()  || filterEsito.equals(a.esito))
                    && (soglia == 0           || a.ts >= soglia);
            if (match) {
                filtered.add(a);
                if (a.isOk()) ok++; else err++;
            }
        }

        adapter.setData(filtered);
        b.tvCountOk.setText(String.valueOf(ok));
        b.tvCountErr.setText(String.valueOf(err));
    }

    abstract static class SimpleItemSelected implements AdapterView.OnItemSelectedListener {
        @Override public void onNothingSelected(AdapterView<?> p) {}
    }
}
