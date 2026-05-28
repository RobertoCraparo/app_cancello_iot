package com.example.cancello_iot.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
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
import com.example.cancello_iot.mqtt.MqttManager;

import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardFragment extends Fragment implements MqttManager.Listener {

    private static final String TAG = "DashboardFragment";

    private FragmentDashboardBinding b;
    private AccessiAdapter adapter;
    private MqttManager mqtt;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    @Override
    public View onCreateView(@NonNull LayoutInflater inf, ViewGroup container, Bundle s) {
        b = FragmentDashboardBinding.inflate(inf, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getActivity() instanceof MainActivity) {
            mqtt = ((MainActivity) getActivity()).getMqtt();
        }

        adapter = new AccessiAdapter();
        b.rvUltimiAccessi.setLayoutManager(new LinearLayoutManager(getContext()));
        b.rvUltimiAccessi.setAdapter(adapter);
        b.rvUltimiAccessi.setNestedScrollingEnabled(false);

        b.btnApri.setOnClickListener(v -> sendCommand("apri"));
        b.btnChiudi.setOnClickListener(v -> sendCommand("chiudi"));

        b.swipeRefresh.setOnRefreshListener(this::loadData);
        b.swipeRefresh.setColorSchemeColors(0xFF1352A0);

        b.tvVediTutti.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).getSupportFragmentManager()
                        .beginTransaction()
                        .replace(com.example.cancello_iot.R.id.fragmentContainer, new AccessiFragment())
                        .commit();
                ((MainActivity) getActivity()).findViewById(com.example.cancello_iot.R.id.nav_accessi).performClick();
            }
        });

        loadData();
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
    }

    private void loadData() {
        if (mqtt != null) {
            mqtt.publish(MqttManager.TOPIC_SYNC_REQ, "{\"action\":\"fetch_initial_data\"}");
        }

        exec.submit(() -> {
            try {
                List<Accesso> accessi = new ApiService().getAccessi();
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    List<Accesso> ultimi = accessi.size() > 6 ? accessi.subList(0, 6) : accessi;
                    adapter.setData(ultimi);
                    b.swipeRefresh.setRefreshing(false);
                });
            } catch (Exception e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    b.swipeRefresh.setRefreshing(false);
                    Toast.makeText(getContext(), "Errore API lista: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void sendCommand(String azione) {
        if (mqtt != null) {
            long timestamp = System.currentTimeMillis() / 1000;
            String payload = "{\"cmd\":\"" + azione + "\",\"timestamp\":" + timestamp + ",\"admin_id\":1,\"admin_nome\":\"Marco Rossi\"}";
            mqtt.publish(MqttManager.TOPIC_COMANDO, payload);
            Toast.makeText(getContext(), "Comando inviato: " + azione, Toast.LENGTH_SHORT).show();
        }
    }

    // ─── MqttManager.Listener ────────────────────────────────────────────────

    @Override
    public void onConnected() {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            b.tvMqttInd.setText("Online");
            b.tvMqttInd.setTextColor(Color.WHITE);
        });
    }

    @Override
    public void onDisconnected() {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            b.tvMqttInd.setText("Offline");
            b.tvMqttInd.setTextColor(Color.parseColor("#EF4444"));
            b.tvWifi.setText("Sconosciuto");
            b.tvWifi.setTextColor(Color.parseColor("#EF4444"));
        });
    }

    @Override
    public void onMessage(String topic, String payload) {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            try {
                switch (topic) {
                    case MqttManager.TOPIC_STATO:
                        updateGateStatus(payload);
                        break;

                    case MqttManager.TOPIC_ESP_STATUS:
                        JSONObject objStatus = new JSONObject(payload);
                        if (objStatus.has("wifi")) {
                            String wifi = objStatus.getString("wifi");
                            b.tvWifi.setText(wifi);
                            b.tvWifi.setTextColor(wifi.equalsIgnoreCase("Connesso") ? Color.WHITE : Color.parseColor("#EF4444"));
                        }
                        if (objStatus.has("mqtt")) {
                            String mqttStr = objStatus.getString("mqtt");
                            b.tvMqttInd.setText(mqttStr);
                            b.tvMqttInd.setTextColor(mqttStr.equalsIgnoreCase("Online") ? Color.WHITE : Color.parseColor("#EF4444"));
                        }
                        break;

                    case MqttManager.TOPIC_SYNC_RES:
                        JSONObject objSync = new JSONObject(payload);
                        if (objSync.has("accessi_oggi"))  b.tvAccessiOggi.setText(String.valueOf(objSync.getInt("accessi_oggi")));
                        if (objSync.has("falliti"))        b.tvFalliti.setText(String.valueOf(objSync.getInt("falliti")));
                        if (objSync.has("utenti_attivi")) b.tvUtentiAttivi.setText(String.valueOf(objSync.getInt("utenti_attivi")));
                        if (objSync.has("stato_cancello")) updateGateStatus(objSync.getString("stato_cancello"));
                        break;

                    case MqttManager.TOPIC_LOG:
                        loadData();
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "Errore parsing MQTT: " + e.getMessage());
            }
        });
    }

    @Override public void onError(String message) {}

    private void updateGateStatus(String jsonOrString) {
        try {
            String stato = jsonOrString;
            if (jsonOrString.trim().startsWith("{")) {
                JSONObject obj = new JSONObject(jsonOrString);
                stato = obj.optString("stato", jsonOrString);
            }
            boolean isAperto = stato.equalsIgnoreCase("aperto");
            b.tvGateStatus.setText(isAperto ? "Aperto" : "Chiuso");
            b.gateStatusDot.setBackgroundColor(isAperto ? Color.parseColor("#22C55E") : Color.parseColor("#EF4444"));
            b.tvLastAction.setText("Sincronizzato in tempo reale");
        } catch (Exception ignored) {
            b.tvGateStatus.setText("Errore Dati");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
