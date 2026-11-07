package dev.sola;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Axiom Design System for Sola.
 * Authentic Material 3 Obsidian dark theme, 20dp card containers, 12dp sub-cards,
 * clean typography with uppercase tracked labels, and zero emojis.
 */
public final class Ui {

    // Axiom Exact Color Tokens (Material 3 Obsidian Elevation Hierarchy)
    public static final int BG = 0xFF111318;                 // Base Canvas
    public static final int SURFACE = 0xFF1C1E25;            // Surface Container (Outer Card, solid)
    public static final int SURFACE_HIGH = 0xFF272A34;       // Surface Container High (Inner Sub-card, solid)
    public static final int LINE = 0xFF373942;               // Outline / Subtle Dividers

    public static final int PRIMARY = 0xFFA8C7FA;            // Cyber Blue / Accent
    public static final int PRIMARY_CONTAINER = 0xFF1F3354;
    public static final int POSITIVE = 0xFF7CDA9A;           // Soft Mint Green
    public static final int WARNING = 0xFFF6C177;            // Warm Amber
    public static final int ERROR = 0xFFF28B82;              // Coral Red

    public static final int TEXT = 0xFFE2E2E6;               // On Surface
    public static final int MUTED = 0xFF90929C;              // On Surface Variant
    public static final int DIM = 0xFF73757F;                // Muted Outline

    // Legacy aliases for REA tab compatibility
    public static final int CARD = SURFACE;
    public static final int CARD_ELEV = SURFACE_HIGH;
    public static final int ACCENT = PRIMARY;
    public static final int ACCENT_TEXT = 0xFF002F65;
    public static final int INFO = PRIMARY;
    public static final int WARN = WARNING;
    public static final int BAD = ERROR;
    public static final String DASH = "—";

    public static String fmt(double v, int decimals) {
        if (Double.isNaN(v)) return DASH;
        return String.format(java.util.Locale.US, "%." + decimals + "f", v);
    }

    public static String fmt(Double v, int decimals) {
        if (v == null || Double.isNaN(v)) return DASH;
        return String.format(java.util.Locale.US, "%." + decimals + "f", v);
    }

    public static String pct(double v) {
        if (Double.isNaN(v)) return DASH;
        return String.format(java.util.Locale.US, "%.0f%%", v);
    }

    public static TextView pill(Context c, String text, int color) {
        return pin(c, text, color);
    }

    public static void pill(TextView v, String text, int color) {
        v.setText(text);
        v.setTextColor(color);
        v.setIncludeFontPadding(false);
        v.setGravity(Gravity.CENTER);
        v.setBackground(round(v.getContext(), alpha(color, 0x22), 12, 0));
    }

    public static View space(Context c, int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, dp)));
        return v;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1)));
        return v;
    }

    public static int dp(Context c, int value) {
        return (int) (value * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static int alpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static GradientDrawable round(Context c, int fill, int radiusDp, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) {
            d.setStroke(dp(c, 1), stroke);
        }
        return d;
    }

    public static TextView text(Context c, CharSequence s, float sizeSp, int color) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(sizeSp);
        v.setTextColor(color);
        return v;
    }

    public static TextView heading(Context c, String s) {
        TextView v = text(c, s.toUpperCase(), 11, MUTED);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setLetterSpacing(0.08f);
        v.setPadding(0, dp(c, 4), 0, dp(c, 8));
        return v;
    }

    public static TextView note(Context c, String s) {
        return text(c, s, 12, MUTED);
    }

    public static Button button(Context c, String label, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(TEXT);
        b.setBackground(round(c, SURFACE_HIGH, 12, LINE));
        b.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
        b.setMinHeight(dp(c, 40));
        b.setTransformationMethod(null);
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    public static Button accent(Context c, String label, View.OnClickListener l) {
        Button b = button(c, label, l);
        b.setTextColor(Color.BLACK);
        b.setBackground(round(c, PRIMARY, 12, 0));
        return b;
    }

    public static Button danger(Context c, String label, View.OnClickListener l) {
        Button b = button(c, label, l);
        b.setTextColor(ERROR);
        b.setBackground(round(c, alpha(ERROR, 0x1A), 12, alpha(ERROR, 0x44)));
        return b;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** Axiom 20dp Solid Outer Card Container (Material 3 surfaceContainer, zero outline) */
    public static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        l.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        l.setBackground(round(c, SURFACE, 20, 0));
        return l;
    }

    public static LinearLayout card(Context c, int padDp) {
        LinearLayout l = column(c);
        l.setPadding(dp(c, padDp), dp(c, padDp), dp(c, padDp), dp(c, padDp));
        l.setBackground(round(c, SURFACE, 20, 0));
        return l;
    }

    public static LinearLayout elevatedCard(Context c, int padDp, int strokeColor) {
        LinearLayout l = column(c);
        l.setPadding(dp(c, padDp), dp(c, padDp), dp(c, padDp), dp(c, padDp));
        l.setBackground(round(c, SURFACE_HIGH, 20, strokeColor));
        return l;
    }

    /** Axiom Card Header with 32dp Circular Icon Box and Bold Title */
    public static LinearLayout cardHeader(Context c, int iconResId, String title) {
        LinearLayout h = row(c);
        h.setPadding(0, 0, 0, dp(c, 14));

        if (iconResId != 0) {
            LinearLayout iconBox = column(c);
            iconBox.setGravity(Gravity.CENTER);
            iconBox.setBackground(round(c, PRIMARY_CONTAINER, 16, 0));
            ImageView icon = new ImageView(c);
            icon.setImageResource(iconResId);
            icon.setColorFilter(PRIMARY);
            iconBox.addView(icon, new LinearLayout.LayoutParams(dp(c, 18), dp(c, 18)));

            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(c, 32), dp(c, 32));
            ip.rightMargin = dp(c, 10);
            h.addView(iconBox, ip);
        }

        TextView t = text(c, title, 16, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        h.addView(t);
        return h;
    }

    /** Axiom 12dp Rounded Solid Sub-Card (InfoSubCard - surfaceContainerHigh, zero outline) */
    public static LinearLayout subCard(Context c, String label, String value, int valColor) {
        LinearLayout box = column(c);
        box.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        box.setBackground(round(c, SURFACE_HIGH, 12, 0));

        TextView lbl = text(c, label.toUpperCase(), 9, MUTED);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        lbl.setLetterSpacing(0.06f);

        TextView val = text(c, value, 15, valColor != 0 ? valColor : TEXT);
        val.setTypeface(Typeface.DEFAULT_BOLD);
        val.setPadding(0, dp(c, 3), 0, 0);

        box.addView(lbl);
        box.addView(val);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        box.setLayoutParams(lp);
        return box;
    }

    /** Pill badge (e.g. LOCKED, ARMED, OPTIMAL) */
    public static TextView badge(Context c, String label, int color) {
        TextView tv = text(c, label.toUpperCase(), 10, color);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setLetterSpacing(0.04f);
        tv.setIncludeFontPadding(false);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        tv.setBackground(round(c, alpha(color, 0x22), 12, 0));
        return tv;
    }

    /** Material 3 Status Pin / Chip (Axiom style) */
    public static TextView pin(Context c, String text, int color) {
        TextView tv = text(c, text.toUpperCase(), 10, color);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setLetterSpacing(0.06f);
        tv.setIncludeFontPadding(false);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        tv.setBackground(round(c, alpha(color, 0x22), 12, 0));
        return tv;
    }

    /** Material 3 Severity Pin */
    public static TextView severityPin(Context c, String severity) {
        int color;
        if ("CRITICAL".equalsIgnoreCase(severity)) {
            color = ERROR;
        } else if ("WARNING".equalsIgnoreCase(severity) || "WARN".equalsIgnoreCase(severity)) {
            color = WARNING;
        } else {
            color = PRIMARY;
        }
        return pin(c, severity, color);
    }

    /** Material 3 Component Pin (e.g. BMS, MCU, MOTOR, VCU) */
    public static TextView componentPin(Context c, String comp) {
        TextView tv = text(c, comp.toUpperCase(), 10, TEXT);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setLetterSpacing(0.06f);
        tv.setIncludeFontPadding(false);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(c, 8), dp(c, 3), dp(c, 8), dp(c, 3));
        tv.setBackground(round(c, SURFACE_HIGH, 8, 0));
        return tv;
    }

    /** Interface for query changes in Material 3 Search Bar */
    public interface SearchCallback {
        void onQuery(String query);
    }

    /**
     * Material 3 Search Bar with leading search vector icon,
     * smooth rounded container, and trailing clear button.
     */
    public static LinearLayout searchBar(Context c, String hint, SearchCallback callback) {
        LinearLayout container = row(c);
        container.setPadding(dp(c, 14), dp(c, 4), dp(c, 12), dp(c, 4));
        container.setBackground(round(c, SURFACE_HIGH, 16, 0));
        container.setMinimumHeight(dp(c, 48));

        ImageView searchIcon = new ImageView(c);
        searchIcon.setImageResource(R.drawable.ic_search);
        searchIcon.setColorFilter(MUTED);
        LinearLayout.LayoutParams sip = new LinearLayout.LayoutParams(dp(c, 20), dp(c, 20));
        sip.rightMargin = dp(c, 10);
        container.addView(searchIcon, sip);

        android.widget.EditText input = new android.widget.EditText(c);
        input.setHint(hint);
        input.setHintTextColor(MUTED);
        input.setTextColor(TEXT);
        input.setTextSize(13);
        input.setSingleLine(true);
        input.setBackground(null);
        input.setPadding(0, dp(c, 8), 0, dp(c, 8));
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        container.addView(input, ip);

        ImageView clearIcon = new ImageView(c);
        clearIcon.setImageResource(R.drawable.ic_close);
        clearIcon.setColorFilter(MUTED);
        clearIcon.setVisibility(View.GONE);
        LinearLayout.LayoutParams cip = new LinearLayout.LayoutParams(dp(c, 20), dp(c, 20));
        container.addView(clearIcon, cip);

        clearIcon.setOnClickListener(v -> {
            input.setText("");
            if (callback != null) callback.onQuery("");
        });

        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s != null ? s.toString() : "";
                clearIcon.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                if (callback != null) callback.onQuery(q);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        return container;
    }

    public static ScrollView page(Context c) {
        ScrollView s = new ScrollView(c);
        s.setFillViewport(true);
        s.setBackgroundColor(BG);
        LinearLayout body = column(c);
        body.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 56));
        s.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    public static LinearLayout body(ScrollView page) {
        return (LinearLayout) page.getChildAt(0);
    }

    public static void set(TextView v, CharSequence s) {
        if (!s.equals(v.getText())) {
            v.setText(s);
        }
    }

    /** Key-value row for tables */
    public static final class Kv extends LinearLayout {
        public final TextView value;

        public Kv(Context c, String label) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(0, dp(c, 8), 0, dp(c, 8));
            TextView key = text(c, label, 13, MUTED);
            addView(key, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            value = text(c, DASH, 13, TEXT);
            value.setTypeface(Typeface.DEFAULT_BOLD);
            value.setGravity(Gravity.END);
            addView(value, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }

        public Kv set(String s) {
            value.setText(s);
            value.setTextColor(TEXT);
            return this;
        }

        public Kv set(String s, int color) {
            value.setText(s);
            value.setTextColor(color);
            return this;
        }
    }

    /** Stat tile */
    public static final class Stat extends LinearLayout {
        public final TextView value;
        public final TextView unit;

        public Stat(Context c, String label) {
            super(c);
            setOrientation(VERTICAL);
            setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
            setBackground(round(c, SURFACE_HIGH, 12, 0));

            TextView key = text(c, label.toUpperCase(), 9, MUTED);
            key.setTypeface(Typeface.DEFAULT_BOLD);
            key.setLetterSpacing(0.06f);
            addView(key);

            LinearLayout line = row(c);
            line.setPadding(0, dp(c, 2), 0, 0);
            value = text(c, DASH, 18, TEXT);
            value.setTypeface(Typeface.DEFAULT_BOLD);
            line.addView(value);

            unit = text(c, "", 11, MUTED);
            LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            up.leftMargin = dp(c, 4);
            line.addView(unit, up);
            addView(line);
        }

        public Stat set(String text) {
            Ui.set(value, text);
            return this;
        }

        public Stat set(String text, int color) {
            set(text);
            if (value.getCurrentTextColor() != color) {
                value.setTextColor(color);
            }
            return this;
        }

        public Stat unit(String text) {
            Ui.set(unit, text);
            return this;
        }
    }

    public static LinearLayout statRow(Context c, View left, View right) {
        LinearLayout r = row(c);
        r.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        r.addView(left, lp);
        if (right != null) {
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            rp.leftMargin = dp(c, 8);
            r.addView(right, rp);
        }
        return r;
    }

    public static Button pillButton(Context ctx, String text, int bgColor, int textColor, View.OnClickListener onClick) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextColor(textColor);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTransformationMethod(null);
        btn.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8));
        btn.setBackground(round(ctx, bgColor, 12, 0));
        if (onClick != null) btn.setOnClickListener(onClick);
        return btn;
    }
}
