package it.valentina.telecomandoeliov2;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private EditText ip;
    private EditText password;
    private EditText minutes;
    private CheckBox autoMode;
    private TextView countdown;
    private TextView status;
    private TextView target;
    private Button lockButton;
    private Button unlockButton;
    private Button resetButton;
    private Button testButton;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long left = intent.getLongExtra(AutoLockService.EXTRA_REMAINING, 0);
            String s = intent.getStringExtra(AutoLockService.EXTRA_STATUS);

            if (left > 0) {
                countdown.setText(format(left));
            } else if (s != null && s.toLowerCase(Locale.ROOT).contains("blocc")) {
                countdown.setText("0:00");
            }

            if (s != null) status.setText(s);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        seedDefaults();
        requestNotificationsIfNeeded();

        setContentView(buildUi());
        restore();
    }

    @Override
    protected void onStart() {
        super.onStart();

        IntentFilter f = new IntentFilter(AutoLockService.ACTION_TICK);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, f);
        }

        refreshDeadline();
    }

    @Override
    protected void onStop() {
        saveSettings();

        try {
            unregisterReceiver(receiver);
        } catch (Exception ignored) {}

        super.onStop();
    }

    private View buildUi() {
        int pad = dp(16);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(11, 18, 32));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        scroll.addView(root);

        root.addView(text("Telecomando Elio V2", 28, true, Color.WHITE));

        target = text("", 14, false, Color.LTGRAY);
        root.addView(target);

        addGap(root, 12);

        LinearLayout autoRow = row();

        LinearLayout autoText = new LinearLayout(this);
        autoText.setOrientation(LinearLayout.VERTICAL);
        autoText.addView(text("AutoMode", 20, true, Color.WHITE));
        autoText.addView(text("Dopo SBLOCCA: timer → BLOCCA → SCREENSAVER", 13, false, Color.LTGRAY));

        autoMode = new CheckBox(this);
        autoMode.setTextColor(Color.WHITE);
        autoMode.setText("ON");

        autoRow.addView(autoText, new LinearLayout.LayoutParams(0, -2, 1));
        autoRow.addView(autoMode);

        root.addView(card(autoRow));

        countdown = text("—:—", 52, true, Color.WHITE);
        countdown.setGravity(Gravity.CENTER);
        root.addView(countdown);

        status = text("Pronto.", 14, false, Color.rgb(203, 213, 225));
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(status);

        addGap(root, 12);

        lockButton = button("🔒\nBLOCCA", Color.rgb(220, 38, 38));
        lockButton.setTextSize(24);
        lockButton.setMinHeight(dp(120));
        root.addView(lockButton);

        unlockButton = button("🔓\nSBLOCCA", Color.rgb(22, 163, 74));
        unlockButton.setTextSize(24);
        unlockButton.setMinHeight(dp(120));
        root.addView(unlockButton);

        resetButton = button("↻ RESET TIMER", Color.rgb(124, 58, 237));
        resetButton.setTextSize(16);
        resetButton.setMinHeight(dp(62));
        root.addView(resetButton);

        testButton = button("📡 TEST CONNESSIONE", Color.rgb(8, 145, 178));
        testButton.setTextSize(16);
        testButton.setMinHeight(dp(62));
        root.addView(testButton);

        addGap(root, 16);

        LinearLayout settings = new LinearLayout(this);
        settings.setOrientation(LinearLayout.VERTICAL);

        settings.addView(text("Impostazioni", 20, true, Color.WHITE));

        settings.addView(text("IP telefono controllato", 13, false, Color.LTGRAY));
        ip = edit();
        settings.addView(ip);

        settings.addView(text("Password Fully Remote Admin", 13, false, Color.LTGRAY));
        password = edit();
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        settings.addView(password);

        settings.addView(text("Minuti AutoMode", 13, false, Color.LTGRAY));
        minutes = edit();
        minutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        settings.addView(minutes);

        settings.addView(text(
                "BLOCCA: blocca il telefono, aspetta 1,5 secondi e attiva lo screensaver.\n"
                        + "SBLOCCA: toglie lo screensaver, aspetta 0,8 secondi e disabilita Locked Mode.\n"
                        + "Ogni comando viene ritentato fino a 3 volte.",
                12, false, Color.GRAY
        ));

        root.addView(card(settings));

        lockButton.setOnClickListener(v -> doLock(false));
        unlockButton.setOnClickListener(v -> doUnlock());
        resetButton.setOnClickListener(v -> resetTimer());
        testButton.setOnClickListener(v -> doTestConnection());

        autoMode.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean("auto", checked).apply();

            if (!checked) {
                cancelTimerSilently();
                status.setText("AutoMode disattivato.");
            } else {
                setIdleCountdown();
                status.setText("AutoMode attivo.");
            }
        });

        return scroll;
    }

    private void doLock(boolean automatic) {
        saveSettings();
        cancelTimerSilently();
        setButtonsEnabled(false);

        status.setText((automatic ? "BLOCCA automatico" : "BLOCCA") + ": blocco schermo…");

        final String host = ip.getText().toString().trim();
        final String pw = password.getText().toString();

        executor.execute(() -> {
            FullyClient.Result r1 = FullyClient.send(this, host, pw, "enableLockedMode");

            if (!r1.ok) {
                main.post(() -> {
                    status.setText("✕ BLOCCA fallito: " + r1.message);
                    setButtonsEnabled(true);
                });
                return;
            }

            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            FullyClient.Result r2 = FullyClient.send(this, host, pw, "startScreensaver");

            main.post(() -> {
                if (r2.ok) {
                    status.setText("✓ BLOCCATO + screensaver attivato");
                } else {
                    status.setText("⚠ BLOCCATO, ma screensaver non riuscito: " + r2.message);
                }
                setButtonsEnabled(true);
            });
        });
    }

    private void doUnlock() {
        saveSettings();
        setButtonsEnabled(false);

        status.setText("SBLOCCA: tolgo screensaver…");

        final String host = ip.getText().toString().trim();
        final String pw = password.getText().toString();

        executor.execute(() -> {
            FullyClient.Result r1 = FullyClient.send(this, host, pw, "stopScreensaver");

            if (!r1.ok) {
                main.post(() -> {
                    status.setText("✕ TOGLI SCREENSAVER fallito: " + r1.message);
                    setButtonsEnabled(true);
                });
                return;
            }

            try {
                Thread.sleep(800);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            FullyClient.Result r2 = FullyClient.send(this, host, pw, "disableLockedMode");

            main.post(() -> {
                if (r2.ok) {
                    status.setText("✓ Screensaver rimosso + telefono sbloccato");

                    if (autoMode.isChecked()) {
                        startTimer();
                    } else {
                        setIdleCountdown();
                    }
                } else {
                    status.setText("⚠ Screensaver rimosso, ma SBLOCCA non riuscito: " + r2.message);
                }

                setButtonsEnabled(true);
            });
        });
    }

    private void doTestConnection() {
        saveSettings();
        setButtonsEnabled(false);
        status.setText("TEST CONNESSIONE: invio…");

        final String host = ip.getText().toString().trim();
        final String pw = password.getText().toString();

        executor.execute(() -> {
            FullyClient.Result r = FullyClient.send(this, host, pw, "getDeviceInfo");

            main.post(() -> {
                if (r.ok) {
                    status.setText("✓ Fully raggiungibile");
                } else {
                    status.setText("✕ Fully non raggiungibile: " + r.message);
                }

                setButtonsEnabled(true);
            });
        });
    }

    private void resetTimer() {
        cancelTimerSilently();

        if (autoMode.isChecked()) {
            startTimer();
            status.setText("Timer reimpostato.");
        } else {
            setIdleCountdown();
            status.setText("AutoMode disattivato.");
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        lockButton.setEnabled(enabled);
        unlockButton.setEnabled(enabled);
        resetButton.setEnabled(enabled);
        testButton.setEnabled(enabled);
    }

    private void startTimer() {
        saveSettings();

        Intent i = new Intent(this, AutoLockService.class);
        i.setAction(AutoLockService.ACTION_START);

        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }

        int m = safeMinutes();
        countdown.setText(m + ":00");
    }

    private void cancelTimerSilently() {
        stopService(new Intent(this, AutoLockService.class));

        prefs.edit()
                .remove("deadline")
                .apply();

        setIdleCountdown();
    }

    private void saveSettings() {
        String host = ip == null
                ? prefs.getString("ip", "192.168.1.10")
                : ip.getText().toString().trim();

        String pw = password == null
                ? prefs.getString("password", "2222")
                : password.getText().toString();

        int m = safeMinutes();

        prefs.edit()
                .putString("ip", host)
                .putString("password", pw)
                .putInt("minutes", m)
                .putBoolean("auto", autoMode != null && autoMode.isChecked())
                .apply();

        if (target != null) {
            target.setText("Telefono: " + host + "   •   porta 2323");
        }
    }

    private int safeMinutes() {
        int m = prefs.getInt("minutes", 5);

        if (minutes != null) {
            try {
                m = Integer.parseInt(minutes.getText().toString().trim());
            } catch (Exception ignored) {}
        }

        return Math.max(1, Math.min(60, m));
    }

    private void restore() {
        ip.setText(prefs.getString("ip", "192.168.1.10"));
        password.setText(prefs.getString("password", "2222"));
        minutes.setText(String.valueOf(prefs.getInt("minutes", 5)));
        autoMode.setChecked(prefs.getBoolean("auto", true));

        target.setText("Telefono: " + ip.getText() + "   •   porta 2323");
        refreshDeadline();
    }

    private void refreshDeadline() {
        long d = prefs.getLong("deadline", 0);
        long left = d - System.currentTimeMillis();

        if (left > 0) {
            countdown.setText(format(left));
        } else {
            setIdleCountdown();
        }
    }

    private void setIdleCountdown() {
        if (countdown == null || minutes == null || autoMode == null) return;

        if (autoMode.isChecked()) {
            countdown.setText(safeMinutes() + ":00");
        } else {
            countdown.setText("—:—");
        }
    }

    private void seedDefaults() {
        if (!prefs.contains("ip")) {
            prefs.edit()
                    .putString("ip", "192.168.1.10")
                    .putString("password", "2222")
                    .putInt("minutes", 5)
                    .putBoolean("auto", true)
                    .apply();
        }
    }

    private void requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    100
            );
        }
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);

        if (bold) {
            t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        t.setPadding(dp(4), dp(4), dp(4), dp(4));
        return t;
    }

    private EditText edit() {
        EditText e = new EditText(this);

        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.GRAY);
        e.setBackgroundColor(Color.rgb(15, 23, 42));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, dp(52));

        p.setMargins(0, dp(4), 0, dp(10));
        e.setLayoutParams(p);

        return e;
    }

    private Button button(String s, int color) {
        Button b = new Button(this);

        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackgroundColor(color);
        b.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, dp(72));

        p.setMargins(dp(5), dp(6), dp(5), dp(6));
        b.setLayoutParams(p);

        return b;
    }

    private View card(View child) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setBackgroundColor(Color.rgb(17, 24, 39));
        box.addView(child);

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, -2);

        p.setMargins(0, dp(5), 0, dp(8));
        box.setLayoutParams(p);

        return box;
    }

    private void addGap(LinearLayout l, int d) {
        View v = new View(this);
        l.addView(v, new LinearLayout.LayoutParams(1, dp(d)));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static String format(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);

        return (s / 60)
                + ":"
                + String.format(Locale.US, "%02d", s % 60);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
