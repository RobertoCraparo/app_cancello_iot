package com.example.cancello_iot.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.cancello_iot.MainActivity;
import com.example.cancello_iot.adapter.AccessiAdapter;
import com.example.cancello_iot.api.ApiService;
import com.example.cancello_iot.databinding.FragmentDashboardBinding;
import com.example.cancello_iot.model.Accesso;
import com.example.cancello_iot.model.DashboardStats;
import com.example.cancello_iot.mqtt.MqttManager;

import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardFragment extends Fragment implements MqttManager.Listener {

    private FragmentDashboardBinding b;
    private AccessiAdapter adapter;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    @Override
    public View onCreateView(@NonNull LayoutInflater inf, ViewGroup container, Bundle s) {
        b = FragmentDashboardBinding.inflate(inf, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new AccessiAdapter();
        b.rvUltimiAccessi.setLayoutManager(new LinearLayoutManager(getContext()));
        b.rvUltimiAccessi.setAdapter(adapter);
        b.rvUltimiAccessi.setNestedScrollingEnabled(false);

        // Comandi gate via MQTT
        b.btnApri.setOnClickListener(v -> sendCommand("apri"));
        b.btnChiudi.setOnClickListener(v -> sendCommand("chiudi"));
        b.btnStop.setOnClickListener(v -> sendCommand("stop"));

        // Swipe to refresh
        b.swipeRefresh.setOnRefreshListener(this::loadData);
        b.swipeRefresh.setColorSchemeColors(0xFF1352A0);

        // "Vedi tutti" → tab Accessi
        b.tvVediTutti.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).getSupportFragmentManager()
                        .beginTransaction()
                        .replace(com.example.cancello_iot.R.id.fragmentContainer,
                                new AccessiFragment())
                        .commit();
                ((MainActivity) getActivity()).findViewById(
                        com.example.cancello_iot.R.id.nav_accessi).performClick();
            }
        });

        // MQTT listener
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).getMqtt().setListener(this);
        }

        loadData();
    }

    private void loadData() {
        exec.submit(() -> {
            try {
                ApiService api = new ApiService();
                DashboardStats stats = api.getDashboard();
                List<Accesso> accessi = api.getAccessi();

                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    b.tvAccessiOggi.setText(String.valueOf(stats.accessi_oggi));
                    b.tvFalliti.setText(String.valueOf(stats.falliti_oggi));
                    b.tvUtentiAttivi.setText(String.valueOf(stats.utenti_attivi));

                    // Ultimi 6
                    List<Accesso> ultimi = accessi.size() > 6 ? accessi.subList(0, 6) : accessi;
                    adapter.setData(ultimi);
                    b.swipeRefresh.setRefreshing(false);
                });
            } catch (Exception e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    b.swipeRefresh.setRefreshing(false);
                    Toast.makeText(getContext(), "Errore API: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void sendCommand(String cmd) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).getMqtt()
                    .publish(MqttManager.TOPIC_COMANDO, cmd);
            Toast.makeText(getContext(), "Comando inviato: " + cmd, Toast.LENGTH_SHORT).show();
        }
    }

    // ─── MQTT Listener ────────────────────────────────────────
    @Override public void onConnected() {
        if (!isAdded()) return;
        b.tvMqttInd.setText("Online");
        b.tvWifi.setText("Connesso");
    }

    @Override public void onDisconnected() {
        if (!isAdded()) return;
        b.tvMqttInd.setText("Offline");
    }

    @Override
    public void onMessage(String topic, String payload) {
        if (!isAdded()) return;
        switch (topic) {
            case MqttManager.TOPIC_STATO:
                updateGateStatus(payload);
                break;
            case MqttManager.TOPIC_ULTRASUONI:
                b.tvDistanza.setText(payload + " cm");
                break;
            case MqttManager.TOPIC_HEARTBEAT:
                b.tvUptime.setText(payload);
                break;
            case MqttManager.TOPIC_LOG:
                // Ricarica lista accessi al prossimo accesso
                loadData();
                break;
        }
    }

    @Override public void onError(String message) {}

    private void updateGateStatus(String json) {
        try {
            // Payload atteso: {"stato":"aperto"} oppure stringa semplice
            String stato;
            if (json.trim().startsWith("{")) {
                JSONObject obj = new JSONObject(json);
                stato = obj.optString("stato", json);
            } else {
                stato = json;
            }
            String label = stato.equals("aperto") ? "Aperto" : "Chiuso";
            b.tvGateStatus.setText(label);
            int color = stato.equals("aperto") ? 0xFF4ADE80 : 0xFFFFFFFF;
            b.tvGateStatus.setTextColor(color);
            b.tvLastAction.setText("Aggiornato via MQTT");
        } catch (Exception ignored) {
            b.tvGateStatus.setText(json);
        }
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
