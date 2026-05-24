package com.example.cancello_iot.model;

public class Utente {
    public int id;
    public String name;
    public String email;
    public String ruolo;
    public String uid_rfid;
    public Integer id_impronta;
    public boolean attivo;
    public int accessi_count;

    public String getInitials() {
        if (name == null || name.isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        return parts.length >= 2
                ? (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase()
                : name.substring(0, 1).toUpperCase();
    }

    public boolean hasRfid()     { return uid_rfid != null && !uid_rfid.isEmpty(); }
    public boolean hasImpronta() { return id_impronta != null; }
}
