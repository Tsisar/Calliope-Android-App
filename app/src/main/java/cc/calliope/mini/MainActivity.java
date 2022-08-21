package cc.calliope.mini;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;
import cc.calliope.mini.adapter.ExtendedBluetoothDevice;
import cc.calliope.mini.dialog.PatternDialogFragment;
import cc.calliope.mini.utils.Utils;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        final Intent intent = getIntent();
        final ExtendedBluetoothDevice device = intent.getParcelableExtra("cc.calliope.mini.EXTRA_DEVICE");
        if (device != null) {

            final String deviceName = device.getName();

            final TextView deviceInfo = findViewById(R.id.deviceInfo);
            deviceInfo.setText(deviceName + " " + R.string.text_connected_with);

            final ImageView pattern1 = findViewById(R.id.pattern1);
            final ImageView pattern2 = findViewById(R.id.pattern2);
            final ImageView pattern3 = findViewById(R.id.pattern3);
            final ImageView pattern4 = findViewById(R.id.pattern4);
            final ImageView pattern5 = findViewById(R.id.pattern5);
            pattern1.setImageResource(device.getDevicePattern(0));
            pattern2.setImageResource(device.getDevicePattern(1));
            pattern3.setImageResource(device.getDevicePattern(2));
            pattern4.setImageResource(device.getDevicePattern(3));
            pattern5.setImageResource(device.getDevicePattern(4));
        }

        ImageView button_info = findViewById(R.id.button_info);
        button_info.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Intent intent = new Intent(MainActivity.this, InfoActivity.class);
                startActivity(intent);
            }
        });

        ConstraintLayout button_scanner = findViewById(R.id.button_scanner);
        button_scanner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                final Intent intent = new Intent(MainActivity.this, ScannerActivity.class);
//                startActivity(intent);
                showPatternDialog();
            }
        });

        ConstraintLayout buttonDemoScript = findViewById(R.id.buttonDemoScript);
        buttonDemoScript.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Intent intent = new Intent(MainActivity.this, editorAcitvity.class);
                if (device != null) intent.putExtra("cc.calliope.mini.EXTRA_DEVICE", device);
                intent.putExtra("TARGET_NAME", "BIBLIOTHEK");
                intent.putExtra("TARGET_URL", getString(R.string.start_programm_url));
                startActivity(intent);
            }
        });

        ConstraintLayout button_editor = findViewById(R.id.button_editor);
        button_editor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Intent intent = new Intent(MainActivity.this, selectEditorActivity.class);
                if (device != null) intent.putExtra("cc.calliope.mini.EXTRA_DEVICE", device);
                startActivity(intent);
            }
        });

        ConstraintLayout button_code = findViewById(R.id.button_code);
        button_code.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Intent intent = new Intent(MainActivity.this, myCodeActivity.class);
                if (device != null) intent.putExtra("cc.calliope.mini.EXTRA_DEVICE", device);
                startActivity(intent);
            }
        });


    }

    public void foo() {
        //       final Intent intent = new Intent(this, ScannerActivity.class);
//        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        //      startActivity(intent);
    }

    private void showPatternDialog() {
//        Bundle bundle = new Bundle();
//        bundle.putString("TEXT","TEST TEXT");

        FragmentManager parentFragmentManager = getSupportFragmentManager();
//        parentFragmentManager.setFragmentResultListener("pattern", getViewLifecycleOwner(), this);
        PatternDialogFragment patternDialogFragment = PatternDialogFragment.newInstance("Some Pattern");
//        patternDialogFragment.setArguments(bundle);

        patternDialogFragment.show(parentFragmentManager, "fragment_pattern");

    }

    //TODO We must check permission when application start
    private void checkPermission() {

    }
}
