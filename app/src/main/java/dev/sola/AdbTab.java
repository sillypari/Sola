package dev.sola;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;

import dev.sola.core.Provision;

/**
 * A root shell over gRPC, plus reversible wireless ADB.
 *
 * <p>{@code ExecuteADBCommand} does not use a shell: it splits the string on
 * whitespace and execs it. So every command here is wrapped in {@code sh -c}
 * with its spaces replaced by {@code ${IFS}}, which the shell expands back.
 * That is what makes pipes, redirection and flags with values work.
 *
 * <p>The ADB buttons do what Axiom does, but without Shizuku: they write a
 * public key to {@code /data/local/tmp}, bind-mount it over {@code /adb_keys},
 * and set the network mode to {@code mfg}. The private key never leaves this
 * phone. Undo removes the mount and puts the mode back.
 */
public final class AdbTab implements Tab {

    private static final String PREFS = "ola";
    private static final String KEY_PRIVATE = "adb_private_pem";
    private static final String KEY_PUBLIC = "adb_public_pem";
    private static final String KEY_PREVIOUS = "adb_previous_mode";

    private final MainActivity activity;
    private final Conn conn;
    private final LinearLayout body;
    private EditText input;
    private TextView output;
    private TextView adbResult;

    public AdbTab(MainActivity activity, Conn conn) {
        this.activity = activity;
        this.conn = conn;
        this.body = Ui.column(activity);

        body.addView(provisioning(), topMargin(0));

        // Quick Preset Chips for Common Diagnostics
        TextView tvPresetsLabel = Ui.text(activity, "QUICK DIAGNOSTIC PRESETS", 9.5f, Ui.MUTED);
        tvPresetsLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvPresetsLabel.setLetterSpacing(0.08f);
        tvPresetsLabel.setPadding(0, Ui.dp(activity, 14), 0, Ui.dp(activity, 6));
        body.addView(tvPresetsLabel);

        android.widget.HorizontalScrollView hScroll = new android.widget.HorizontalScrollView(activity);
        hScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presets = Ui.row(activity);
        presets.setPadding(0, 0, 0, Ui.dp(activity, 8));

        String[][] quickCmds = {
                {"MoveOS Version", "getprop ro.build.version.release"},
                {"System Uptime", "uptime"},
                {"CAN Bus vcan0", "candump -n 10 vcan0"},
                {"Reboot Head Unit", "reboot"},
                {"Kernel Dmesg", "dmesg | tail -n 25"}
        };
        for (int i = 0; i < quickCmds.length; i++) {
            final String cmd = quickCmds[i][1];
            TextView chip = new TextView(activity);
            chip.setText(quickCmds[i][0]);
            chip.setTextSize(11);
            chip.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setIncludeFontPadding(false);
            chip.setTextColor(Ui.PRIMARY);
            chip.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 10, 0));
            chip.setPadding(Ui.dp(activity, 12), 0, Ui.dp(activity, 12), 0);
            chip.setOnClickListener(v -> {
                input.setText(cmd);
                run();
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(activity, 30));
            if (i > 0) clp.leftMargin = Ui.dp(activity, 8);
            presets.addView(chip, clp);
        }
        hScroll.addView(presets);
        body.addView(hScroll);

        // Terminal Command Input Container
        LinearLayout inputCard = Ui.column(activity);
        inputCard.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 10, 0));
        inputCard.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 10), Ui.dp(activity, 12), Ui.dp(activity, 10));

        TextView tvInputPrompt = Ui.text(activity, "ROOT COMMAND PROMPT ($ sh -c)", 9.5f, Ui.MUTED);
        tvInputPrompt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvInputPrompt.setLetterSpacing(0.06f);
        inputCard.addView(tvInputPrompt);

        input = new EditText(activity);
        input.setText("getprop ro.build.version.release");
        input.setTextSize(13);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.DIM);
        input.setTypeface(android.graphics.Typeface.MONOSPACE);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setMinLines(2);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setBackground(null);
        input.setPadding(0, Ui.dp(activity, 6), 0, Ui.dp(activity, 6));
        inputCard.addView(input);

        // Action Buttons Row inside inputCard
        LinearLayout buttons = Ui.row(activity);
        buttons.setPadding(0, Ui.dp(activity, 6), 0, 0);

        Button btnRun = Ui.accent(activity, "Run Command", v -> run());
        btnRun.setMinHeight(Ui.dp(activity, 34));
        btnRun.setMinimumHeight(Ui.dp(activity, 34));
        btnRun.setTextSize(11);
        btnRun.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        Button btnClear = Ui.button(activity, "Clear", v -> {
            input.setText("");
            output.setText("");
        });
        btnClear.setMinHeight(Ui.dp(activity, 34));
        btnClear.setMinimumHeight(Ui.dp(activity, 34));
        btnClear.setTextSize(11);
        btnClear.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        buttons.addView(btnRun, weight());
        LinearLayout.LayoutParams gap = weight();
        gap.leftMargin = Ui.dp(activity, 8);
        buttons.addView(btnClear, gap);

        inputCard.addView(buttons);
        body.addView(inputCard, topMargin(4));

        // Shell Output Terminal Card
        TextView tvOutputLabel = Ui.text(activity, "SHELL CONSOLE OUTPUT", 9.5f, Ui.MUTED);
        tvOutputLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvOutputLabel.setLetterSpacing(0.08f);
        tvOutputLabel.setPadding(0, Ui.dp(activity, 14), 0, Ui.dp(activity, 6));
        body.addView(tvOutputLabel);

        output = Ui.text(activity, "Ready. Tap any preset above or type a command to execute on Android SOM.", 11.5f, Ui.DIM);
        output.setTypeface(android.graphics.Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        output.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 10, 0));
        output.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 10),
                Ui.dp(activity, 12), Ui.dp(activity, 10));
        output.setMinLines(5);
        body.addView(output);
    }

    // ---------------------------------------------------------- provisioning

    private View provisioning() {
        LinearLayout card = Ui.column(activity);
        card.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12), Ui.dp(activity, 14), Ui.dp(activity, 12));
        card.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 10, 0));

        TextView tvTitle = Ui.text(activity, "Wireless ADB Provisioning", 13.5f, Ui.TEXT);
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(tvTitle);

        TextView note = Ui.note(activity,
                "Temporarily mounts public key into /adb_keys and switches network mode to mfg. Zero flash writes; reverts cleanly on reboot.");
        note.setPadding(0, Ui.dp(activity, 2), 0, Ui.dp(activity, 10));
        card.addView(note);

        LinearLayout row = Ui.row(activity);

        Button btnUnlock = Ui.accent(activity, "Unlock ADB", v -> confirmProvision());
        btnUnlock.setMinHeight(Ui.dp(activity, 34));
        btnUnlock.setMinimumHeight(Ui.dp(activity, 34));
        btnUnlock.setTextSize(10.5f);
        btnUnlock.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        Button btnUndo = Ui.button(activity, "Undo", v -> undo());
        btnUndo.setMinHeight(Ui.dp(activity, 34));
        btnUndo.setMinimumHeight(Ui.dp(activity, 34));
        btnUndo.setTextSize(10.5f);
        btnUndo.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        Button btnStatus = Ui.button(activity, "Status", v -> status());
        btnStatus.setMinHeight(Ui.dp(activity, 34));
        btnStatus.setMinimumHeight(Ui.dp(activity, 34));
        btnStatus.setTextSize(10.5f);
        btnStatus.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        row.addView(btnUnlock, new LinearLayout.LayoutParams(0, Ui.dp(activity, 34), 1f));

        LinearLayout.LayoutParams g1 = new LinearLayout.LayoutParams(0, Ui.dp(activity, 34), 1f);
        g1.leftMargin = Ui.dp(activity, 6);
        row.addView(btnUndo, g1);

        LinearLayout.LayoutParams g2 = new LinearLayout.LayoutParams(0, Ui.dp(activity, 34), 1f);
        g2.leftMargin = Ui.dp(activity, 6);
        row.addView(btnStatus, g2);

        card.addView(row);

        adbResult = Ui.text(activity, "", 11, Ui.DIM);
        adbResult.setTypeface(android.graphics.Typeface.MONOSPACE);
        adbResult.setTextIsSelectable(true);
        adbResult.setPadding(0, Ui.dp(activity, 8), 0, 0);
        card.addView(adbResult);

        return card;
    }

    private void confirmProvision() {
        new AlertDialog.Builder(activity)
                .setTitle("Unlock wireless ADB?")
                .setMessage("This writes a key you control to the scooter and opens "
                        + "adbd on port 5555.\n\n"
                        + "• the key is stored in this app only\n"
                        + "• the mount is removed by Undo or a reboot\n"
                        + "• the network mode is set to mfg and restored by Undo\n\n"
                        + "Nothing is flashed. Do this on your own scooter.")
                .setPositiveButton("Unlock", (d, w) -> provision())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void provision() {
        if (!conn.ready()) {
            adbResult.setText("Connect to the scooter first.");
            adbResult.setTextColor(Ui.WARN);
            return;
        }
        adbResult.setText("Writing the key ...");
        adbResult.setTextColor(Ui.DIM);
        conn.run(rpc -> {
            String previous = rpc.networkMode();
            KeyPair pair = keyPair();
            String line = Provision.publicKeyLine((RSAPublicKey) pair.getPublic(), "olatool");
            prefs().edit().putString(KEY_PREVIOUS, previous == null ? "" : previous).apply();
            return rpc.runScript("olatool-adb.sh",
                    Provision.provisionScript(line, previous), 60_000);
        }, (value, error) -> {
            if (error != null) {
                adbResult.setText("Failed: " + error.getMessage());
                adbResult.setTextColor(Ui.BAD);
            } else {
                adbResult.setText(value + "\n\nNow try: adb connect <address>:5555");
                adbResult.setTextColor(Ui.ACCENT);
            }
        });
    }

    private void undo() {
        if (!conn.ready()) {
            adbResult.setText("Connect to the scooter first.");
            adbResult.setTextColor(Ui.WARN);
            return;
        }
        adbResult.setText("Undoing ...");
        adbResult.setTextColor(Ui.DIM);
        String previous = prefs().getString(KEY_PREVIOUS, "");
        conn.run(rpc -> rpc.runScript("olatool-adb-undo.sh",
                Provision.undoScript(previous), 30_000), (value, error) -> {
            if (error != null) {
                adbResult.setText("Failed: " + error.getMessage());
                adbResult.setTextColor(Ui.BAD);
            } else {
                adbResult.setText(value);
                adbResult.setTextColor(Ui.ACCENT);
            }
        });
    }

    private void status() {
        if (!conn.ready()) {
            adbResult.setText("Connect to the scooter first.");
            adbResult.setTextColor(Ui.WARN);
            return;
        }
        adbResult.setText("Checking ...");
        adbResult.setTextColor(Ui.DIM);
        conn.run(rpc -> rpc.runScript("olatool-status.sh",
                Provision.statusScript(), 30_000), (value, error) -> {
            if (error != null) {
                adbResult.setText("Failed: " + error.getMessage());
                adbResult.setTextColor(Ui.BAD);
            } else {
                adbResult.setText(value);
                adbResult.setTextColor(Ui.TEXT);
            }
        });
    }

    /**
     * Reuses the key in preferences, or makes one and stores it. The private
     * half is never printed, logged, or sent anywhere.
     */
    private KeyPair keyPair() throws Exception {
        SharedPreferences prefs = prefs();
        String storedPrivate = prefs.getString(KEY_PRIVATE, null);
        String storedPublic = prefs.getString(KEY_PUBLIC, null);
        if (storedPrivate != null && storedPublic != null) {
            try {
                PrivateKey privateKey = Provision.parsePrivateKey(storedPrivate);
                PublicKey publicKey = Provision.parsePublicKey(storedPublic);
                return new KeyPair(publicKey, privateKey);
            } catch (Exception ignored) {
                // A broken saved key is replaced rather than blocking the feature.
            }
        }
        KeyPair pair = Provision.newKeyPair();
        prefs.edit()
                .putString(KEY_PRIVATE, Provision.privateKeyPem(pair.getPrivate()))
                .putString(KEY_PUBLIC, Provision.publicKeyPem(pair.getPublic()))
                .apply();
        return pair;
    }

    private SharedPreferences prefs() {
        return activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ----------------------------------------------------------------- shell

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams topMargin(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(activity, dp);
        return lp;
    }

    private void run() {
        String command = input.getText().toString().trim();
        if (command.isEmpty()) {
            output.setText("Nothing to run.");
            return;
        }
        if (!conn.ready()) {
            output.setText("Connect to the scooter first.");
            return;
        }
        output.setText("...");
        conn.run(rpc -> rpc.shell(command, 60_000), (text, error) -> {
            if (error != null) {
                output.setText("Failed: " + error.getMessage());
            } else if (text == null || text.trim().isEmpty()) {
                output.setText("(no output)");
            } else {
                output.setText(text);
            }
        });
    }

    @Override
    public View view() {
        return body;
    }

    @Override
    public void onShow() {
    }

    @Override
    public void onHide() {
    }
}
