package com.example.cancello_iot.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cancello_iot.R;
import com.example.cancello_iot.model.Utente;

import java.util.ArrayList;
import java.util.List;

public class UtentiAdapter extends RecyclerView.Adapter<UtentiAdapter.VH> {

    public interface OnActionListener {
        void onModifica(Utente u);
        void onElimina(Utente u);
    }

    private List<Utente> data = new ArrayList<>();
    private OnActionListener listener;

    public void setData(List<Utente> list) {
        data = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setListener(OnActionListener l) { listener = l; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_utente, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Utente u = data.get(pos);
        h.tvInitials.setText(u.getInitials());
        h.tvNome.setText(u.name);
        h.tvEmail.setText(u.email);

        h.tvRfid.setVisibility(u.hasRfid() ? View.VISIBLE : View.GONE);
        h.tvImpronta.setVisibility(u.hasImpronta() ? View.VISIBLE : View.GONE);

        if (u.attivo) {
            h.tvStato.setText("● Attivo");
            h.tvStato.setTextColor(0xFF22C55E);
        } else {
            h.tvStato.setText("● Disattivo");
            h.tvStato.setTextColor(0xFF9CA3AF);
        }

        h.btnModifica.setOnClickListener(v -> { if (listener != null) listener.onModifica(u); });
        h.btnElimina.setOnClickListener(v  -> { if (listener != null) listener.onElimina(u); });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvInitials, tvNome, tvEmail, tvRfid, tvImpronta, tvStato, btnModifica, btnElimina;
        VH(View v) {
            super(v);
            tvInitials  = v.findViewById(R.id.tvInitials);
            tvNome      = v.findViewById(R.id.tvNome);
            tvEmail     = v.findViewById(R.id.tvEmail);
            tvRfid      = v.findViewById(R.id.tvRfid);
            tvImpronta  = v.findViewById(R.id.tvImpronta);
            tvStato     = v.findViewById(R.id.tvStato);
            btnModifica = v.findViewById(R.id.btnModifica);
            btnElimina  = v.findViewById(R.id.btnElimina);
        }
    }
}
