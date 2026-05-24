package com.example.cancello_iot.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

import com.example.cancello_iot.R;
import com.example.cancello_iot.adapter.UtentiAdapter;
import com.example.cancello_iot.api.ApiService;
import com.example.cancello_iot.databinding.FragmentUtentiBinding;
import com.example.cancello_iot.model.Utente;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UtentiFragment extends Fragment {

    private FragmentUtentiBinding b;
    private UtentiAdapter adapter;
    private List<Utente> allData = new ArrayList<>();
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    @Override
    public View onCreateView(@NonNull LayoutInflater inf, ViewGroup c, Bundle s) {
        b = FragmentUtentiBinding.inflate(inf, c, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        super.onViewCreated(view, s);

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

        loadData();
    }

    private void loadData() {
        exec.submit(() -> {
            try {
                List<Utente> list = new ApiService().getUtenti();
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    allData = list != null ? list : new ArrayList<>();
                    updateStats();
                    adapter.setData(allData);
                    b.swipeRefresh.setRefreshing(false);
                });
            } catch (Exception e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    b.swipeRefresh.setRefreshing(false);
                    Toast.makeText(getContext(), "Errore: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void updateStats() {
        long attivi   = allData.stream().filter(u -> u.attivo).count();
        long rfid     = allData.stream().filter(Utente::hasRfid).count();
        long impronte = allData.stream().filter(Utente::hasImpronta).count();
        b.tvStatTotali.setText(String.valueOf(allData.size()));
        b.tvStatAttivi.setText(String.valueOf(attivi));
        b.tvStatRfid.setText(String.valueOf(rfid));
        b.tvStatImp.setText(String.valueOf(impronte));
    }

    private void filterList(String q) {
        if (q.isEmpty()) { adapter.setData(allData); return; }
        List<Utente> filtered = new ArrayList<>();
        for (Utente u : allData)
            if ((u.name != null && u.name.toLowerCase(Locale.ITALY).contains(q))
                    || (u.email != null && u.email.toLowerCase(Locale.ITALY).contains(q)))
                filtered.add(u);
        adapter.setData(filtered);
    }

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
                    String name  = etName.getText().toString().trim();
                    String email = etEmail.getText().toString().trim();
                    String pass  = etPassword.getText().toString().trim();
                    String rfid  = etRfid.getText().toString().trim();
                    String impStr = etImpronta.getText().toString().trim();
                    Integer imp  = impStr.isEmpty() ? null : Integer.parseInt(impStr);
                    boolean att  = swAttivo.isChecked();

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

    @Override public void onDestroyView() { super.onDestroyView(); b = null; }
}
