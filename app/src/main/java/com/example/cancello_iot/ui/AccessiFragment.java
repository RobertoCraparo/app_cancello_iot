package com.example.cancello_iot.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

import com.example.cancello_iot.adapter.AccessiAdapter;
import com.example.cancello_iot.api.ApiService;
import com.example.cancello_iot.databinding.FragmentAccessiBinding;
import com.example.cancello_iot.model.Accesso;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccessiFragment extends Fragment {

    private FragmentAccessiBinding b;
    private AccessiAdapter adapter;
    private List<Accesso> allData = new ArrayList<>();
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    private String filterSearch = "", filterMetodo = "", filterEsito = "", filterPeriodo = "";

    @Override
    public View onCreateView(@NonNull LayoutInflater inf, ViewGroup c, Bundle s) {
        b = FragmentAccessiBinding.inflate(inf, c, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        super.onViewCreated(view, s);

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

        // Ricerca testuale
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

        loadData();
    }

    private void loadData() {
        exec.submit(() -> {
            try {
                List<Accesso> list = new ApiService().getAccessi();
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    allData = list != null ? list : new ArrayList<>();
                    applyFilters();
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

    private void applyFilters() {
        long now = System.currentTimeMillis() / 1000;
        long soglia = switch (filterPeriodo) {
            case "oggi" -> { long d = now - (now % 86400); yield d; }
            case "7"  -> now - 7  * 86400L;
            case "30" -> now - 30 * 86400L;
            default   -> 0L;
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

    @Override public void onDestroyView() { super.onDestroyView(); b = null; }

    /** Helper per evitare boilerplate */
    abstract static class SimpleItemSelected implements AdapterView.OnItemSelectedListener {
        @Override public void onNothingSelected(AdapterView<?> p) {}
    }
}
