package com.example.cancello_iot.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cancello_iot.R;
import com.example.cancello_iot.model.Accesso;

import java.util.ArrayList;
import java.util.List;

public class AccessiAdapter extends RecyclerView.Adapter<AccessiAdapter.VH> {

    private List<Accesso> data = new ArrayList<>();

    public void setData(List<Accesso> list) {
        data = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    public List<Accesso> getData() { return data; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_accesso, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Accesso a = data.get(pos);
        h.tvInitials.setText(a.getInitials());
        h.tvNome.setText(a.nome != null ? a.nome : "Sconosciuto");
        h.tvMetodo.setText(a.getMetodoLabel());
        h.tvOra.setText(a.getFormattedTime());
        int color = a.isOk() ? 0xFF22C55E : 0xFFEF4444;
        h.dotEsito.setBackgroundColor(color);
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvInitials, tvNome, tvMetodo, tvOra;
        View dotEsito;
        VH(View v) {
            super(v);
            tvInitials = v.findViewById(R.id.tvInitials);
            tvNome     = v.findViewById(R.id.tvNome);
            tvMetodo   = v.findViewById(R.id.tvMetodo);
            tvOra      = v.findViewById(R.id.tvOra);
            dotEsito   = v.findViewById(R.id.dotEsito);
        }
    }
}
