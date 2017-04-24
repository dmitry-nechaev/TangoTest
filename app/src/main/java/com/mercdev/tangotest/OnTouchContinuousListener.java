package com.mercdev.tangotest;

import android.view.MotionEvent;
import android.view.View;

/**
 * Created by nechaev on 17.03.2017.
 */

public abstract class OnTouchContinuousListener implements View.OnTouchListener {

    private final int mInitialRepeatDelay;
    private final int mNormalRepeatDelay;
    private View mView;

    /**
     * Construct listener with default delays
     */
    public OnTouchContinuousListener() {
        this.mInitialRepeatDelay = 500;
        this.mNormalRepeatDelay = 200;
    }

    /**
     *
     * Construct listener with configurable delays
     *
     *
     * @param initialRepeatDelay
     *          delay to the first repeat in millis
     * @param normalRepeatDelay
     *          delay to subsequent repeats in millis
     */
    public OnTouchContinuousListener(int initialRepeatDelay, int normalRepeatDelay) {
        this.mInitialRepeatDelay = initialRepeatDelay;
        this.mNormalRepeatDelay = normalRepeatDelay;
    }

    private final Runnable repeatRunnable = new Runnable() {
        @Override
        public void run() {

            // as long the button is press we continue to repeat
            if (mView.isPressed()) {

                // Fire the onTouchRepeat event
                onTouchRepeat(mView);

                // Schedule the repeat
                mView.postDelayed(repeatRunnable, mNormalRepeatDelay);
            }
        }
    };

    /**
     * Called when a touch event is dispatched to a view. This allows listeners to
     * get a chance to respond before the target view.
     *
     * @param v
     *          The view the touch event has been dispatched to.
     * @param event
     *          The MotionEvent object containing full information about the
     *          event.
     * @return True if the listener has consumed the event, false otherwise.
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mView = v;

            // Fire the first touch straight away
            onTouchRepeat(mView);

            // Start the incrementing with the initial delay
            mView.postDelayed(repeatRunnable, mInitialRepeatDelay);
        }

        // don't return true, we don't want to disable buttons default behavior
        return false;
    }

    /**
     * Called when the target item should be changed due to continuous touch. This
     * happens at first press, and also after each repeat timeout. Releasing the
     * touch will stop the repeating.
     *
     */
    public abstract void onTouchRepeat(View view);

}
