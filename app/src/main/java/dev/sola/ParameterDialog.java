package dev.sola;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/**
 * Interactive inspection dialog displayed when holding any metric or card.
 * Presents plain-English explanation, safe operating bounds, live reading,
 * and low-level CAN / DBC technical specifications.
 * Zero emojis. Material 3 Obsidian theme.
 */
public final class ParameterDialog {

    private ParameterDialog() {}

    public static void show(Context context, String title, String category,
                            String liveValue, String unit, String dbcName,
                            String canId, String safeRange, String description) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);

        LinearLayout root = Ui.column(context);
        root.setPadding(Ui.dp(context, 20), Ui.dp(context, 18), Ui.dp(context, 20), Ui.dp(context, 20));
        root.setBackground(Ui.round(context, Ui.SURFACE, 20, Ui.LINE));

        // 1. Header Row (Category pill + CAN ID pill)
        LinearLayout headerRow = Ui.row(context);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        String catText = (category != null && !category.isEmpty()) ? category.toUpperCase(Locale.ROOT) : "DIAGNOSTIC SIGNAL";
        TextView catPill = Ui.pin(context, catText, Ui.PRIMARY);
        headerRow.addView(catPill);

        if (canId != null && !canId.isEmpty()) {
            View s = new View(context);
            s.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(context, 8), 1));
            headerRow.addView(s);

            String canText = canId.startsWith("0x") ? canId : "0x" + canId;
            TextView canPill = Ui.pin(context, "CAN " + canText, Ui.MUTED);
            headerRow.addView(canPill);
        }
        root.addView(headerRow);

        // 2. Title
        TextView tvTitle = Ui.text(context, title, 18, Ui.TEXT);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setPadding(0, Ui.dp(context, 12), 0, Ui.dp(context, 6));
        root.addView(tvTitle);

        // 3. Live Value Hero Row
        LinearLayout valRow = Ui.row(context);
        valRow.setGravity(Gravity.BOTTOM);
        valRow.setPadding(0, 0, 0, Ui.dp(context, 14));

        String displayVal = (liveValue != null && !liveValue.isEmpty()) ? liveValue : "--";
        TextView tvVal = Ui.text(context, displayVal, 28, Ui.PRIMARY);
        tvVal.setTypeface(Typeface.DEFAULT_BOLD);
        valRow.addView(tvVal);

        if (unit != null && !unit.isEmpty() && !displayVal.contains(unit)) {
            TextView tvUnit = Ui.text(context, " " + unit, 14, Ui.MUTED);
            tvUnit.setPadding(Ui.dp(context, 4), 0, 0, Ui.dp(context, 4));
            valRow.addView(tvUnit);
        }
        root.addView(valRow);

        // Divider
        View div = new View(context);
        div.setBackgroundColor(Ui.LINE);
        div.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        root.addView(div);

        // 4. "What This Measures" Section
        TextView lblDesc = Ui.text(context, "WHAT THIS MEASURES", 10, Ui.DIM);
        lblDesc.setTypeface(Typeface.DEFAULT_BOLD);
        lblDesc.setLetterSpacing(0.06f);
        lblDesc.setPadding(0, Ui.dp(context, 14), 0, Ui.dp(context, 6));
        root.addView(lblDesc);

        String desc = (description != null && !description.isEmpty())
                ? description
                : "Vehicle telemetry signal monitored in real-time from the electronic control unit.";
        TextView tvDesc = Ui.text(context, desc, 13, Ui.TEXT);
        tvDesc.setLineSpacing(Ui.dp(context, 3), 1.0f);
        root.addView(tvDesc);

        // 5. Technical Specifications Box
        TextView lblTech = Ui.text(context, "TECHNICAL SPECIFICATIONS", 10, Ui.DIM);
        lblTech.setTypeface(Typeface.DEFAULT_BOLD);
        lblTech.setLetterSpacing(0.06f);
        lblTech.setPadding(0, Ui.dp(context, 16), 0, Ui.dp(context, 8));
        root.addView(lblTech);

        LinearLayout techBox = Ui.column(context);
        techBox.setPadding(Ui.dp(context, 12), Ui.dp(context, 10), Ui.dp(context, 12), Ui.dp(context, 10));
        techBox.setBackground(Ui.round(context, Ui.SURFACE_HIGH, 12, 0));

        addSpecRow(context, techBox, "DBC Signal Name", (dbcName != null && !dbcName.isEmpty()) ? dbcName : title);
        addSpecRow(context, techBox, "CAN Message ID", (canId != null && !canId.isEmpty()) ? canId : "Proprietary Bus");
        addSpecRow(context, techBox, "Safe Operating Range", (safeRange != null && !safeRange.isEmpty()) ? safeRange : "OEM Calibrated");
        addSpecRow(context, techBox, "Source Controller", (category != null && !category.isEmpty()) ? category + " ECU" : "Vehicle Bus");
        root.addView(techBox);

        // 6. Close Button
        Button btnClose = new Button(context);
        btnClose.setText("CLOSE");
        btnClose.setTextSize(13);
        btnClose.setTextColor(Ui.TEXT);
        btnClose.setTypeface(Typeface.DEFAULT_BOLD);
        btnClose.setBackground(Ui.round(context, Ui.SURFACE_HIGH, 12, Ui.LINE));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 44));
        clp.topMargin = Ui.dp(context, 18);
        btnClose.setLayoutParams(clp);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        root.addView(btnClose);

        scroll.addView(root);
        dialog.setContentView(scroll);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (context.getResources().getDisplayMetrics().widthPixels * 0.90),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        dialog.show();
    }

    private static void addSpecRow(Context context, LinearLayout parent, String key, String val) {
        LinearLayout row = Ui.row(context);
        row.setPadding(0, Ui.dp(context, 3), 0, Ui.dp(context, 3));

        TextView tvK = Ui.text(context, key, 11, Ui.MUTED);
        tvK.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));

        TextView tvV = Ui.text(context, val, 11, Ui.TEXT);
        tvV.setTypeface(Typeface.MONOSPACE);
        tvV.setGravity(Gravity.END);
        tvV.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.8f));

        row.addView(tvK);
        row.addView(tvV);
        parent.addView(row);
    }
}
