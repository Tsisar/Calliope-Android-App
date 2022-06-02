package cc.calliope.mini.service;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import cc.calliope.mini.BuildConfig;
import cc.calliope.mini.ui.activity.NotificationActivity;
import no.nordicsemi.android.dfu.DfuBaseService;

public class DfuService extends DfuBaseService {
    @Override
    protected Class<? extends Activity> getNotificationTarget() {
        return NotificationActivity.class;
    }
}
