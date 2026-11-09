package dev.sola;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.sola.core.Msg;

/**
 * Reconstructed Vehicle Operation Mode & Network Security Panel.
 * Manages MoveOS network security mode (persist.ola.security.scootermode)
 * and inspects live vehicle lifecycle operation state (/data/vendor/ola/v-op-mode.json).
 * Zero emojis, clean typography hierarchy, Material 3 rounded cards.
 */
public final class ModeTab implements Tab {

    public interface ModeListener {
        void onModeChanged(String networkMode, String vehicleMode);
    }

    private final MainActivity activity;
    private final Conn conn;
    private final LinearLayout body;

    // IPv4 Safety Guard
    private final LinearLayout ipv4AlertBox;
    private final TextView tvIpv4Warning;

    // Network Mode Card
    private final TextView tvNetTitle;
    private final TextView tvNetPin;
    private final TextView tvNetDetail;
    private final Button btnCustomer;
    private final Button btnService;

    // Vehicle Operation Mode Card
    private final TextView tvVehPin;
    private final TextView tvVehState;
    private final TextView tvVehMeta;

    // Feedback & Console Output
    private final TextView tvResult;
    private final Button btnRefresh;

    private ModeListener listener;

    public ModeTab(MainActivity activity, Conn conn) {
        this.activity = activity;
        this.conn = conn;
        this.body = Ui.column(activity);

        // 1. IPv4 Safety Guard Callout (shown only when connected via IPv4)
        ipv4AlertBox = Ui.column(activity);
        ipv4AlertBox.setBackground(Ui.round(activity, Ui.alpha(Ui.WARNING, 0x18), 10, Ui.alpha(Ui.WARNING, 0x44)));
        ipv4AlertBox.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 10), Ui.dp(activity, 14), Ui.dp(activity, 10));
        ipv4AlertBox.setVisibility(View.GONE);

        TextView tvIpv4Title = Ui.text(activity, "IPv4 CONNECTION GUARD ACTIVE", 9.5f, Ui.WARNING);
        tvIpv4Title.setTypeface(Typeface.DEFAULT_BOLD);
        tvIpv4Title.setLetterSpacing(0.08f);
        ipv4AlertBox.addView(tvIpv4Title);

        tvIpv4Warning = Ui.text(activity,
                "You are connected over IPv4. Switching to Customer mode closes IPv4 ports (50005/5555) and will drop this session immediately. Reconnect via IPv6 to switch modes.",
                11.5f, Ui.TEXT);
        tvIpv4Warning.setLineSpacing(0, 1.25f);
        tvIpv4Warning.setPadding(0, Ui.dp(activity, 4), 0, 0);
        ipv4AlertBox.addView(tvIpv4Warning);

        body.addView(ipv4AlertBox);
        addSpace(body, 10);

        // 2. Network Firewall & ADB Mode Card
        LinearLayout netCard = Ui.column(activity);
        netCard.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 10, 0));
        netCard.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12), Ui.dp(activity, 14), Ui.dp(activity, 12));

        LinearLayout netHeader = Ui.row(activity);
        netHeader.setGravity(Gravity.CENTER_VERTICAL);

        tvNetTitle = Ui.text(activity, "Network Firewall & ADB Mode", 13.5f, Ui.TEXT);
        tvNetTitle.setTypeface(Typeface.DEFAULT_BOLD);
        netHeader.addView(tvNetTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        tvNetPin = Ui.pin(activity, "CHECKING...", Ui.DIM);
        netHeader.addView(tvNetPin);
        netCard.addView(netHeader);

        tvNetDetail = Ui.text(activity,
                "Controls firewall ports TCP 50005/5555 and adbd daemon authorization. Road riding, charging, and motor controls are never affected.",
                11.5f, Ui.MUTED);
        tvNetDetail.setLineSpacing(0, 1.25f);
        tvNetDetail.setPadding(0, Ui.dp(activity, 6), 0, Ui.dp(activity, 12));
        netCard.addView(tvNetDetail);

        // Paired Action Buttons
        LinearLayout btnRow = Ui.row(activity);

        btnCustomer = Ui.button(activity, "Switch to Customer", v -> confirm("cust"));
        btnCustomer.setMinHeight(Ui.dp(activity, 34));
        btnCustomer.setMinimumHeight(Ui.dp(activity, 34));
        btnCustomer.setTextSize(10.5f);
        btnCustomer.setTypeface(Typeface.DEFAULT_BOLD);

        btnService = Ui.button(activity, "Switch to Service", v -> confirm("serv"));
        btnService.setMinHeight(Ui.dp(activity, 34));
        btnService.setMinimumHeight(Ui.dp(activity, 34));
        btnService.setTextSize(10.5f);
        btnService.setTypeface(Typeface.DEFAULT_BOLD);

        btnRow.addView(btnCustomer, new LinearLayout.LayoutParams(0, Ui.dp(activity, 34), 1f));

        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, Ui.dp(activity, 34), 1f);
        spLp.leftMargin = Ui.dp(activity, 8);
        btnRow.addView(btnService, spLp);

        netCard.addView(btnRow);
        body.addView(netCard);

        addSpace(body, 10);

        // 3. MoveOS Vehicle Lifecycle Operation Mode Card
        LinearLayout vehCard = Ui.column(activity);
        vehCard.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 10, 0));
        vehCard.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12), Ui.dp(activity, 14), Ui.dp(activity, 12));

        LinearLayout vehHeader = Ui.row(activity);
        vehHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvVehTitle = Ui.text(activity, "MoveOS Vehicle Lifecycle", 13.5f, Ui.TEXT);
        tvVehTitle.setTypeface(Typeface.DEFAULT_BOLD);
        vehHeader.addView(tvVehTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        tvVehPin = Ui.pin(activity, "READ ONLY", Ui.MUTED);
        vehHeader.addView(tvVehPin);
        vehCard.addView(vehHeader);

        tvVehState = Ui.text(activity, "Vehicle Mode: Checking...", 12.5f, Ui.TEXT);
        tvVehState.setTypeface(Typeface.DEFAULT_BOLD);
        tvVehState.setPadding(0, Ui.dp(activity, 6), 0, Ui.dp(activity, 2));
        vehCard.addView(tvVehState);

        tvVehMeta = Ui.text(activity, "Source: /data/vendor/ola/v-op-mode.json", 11, Ui.MUTED);
        vehCard.addView(tvVehMeta);

        TextView tvVehNotice = Ui.text(activity,
                "Protected MoveOS lifecycle property. Maintained read-only to prevent bricking or factory lockouts.",
                11, Ui.DIM);
        tvVehNotice.setLineSpacing(0, 1.25f);
        tvVehNotice.setPadding(0, Ui.dp(activity, 6), 0, 0);
        vehCard.addView(tvVehNotice);

        body.addView(vehCard);

        addSpace(body, 10);

        // 4. Output Status Banner
        tvResult = Ui.text(activity, "", 11, Ui.DIM);
        tvResult.setTypeface(Typeface.MONOSPACE);
        tvResult.setPadding(Ui.dp(activity, 2), 0, Ui.dp(activity, 2), Ui.dp(activity, 6));
        tvResult.setVisibility(View.GONE);
        body.addView(tvResult);

        // 5. Refresh State Control
        btnRefresh = Ui.button(activity, "Refresh Mode State", v -> refresh());
        btnRefresh.setMinHeight(Ui.dp(activity, 34));
        btnRefresh.setMinimumHeight(Ui.dp(activity, 34));
        btnRefresh.setTextSize(11);
        btnRefresh.setTypeface(Typeface.DEFAULT_BOLD);
        body.addView(btnRefresh, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 34)));
    }

    public void setModeListener(ModeListener listener) {
        this.listener = listener;
    }

    private void addSpace(LinearLayout parent, int dp) {
        View spacer = new View(activity);
        parent.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, dp)));
    }

    @Override
    public View view() {
        return body;
    }

    @Override
    public void onShow() {
        refresh();
    }

    @Override
    public void onHide() {
    }

    public void refresh() {
        if (!conn.ready()) {
            tvNetPin.setText("DISCONNECTED");
            tvNetPin.setTextColor(Ui.DIM);
            tvNetPin.setBackground(Ui.round(activity, Ui.alpha(Ui.MUTED, 0x22), 12, 0));
            tvNetDetail.setText("Connect to scooter to inspect firewall status and vehicle operation mode.");
            tvVehState.setText("Vehicle Mode: Disconnected");
            tvVehPin.setText("OFFLINE");
            tvVehPin.setTextColor(Ui.DIM);
            tvVehPin.setBackground(Ui.round(activity, Ui.alpha(Ui.MUTED, 0x22), 12, 0));
            btnCustomer.setEnabled(false);
            btnService.setEnabled(false);
            btnCustomer.setAlpha(0.4f);
            btnService.setAlpha(0.4f);
            ipv4AlertBox.setVisibility(View.GONE);
            return;
        }

        boolean ipv6 = conn.ipv6();
        btnCustomer.setEnabled(ipv6);
        btnService.setEnabled(ipv6);
        btnCustomer.setAlpha(ipv6 ? 1f : 0.4f);
        btnService.setAlpha(ipv6 ? 1f : 0.4f);
        ipv4AlertBox.setVisibility(ipv6 ? View.GONE : View.VISIBLE);

        conn.run(rpc -> {
            String mode = rpc.networkMode();
            String vehicle;
            String vehicleShort;
            try {
                Msg.OpMode op = rpc.vehicleOperationMode();
                vehicleShort = op.modeName();
                vehicle = "Lifecycle Mode: " + op.modeName().toUpperCase()
                        + (TextUtils.isEmpty(op.modifiedBy) ? "" : "\nSet By: " + op.modifiedBy)
                        + (TextUtils.isEmpty(op.modifiedTsStr) ? "" : "  ·  " + op.modifiedTsStr);
            } catch (RuntimeException e) {
                vehicleShort = "unavailable";
                vehicle = "Lifecycle Mode: Unavailable (" + e.getMessage() + ")";
            }
            return new String[]{mode, vehicle, vehicleShort};
        }, (value, error) -> {
            if (error != null) {
                tvNetPin.setText("RPC ERROR");
                tvNetPin.setTextColor(Ui.BAD);
                tvNetPin.setBackground(Ui.round(activity, Ui.alpha(Ui.BAD, 0x22), 12, 0));
                tvResult.setText("RPC error: " + error.getMessage());
                tvResult.setTextColor(Ui.BAD);
                tvResult.setVisibility(View.VISIBLE);
                return;
            }
            showMode(value[0]);
            tvVehState.setText(value[1]);
            String vShort = value[2];
            tvVehPin.setText(vShort.toUpperCase());
            if ("customer".equalsIgnoreCase(vShort)) {
                tvVehPin.setTextColor(Ui.POSITIVE);
                tvVehPin.setBackground(Ui.round(activity, Ui.alpha(Ui.POSITIVE, 0x22), 12, 0));
            } else if ("service".equalsIgnoreCase(vShort) || "mfg".equalsIgnoreCase(vShort)) {
                tvVehPin.setTextColor(Ui.WARNING);
                tvVehPin.setBackground(Ui.round(activity, Ui.alpha(Ui.WARNING, 0x22), 12, 0));
            } else {
                tvVehPin.setTextColor(Ui.MUTED);
                tvVehPin.setBackground(Ui.round(activity, Ui.alpha(Ui.MUTED, 0x22), 12, 0));
            }

            if (listener != null) {
                listener.onModeChanged(value[0], vShort);
            }
        });
    }

    private void showMode(String raw) {
        String mode = raw == null ? "" : raw.trim();
        if ("cust".equals(mode)) {
            tvNetPin.setText("CUSTOMER (LOCKED)");
            tvNetPin.setTextColor(Ui.POSITIVE);
            tvNetPin.setBackground(Ui.round(activity, Ui.alpha(Ui.POSITIVE, 0x22), 12, 0));
            tvNetDetail.setText("IPv4 ports TCP 50005 & 5555 are closed. adbd rejects keys. Recommended secure state for riding.");

            btnCustomer.setBackground(Ui.round(activity, Ui.PRIMARY, 10, 0));
            btnCustomer.setTextColor(Ui.ACCENT_TEXT);

            btnService.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 10, 0));
            btnService.setTextColor(Ui.TEXT);
        } else if ("serv".equals(mode)) {
            tvNetPin.setText("SERVICE (ADB UNLOCKED)");
            tvNetPin.setTextColor(Ui.WARNING);
            tvNetPin.setBackground(Ui.round(activity, Ui.alpha(Ui.WARNING, 0x22), 12, 0));
            tvNetDetail.setText("IPv4 ports TCP 50005 & 5555 are open and adbd is active. Enables ADB shell & screen mirror; switch back when done.");

            btnService.setBackground(Ui.round(activity, Ui.WARNING, 10, 0));
            btnService.setTextColor(android.graphics.Color.WHITE);

            btnCustomer.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 10, 0));
            btnCustomer.setTextColor(Ui.TEXT);
        } else if (mode.isEmpty()) {
            tvNetPin.setText("NOT SET");
            tvNetPin.setTextColor(Ui.DIM);
            tvNetPin.setBackground(Ui.round(activity, Ui.alpha(Ui.MUTED, 0x22), 12, 0));
        } else {
            tvNetPin.setText(mode.toUpperCase());
            tvNetPin.setTextColor(Ui.TEXT);
            tvNetPin.setBackground(Ui.round(activity, Ui.alpha(Ui.MUTED, 0x22), 12, 0));
        }
    }

    private void confirm(String mode) {
        boolean customer = "cust".equals(mode);
        String title = customer ? "Switch to Customer Mode?" : "Switch to Service Mode?";
        String message = customer
                ? "This sets persist.ola.security.scootermode=cust.\n\n"
                + "• TCP 50005 and 5555 close over IPv4\n"
                + "• adbd stops trusting unauthorized keys\n"
                + "• Active IPv6 session remains connected\n\n"
                + "Riding, charging and motor performance are unaffected."
                : "This sets persist.ola.security.scootermode=serv.\n\n"
                + "• TCP 50005 and 5555 open over IPv4\n"
                + "• adbd restarts and accepts diagnostic keys\n\n"
                + "Recommended only during active debugging.";

        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Switch", (d, w) -> apply(mode))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void apply(String mode) {
        tvResult.setVisibility(View.VISIBLE);
        tvResult.setText("Switching mode via MoveOS RPC...");
        tvResult.setTextColor(Ui.DIM);
        conn.run(rpc -> {
            Msg.Response response = rpc.setNetworkMode(mode, 20_000);
            Thread.sleep(700);
            return new String[]{response.toString(), rpc.networkMode()};
        }, (value, error) -> {
            if (error != null) {
                tvResult.setText("Failed: " + error.getMessage());
                tvResult.setTextColor(Ui.BAD);
                return;
            }
            showMode(value[1]);
            boolean landed = mode.equals(value[1].trim());
            tvResult.setText(landed ? "Success: Network mode is now " + value[1].trim() + "."
                    : "Property not modified: " + value[0]);
            tvResult.setTextColor(landed ? Ui.POSITIVE : Ui.WARN);
            if (listener != null) {
                listener.onModeChanged(value[1].trim(), null);
            }
        });
    }
}
