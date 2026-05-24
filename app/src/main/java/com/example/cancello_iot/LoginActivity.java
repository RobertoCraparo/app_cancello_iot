package com.example.cancello_iot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cancello_iot.databinding.ActivityLoginBinding;
import com.example.cancello_iot.mqtt.MqttManager;

import java.util.UUID;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding b;
    private MqttManager mqtt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("cancello", MODE_PRIVATE);
        if (prefs.getBoolean("logged_in", false)) { startMain(); return; }

        b = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        mqtt = new MqttManager();
        b.btnLogin.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        String email    = b.etEmail.getText().toString().trim();
        String password = b.etPassword.getText().toString().trim();
        if (email.isEmpty())    { b.etEmail.setError("Inserisci l'email"); return; }
        if (password.isEmpty()) { b.etPassword.setError("Inserisci la password"); return; }

        setLoading(true);
        String clientId = "android_login_" + UUID.randomUUID().toString().substring(0, 8);

        mqtt.setListener(new MqttManager.Listener() {
            @Override public void onConnected() {
                if (isValidAdmin(email, password)) {
                    getSharedPreferences("cancello", MODE_PRIVATE).edit()
                            .putBoolean("logged_in", true)
                            .putString("email", email)
                            .apply();
                    startMain();
                } else {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, "Credenziali non valide", Toast.LENGTH_SHORT).show();
                    mqtt.disconnect();
                }
            }
            @Override public void onDisconnected() { setLoading(false); }
            @Override public void onMessage(String topic, String payload) {}
            @Override public void onError(String message) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, "Errore: " + message, Toast.LENGTH_LONG).show();
            }
        });
        mqtt.connect(clientId);
    }

    /** Demo: valida admin@cancello.local / password
     *  In produzione sostituire con chiamata REST o auth MQTT reale */
    private boolean isValidAdmin(String email, String password) {
        return "admin@cancello.local".equalsIgnoreCase(email) && "password".equals(password);
    }

    private void startMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void setLoading(boolean on) {
        b.btnLogin.setEnabled(!on);
        b.progressBar.setVisibility(on ? View.VISIBLE : View.GONE);
        b.btnLogin.setText(on ? "Connessione…" : getString(R.string.btn_login));
    }
}
