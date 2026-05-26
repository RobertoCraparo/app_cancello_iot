package com.example.cancello_iot;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.cancello_iot.databinding.ActivityMainBinding;
import com.example.cancello_iot.mqtt.MqttManager;
import com.example.cancello_iot.ui.AccessiFragment;
import com.example.cancello_iot.ui.DashboardFragment;
import com.example.cancello_iot.ui.DispositivoFragment;
import com.example.cancello_iot.ui.UtentiFragment;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding b;
    private MqttManager mqtt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        mqtt = new MqttManager();
        String clientId = "android_app_" + UUID.randomUUID().toString().substring(0, 8);

        mqtt.setListener(new MqttManager.Listener() {
            @Override public void onConnected() {
                // Aggiorna l'interfaccia globale in alto a sinistra
                b.tvMqttStatus.setText("MQTT Online");
                b.tvMqttStatus.setTextColor(0xFF22C55E); // Verde
                b.mqttDot.setBackgroundColor(0xFF22C55E);

                // Richiede a Laravel i dati di stato iniziale non appena connesso
                mqtt.publish(MqttManager.TOPIC_SYNC_REQ, "{\"action\":\"fetch_initial_data\"}");
            }
            @Override public void onDisconnected() {
                // Notifica la caduta della connessione al broker MQTT
                b.tvMqttStatus.setText("MQTT Offline");
                b.tvMqttStatus.setTextColor(0xFFEF4444); // Rosso
                b.mqttDot.setBackgroundColor(0xFFEF4444);
            }
            @Override public void onMessage(String topic, String payload) {
                // Propaga il messaggio in entrata al Fragment attualmente visibile
                Fragment current = getSupportFragmentManager()
                        .findFragmentById(R.id.fragmentContainer);
                if (current instanceof MqttManager.Listener)
                    ((MqttManager.Listener) current).onMessage(topic, payload);
            }
            @Override public void onError(String message) {
                b.tvMqttStatus.setText("Offline");
                b.tvMqttStatus.setTextColor(0xFFEF4444);
                b.mqttDot.setBackgroundColor(0xFFEF4444);
            }
        });

        mqtt.connect(clientId);

        if (savedInstanceState == null) loadFragment(new DashboardFragment());

        b.bottomNav.setOnItemSelectedListener(item -> {
            Fragment f;
            int id = item.getItemId();
            if      (id == R.id.nav_dashboard)   f = new DashboardFragment();
            else if (id == R.id.nav_accessi)     f = new AccessiFragment();
            else if (id == R.id.nav_utenti)      f = new UtentiFragment();
            else                                 f = new DispositivoFragment();
            loadFragment(f);
            return true;
        });

        b.ivLogout.setOnClickListener(v -> logout());
    }

    private void loadFragment(Fragment f) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, f).commit();
    }

    public MqttManager getMqtt() { return mqtt; }

    private void logout() {
        mqtt.disconnect();
        getSharedPreferences("cancello", MODE_PRIVATE)
                .edit().putBoolean("logged_in", false).apply();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() { super.onDestroy(); mqtt.disconnect(); }
}