package org.thunderatz.tiago.thundertrekking.views;

import android.content.Context;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.TextView;

public class ScrollingTextView extends TextView {
    public ScrollingTextView(Context context) {
        super(context);
        setupView();
    }

    public ScrollingTextView(Context context, AttributeSet attributes) {
        super(context, attributes);
        setupView();
    }

    public ScrollingTextView(Context context, AttributeSet attributes, int defStyleAttr) {
        super(context, attributes, defStyleAttr);
        setupView();
    }

    private void setupView() {
        super.setGravity(Gravity.BOTTOM);
        super.setMovementMethod(new ScrollingMovementMethod());
    }
}
