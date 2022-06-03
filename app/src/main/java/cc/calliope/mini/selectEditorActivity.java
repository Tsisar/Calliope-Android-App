package cc.calliope.mini;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.appcompat.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import cc.calliope.mini.adapter.ExtendedBluetoothDevice;

public class selectEditorActivity extends AppCompatActivity {

    private ExtendedBluetoothDevice device = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_editor);

        final Intent intent = getIntent();
        device = intent.getParcelableExtra("cc.calliope.mini.EXTRA_DEVICE");
        if (device != null) {
            final String deviceName = device.getName();
            final String deviceAddress = device.getAddress();
            Log.i("DEVICE", deviceName);
            final TextView deviceInfo = findViewById(R.id.deviceInfo);
        }

//        ConstraintLayout buttonMiniEdit = findViewById(R.id.buttonMiniEdit);
//        buttonMiniEdit.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                openEditor("https://miniedit.calliope.cc/", "MINIEDIT");
//            }
//        });

        ConstraintLayout buttonMakeCode = findViewById(R.id.buttonMakeCode);
        buttonMakeCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openEditor("https://makecode.calliope.cc", "MAKECODE");
            }
        });

        ConstraintLayout buttonNepo = findViewById(R.id.buttonNepo);
        buttonNepo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
//                String nepoLink = prefs.getString("NEPO_URL", "https://lab.open-roberta.org/#loadSystem&&calliope2017");
                openEditor("https://lab.open-roberta.org/#loadSystem&&calliope2017", "NEPO");
            }
        });

        ConstraintLayout buttonCustomEditor = findViewById(R.id.buttonCustomEditor);
        buttonCustomEditor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                String customLink = prefs.getString("CUSTOM_URL", "https://calliope.cc/programmieren/editoren");
                openEditor(customLink, "CUSTOM");
            }
        });




        ImageView buttonEdit = findViewById(R.id.buttonEdit);
        buttonEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
                builder.setTitle(R.string.set_nepo_link_title);

// Set up the input
                final EditText input = new EditText(view.getContext());

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                String customLink = prefs.getString("CUSTOM_URL", "https://calliope.cc/programmieren/editoren");
                input.setText(customLink);
// Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
                builder.setView(input);

// Set up the buttons
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //m_Text = input.getText().toString();
                        SharedPreferences.Editor prefEditor = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit();
                        prefEditor.putString("CUSTOM_URL", input.getText().toString());
                        prefEditor.apply();
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.show();
            }
        });

    }

    protected void openEditor(String editor, String editorName) {
        final Intent intent = new Intent(selectEditorActivity.this, editorAcitvity.class);
        if(device != null) intent.putExtra("cc.calliope.mini.EXTRA_DEVICE", device);
            intent.putExtra("TARGET_NAME", editorName);
            intent.putExtra("TARGET_URL", editor);
            startActivity(intent);
    }


}
