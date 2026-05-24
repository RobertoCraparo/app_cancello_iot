package com.example.cancello_iot.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Accesso {
    public int id;
    public String nome;
    public String ruolo;
    public String metodo;
    public String esito;
    public long ts;

    public String getMetodoLabel() {
        if (metodo == null) return "—";
        switch (metodo) {
            case "rfid": return "💳 Badge RFID";
            case "fp":   return "👆 Impronta";
            default:     return "🔘 Pulsante";
        }
    }

    public String getFormattedTime() {
        if (ts == 0) return "—";
        return new SimpleDateFormat("dd/MM HH:mm", Locale.ITALY)
                .format(new Date(ts * 1000));
    }

    public String getInitials() {
        if (nome == null || nome.isEmpty()) return "?";
        String[] parts = nome.trim().split("\\s+");
        return parts.length >= 2
                ? (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase()
                : nome.substring(0, 1).toUpperCase();
    }

    public boolean isOk() { return "ok".equals(esito); }
}
