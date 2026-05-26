package com.example.cancello_iot.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cancello_iot.MainActivity;
import com.example.cancello_iot.R;
import com.example.cancello_iot.databinding.FragmentDispositivoBinding;
import com.example.cancello_iot.mqtt.MqttManager;

import java.util.Arrays;
import java.util.List;

public class DispositivoFragment extends Fragment implements MqttManager.Listener {

    private FragmentDispositivoBinding b;

    // ─── Dati statici (Sensori attualmente in uso) ─────────
    static final List<String[]> DEVICES = Arrays.asList(
            new String[]{"💳","RC522 RFID",       "Lettore SPI",         "Attivo","", "GPIO12-15 — SPI hardware"},
            new String[]{"👆","AS608",            "Sensore Impronte",    "Attivo","", "UART — Impronte reg."},
            new String[]{"🖥️","Display",          "Interfaccia I2C",     "Attivo","", "Comunicazione I2C"},
            new String[]{"⚙️","Servo HSMS2309S",  "Controllo Cancello",  "0-90","°", "PWM Software"},
            new String[]{"🔢","Tastierino HX543", "Input PIN",           "Attivo","", "Matrice I/O"}
    );

    // ─── Topic allineati con il nuovo MqttManager ─────────
    static final List<String[]> TOPICS = Arrays.asList(
            new String[]{MqttManager.TOPIC_STATO,      "Stato attuale cancello",         "PUB"},
            new String[]{MqttManager.TOPIC_SERVO,      "Comando apertura/chiusura 0-90°","SUB"},
            new String[]{MqttManager.TOPIC_LOG,        "Log accessi per Database",       "PUB"},
            new String[]{MqttManager.TOPIC_ESP_STATUS, "Stato rete e vita ESP8266",      "PUB"},
            new String[]{MqttManager.TOPIC_SYNC_REQ,   "Richiesta dati a Laravel",       "PUB"},
            new String[]{MqttManager.TOPIC_SYNC_RES,   "Risposta dati da Laravel",       "SUB"}
    );

    // ─── PIN Hardware aggiornati ─────────
    static final List<String[]> PINS = Arrays.asList(
            new String[]{"GPIO14","RC522 SCK",    "SPI"},
            new String[]{"GPIO13","RC522 MOSI",   "SPI"},
            new String[]{"GPIO12","RC522 MISO",   "SPI"},
            new String[]{"GPIO15","RC522 SS",     "SPI"},
            new String[]{"GPIO4", "I2C SDA (Disp)","I2C"},
            new String[]{"GPIO5", "I2C SCL (Disp)","I2C"},
            new String[]{"UART",  "AS608 TX/RX",  "UART"},
            new String[]{"PWM",   "Servo HSMS2309S","PWM"},
            new String[]{"I/O",   "Tastierino",   "GPIO"}
    );

    @Override
    public View onCreateView(@NonNull LayoutInflater inf, ViewGroup c, Bundle s) {
        b = FragmentDispositivoBinding.inflate(inf, c, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        super.onViewCreated(view, s);

        // Imposta la griglia dei dispositivi (2 colonne)
        b.rvDevices.setLayoutManager(new GridLayoutManager(getContext(), 2));
        b.rvDevices.setAdapter(new SimpleAdapter<>(DEVICES, R.layout.item_device) {
            @Override void bind(View v, String[] item, int pos) {
                ((TextView) v.findViewById(R.id.tvIcon)).setText(item[0]);
                ((TextView) v.findViewById(R.id.tvNome)).setText(item[1]);
                ((TextView) v.findViewById(R.id.tvTipo)).setText(item[2]);
                ((TextView) v.findViewById(R.id.tvVal)).setText(item[3]);
                ((TextView) v.findViewById(R.id.tvUnit)).setText(item[4]);
                ((TextView) v.findViewById(R.id.tvNote)).setText(item[5]);
            }
        });

        // Imposta la lista dei Topic MQTT
        b.rvTopics.setLayoutManager(new LinearLayoutManager(getContext()));
        b.rvTopics.setAdapter(new SimpleAdapter<>(TOPICS, R.layout.item_topic) {
            @Override void bind(View v, String[] item, int pos) {
                ((TextView) v.findViewById(R.id.tvNome)).setText(item[0]);
                ((TextView) v.findViewById(R.id.tvDesc)).setText(item[1]);
                TextView tvDir = v.findViewById(R.id.tvDir);
                tvDir.setText(item[2]);
                if ("PUB".equals(item[2])) {
                    tvDir.setTextColor(Color.parseColor("#0F766E"));
                    tvDir.setBackgroundColor(Color.parseColor("#F0FDFA"));
                } else {
                    tvDir.setTextColor(Color.parseColor("#92400E"));
                    tvDir.setBackgroundColor(Color.parseColor("#FFFBEB"));
                }
            }
        });

        // Imposta la lista dei Pin
        b.rvPins.setLayoutManager(new LinearLayoutManager(getContext()));
        b.rvPins.setAdapter(new SimpleAdapter<>(PINS, R.layout.item_pin) {
            @Override void bind(View v, String[] item, int pos) {
                ((TextView) v.findViewById(R.id.tvGpio)).setText(item[0]);
                ((TextView) v.findViewById(R.id.tvModulo)).setText(item[1]);
                TextView tvBus = v.findViewById(R.id.tvBus);
                tvBus.setText(item[2]);
                int[] colors = busColors(item[2]);
                tvBus.setTextColor(colors[0]);
                tvBus.setBackgroundColor(colors[1]);
            }
        });

        // Collega questo fragment al manager MQTT per ascoltare eventi (se necessario in futuro)
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).getMqtt().setListener(this);
        }
    }

    // Assegna colori ai vari protocolli hardware per la UI
    private int[] busColors(String bus) {
        switch (bus) {
            case "SPI":  return new int[]{Color.parseColor("#1D4ED8"), Color.parseColor("#EFF6FF")};
            case "I2C":  return new int[]{Color.parseColor("#0F766E"), Color.parseColor("#F0FDFA")};
            case "UART": return new int[]{Color.parseColor("#C2410C"), Color.parseColor("#FFF7ED")};
            case "PWM":  return new int[]{Color.parseColor("#92400E"), Color.parseColor("#FFFBEB")};
            default:     return new int[]{Color.parseColor("#374151"), Color.parseColor("#F3F4F6")};
        }
    }

    // --- Metodi interfaccia MqttManager.Listener (Attualmente non usati per logica live qui) ---
    @Override public void onConnected() {}
    @Override public void onDisconnected() {}
    @Override public void onMessage(String topic, String payload) {}
    @Override public void onError(String message) {}

    @Override public void onDestroyView() { super.onDestroyView(); b = null; }

    // ─── Adapter generico inline per gestire le liste ─────────────────────────────
    abstract static class SimpleAdapter<T> extends RecyclerView.Adapter<SimpleAdapter.VH> {
        private final List<T> items;
        private final int layout;

        SimpleAdapter(List<T> items, int layout) { this.items = items; this.layout = layout; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            View v = LayoutInflater.from(p.getContext()).inflate(layout, p, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) { bind(h.itemView, items.get(pos), pos); }

        @Override public int getItemCount() { return items.size(); }

        abstract void bind(View v, T item, int pos);

        static class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }
}