package dev.sola.hud;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Axiom Design System for Sola HUD.
 * Material 3 Obsidian dark palette, clean typography, zero emojis.
 */
public final class HudUi {

    public static final int BG = 0xFF111318;
    public static final int SURFACE = 0xFF1B1D22;
    public static final int SURFACE_HIGH = 0xFF25272E;
    public static final int SURFACE_TRANSLUCENT = 0xE61B1D22; // 90% opacity for HUD drawer
    public static final int CAPSULE_BG = 0xEE111318;          // 93% opacity for collapsed pill
    public static final int LINE = 0xFF373942;

    public static final int PRIMARY = 0xFFA8C7FA;
    public static final int PRIMARY_CONTAINER = 0xFF1F3354;
    public static final int POSITIVE = 0xFF7CDA9A;
    public static final int WARNING = 0xFFF6C177;
    public static final int ERROR = 0xFFF28B82;

    public static final int TEXT = 0xFFE2E2E6;
    public static final int MUTED = 0xFF90929C;
    public static final int DIM = 0xFF73757F;

    public static final String DASH = "—";

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

    public static GradientDrawable capsuleShape(Context c, int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(c, 24));
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
        TextView v = text(c, s.toUpperCase(), 10, MUTED);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setLetterSpacing(0.08f);
        v.setPadding(0, dp(c, 4), 0, dp(c, 6));
        return v;
    }

    public static TextView pin(Context c, String text, int color) {
        TextView v = new TextView(c);
        pin(v, text, color);
        return v;
    }

    public static void pin(TextView v, String text, int color) {
        v.setText(text);
        v.setTextColor(color);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setIncludeFontPadding(false);
        v.setGravity(Gravity.CENTER);
        v.setBackground(round(v.getContext(), alpha(color, 0x22), 8, alpha(color, 0x44)));
        int h = dp(v.getContext(), 8);
        int vPad = dp(v.getContext(), 3);
        v.setPadding(h, vPad, h, vPad);
    }

    public static Button button(Context c, String label, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(TEXT);
        b.setBackground(round(c, SURFACE_HIGH, 10, LINE));
        b.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
        b.setMinHeight(dp(c, 36));
        b.setTransformationMethod(null);
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    public static Button accentButton(Context c, String label, View.OnClickListener l) {
        Button b = button(c, label, l);
        b.setTextColor(0xFF002F65);
        b.setBackground(round(c, PRIMARY, 10, 0));
        return b;
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
}
