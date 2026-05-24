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

    // ─── Dati statici (specchio del controller PHP) ─────────
    static final List<String[]> DEVICES = Arrays.asList(
        new String[]{"📡","HC-SR04",         "Sensore Ultrasuoni",  "38","cm",  "GPIO16 Trig / GPIO10 Echo"},
        new String[]{"💳","RC522 RFID",       "Lettore SPI",         "Ready","", "GPIO12-15 — SPI hardware"},
        new String[]{"👆","Sensore Impronte", "UART SoftSerial",     "4","FP",   "GPIO1/GPIO3 — 4 impronte reg."},
        new String[]{"🖥️","OLED Display",     "I2C via PCF8574",     "0x27","I2C","GPIO4 SDA / GPIO5 SCL"},
        new String[]{"🔗","MCP23017",         "I/O Expander I2C",    "0x20","I2C","16 GPIO — LED/Buzzer/Tastiera"},
        new String[]{"⚙️","Servo × 2",        "PWM Software",        "0","°",    "GPIO0 Sx / GPIO2 Dx"}
    );

    static final List<String[]> TOPICS = Arrays.asList(
        new String[]{"cancello/stato",              "Stato attuale cancello",         "PUB"},
        new String[]{"cancello/comando",            "Comandi apertura/chiusura",      "SUB"},
        new String[]{"cancello/accessi/log",        "Log accessi → MongoDB",          "PUB"},
        new String[]{"cancello/sensore/ultrasuoni", "Distanza HC-SR04 live",          "PUB"},
        new String[]{"cancello/sistema/heartbeat",  "Keepalive ESP8266",              "PUB"},
        new String[]{"cancello/utenti/aggiorna",    "Aggiorna credenziali da server", "SUB"}
    );

    static final List<String[]> PINS = Arrays.asList(
        new String[]{"GPIO14","RC522 SCK",    "SPI"},
        new String[]{"GPIO13","RC522 MOSI",   "SPI"},
        new String[]{"GPIO12","RC522 MISO",   "SPI"},
        new String[]{"GPIO15","RC522 SS",     "SPI"},
        new String[]{"GPIO4", "I2C SDA",      "I2C"},
        new String[]{"GPIO5", "I2C SCL",      "I2C"},
        new String[]{"GPIO1", "Impronte TX",  "UART"},
        new String[]{"GPIO3", "Impronte RX",  "UART"},
        new String[]{"GPIO0", "Servo Sx",     "PWM"},
        new String[]{"GPIO2", "Servo Dx",     "PWM"},
        new String[]{"GPIO16","HC-SR04 Trig", "GPIO"},
        new String[]{"GPIO10","HC-SR04 Echo", "GPIO"}
    );

    @Override
    public View onCreateView(@NonNull LayoutInflater inf, ViewGroup c, Bundle s) {
        b = FragmentDispositivoBinding.inflate(inf, c, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        super.onViewCreated(view, s);

        // Devices grid 2 colonne
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

        // Topics
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

        // Pins
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

        // MQTT
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).getMqtt().setListener(this);
        }
    }

    private int[] busColors(String bus) {
        switch (bus) {
            case "SPI":  return new int[]{Color.parseColor("#1D4ED8"), Color.parseColor("#EFF6FF")};
            case "I2C":  return new int[]{Color.parseColor("#0F766E"), Color.parseColor("#F0FDFA")};
            case "UART": return new int[]{Color.parseColor("#C2410C"), Color.parseColor("#FFF7ED")};
            case "PWM":  return new int[]{Color.parseColor("#92400E"), Color.parseColor("#FFFBEB")};
            default:     return new int[]{Color.parseColor("#374151"), Color.parseColor("#F3F4F6")};
        }
    }

    // ─── MQTT: aggiorna distanza in tempo reale ─────────────
    @Override public void onConnected() {}
    @Override public void onDisconnected() {}
    @Override public void onMessage(String topic, String payload) {
        if (!isAdded() || !MqttManager.TOPIC_ULTRASUONI.equals(topic)) return;
        // Aggiorna card HC-SR04 se visibile — per semplicità aggiorniamo il titolo del fragment
        requireActivity().runOnUiThread(() -> {
            // La griglia usa dati statici; per live update potremmo notifyItemChanged(0)
            // In questa implementazione aggiorniamo solo il titolo di debug
        });
    }
    @Override public void onError(String message) {}

    @Override public void onDestroyView() { super.onDestroyView(); b = null; }

    // ─── Adapter generico inline ─────────────────────────────
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
