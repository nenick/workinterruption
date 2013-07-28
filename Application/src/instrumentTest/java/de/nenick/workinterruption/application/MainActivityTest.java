package de.nenick.workinterruption.application;

import android.test.ActivityInstrumentationTestCase2;
import android.test.ActivityUnitTestCase;

import de.nenick.workinterruption.R;
import de.nenick.workinterruption.application.MainActivity;

public class MainActivityTest extends ActivityInstrumentationTestCase2<MainActivity> {

    private MainActivity activity;

    public MainActivityTest() {
        super(MainActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        activity = getActivity();
    }

    public void testLayout() {

        assertNotNull(activity.findViewById(R.id.work_toggle));
        assertNotNull(activity.findViewById(R.id.break_toggle));
        assertNotNull(activity.findViewById(R.id.meeting_toggle));
        assertNotNull(activity.findViewById(R.id.interrupt_toggle));
    }
}
