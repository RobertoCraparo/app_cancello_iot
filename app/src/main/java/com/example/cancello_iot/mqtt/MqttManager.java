package com.example.cancello_iot.mqtt;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.cancello_iot.BuildConfig;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLSocketFactory;

public class MqttManager {
    private static final String TAG = "MqttManager";

    // Definizione dei topic per la comunicazione MQTT
    public static final String TOPIC_STATO      = "cancello/stato";
    public static final String TOPIC_SERVO      = "cancello/comando/servo"; // Topic per pilotare il servo hsms2309s (0-90 gradi)
    public static final String TOPIC_LOG        = "cancello/accessi/log";
    public static final String TOPIC_ESP_STATUS = "cancello/sistema/esp_stato"; // Stato connessione WiFi/LWT dell'ESP8266
    public static final String TOPIC_SYNC_REQ   = "cancello/app/sync_req"; // L'app richiede i dati a Laravel all'avvio
    public static final String TOPIC_SYNC_RES   = "cancello/laravel/sync_res"; // Laravel risponde con i dati formattati in JSON

    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onMessage(String topic, String payload);
        void onError(String message);
    }

    private MqttClient client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, String> lastValues = new HashMap<>();
    private Listener listener;

    public void setListener(Listener l) { this.listener = l; }

    public void connect(String clientId) {
        executor.submit(() -> {
            try {
                String scheme = BuildConfig.MQTT_PORT == 8883 ? "ssl" : "tcp";
                String url = scheme + "://" + BuildConfig.MQTT_HOST + ":" + BuildConfig.MQTT_PORT;

                client = new MqttClient(url, clientId, new MemoryPersistence());

                MqttConnectOptions opts = new MqttConnectOptions();
                opts.setUserName(BuildConfig.MQTT_USERNAME);
                opts.setPassword(BuildConfig.MQTT_PASSWORD.toCharArray());
                opts.setCleanSession(true);
                opts.setConnectionTimeout(10);
                opts.setKeepAliveInterval(30);
                opts.setAutomaticReconnect(true);

                if (BuildConfig.MQTT_PORT == 8883) {
                    opts.setSocketFactory(SSLSocketFactory.getDefault());
                }

                client.setCallback(new MqttCallback() {
                    @Override public void connectionLost(Throwable cause) {
                        Log.w(TAG, "Connection lost", cause);
                        notifyDisconnected();
                    }
                    @Override public void messageArrived(String topic, MqttMessage msg) {
                        String payload = new String(msg.getPayload());
                        lastValues.put(topic, payload);
                        notifyMessage(topic, payload);
                    }
                    @Override public void deliveryComplete(IMqttDeliveryToken token) {}
                });

                client.connect(opts);

                // Sottoscrizioni ai topic necessari per ricevere aggiornamenti
                client.subscribe(TOPIC_STATO,      1);
                client.subscribe(TOPIC_LOG,        1);
                client.subscribe(TOPIC_ESP_STATUS, 0); // Ascolta lo stato di vita dell'ESP8266
                client.subscribe(TOPIC_SYNC_RES,   1); // Ascolta la risposta di Laravel al boot

                notifyConnected();
                Log.i(TAG, "Connected → " + url);

            } catch (MqttException e) {
                Log.e(TAG, "MQTT error", e);
                notifyError("Connessione MQTT fallita: " + e.getMessage());
            }
        });
    }

    public void publish(String topic, String payload) {
        executor.submit(() -> {
            try {
                if (client != null && client.isConnected()) {
                    MqttMessage msg = new MqttMessage(payload.getBytes());
                    msg.setQos(1);
                    client.publish(topic, msg);
                }
            } catch (MqttException e) { Log.e(TAG, "Publish error", e); }
        });
    }

    public void disconnect() {
        executor.submit(() -> {
            try { if (client != null && client.isConnected()) client.disconnect(); }
            catch (MqttException ignored) {}
        });
    }

    public boolean isConnected() { return client != null && client.isConnected(); }
    public String getLastValue(String topic) { return lastValues.getOrDefault(topic, "—"); }

    private void notifyConnected()                { mainHandler.post(() -> { if (listener != null) listener.onConnected(); }); }
    private void notifyDisconnected()             { mainHandler.post(() -> { if (listener != null) listener.onDisconnected(); }); }
    private void notifyMessage(String t, String p){ mainHandler.post(() -> { if (listener != null) listener.onMessage(t, p); }); }
    private void notifyError(String m)            { mainHandler.post(() -> { if (listener != null) listener.onError(m); }); }
}