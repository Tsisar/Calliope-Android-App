package cc.calliope.mini.dialog;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import cc.calliope.mini.R;
import cc.calliope.mini.adapter.ExtendedBluetoothDevice;
import cc.calliope.mini.databinding.FragmentPatternBinding;
import cc.calliope.mini.utils.Utils;
import cc.calliope.mini.viewmodels.ScannerLiveData;
import cc.calliope.mini.viewmodels.ScannerViewModel;

public class PatternDialogFragment extends DialogFragment {

    private FragmentPatternBinding binding;
    private final List<String> patternList = Arrays.asList("0", "0", "0", "0", "0");

    private ScannerViewModel mScannerViewModel;
    private final LinkedHashSet<ExtendedBluetoothDevice> devices = new LinkedHashSet<>();

    public PatternDialogFragment() {
        // Empty constructor is required for DialogFragment
        // Make sure not to add arguments to the constructor
        // Use `newInstance` instead as shown below
    }

    public static PatternDialogFragment newInstance(String pattern) {
        PatternDialogFragment patternDialogFragment = new PatternDialogFragment();

        Bundle args = new Bundle();
        args.putString("pattern", pattern);
        patternDialogFragment.setArguments(args);

        return patternDialogFragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentPatternBinding.inflate(inflater, container, false);
        // Create view model containing utility methods for scanning
        mScannerViewModel = new ViewModelProvider(this).get(ScannerViewModel.class);
        mScannerViewModel.getScannerState().observe(this, this::startScan);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle bundle = getArguments();
        if (bundle != null) {
            Log.e("Pattern", bundle.getString("pattern", "00000"));
        }
        //TODO we need save & restore it
//        binding.patternA.setPattern(2);
//        binding.patternB.setPattern(5);
//        binding.patternC.setPattern(3);
//        binding.patternD.setPattern(1);
//        binding.patternE.setPattern(2);

        binding.patternA.setOnRatingBarChangeListener((ratingBar, v, b) -> onPatternChange(0, v));
        binding.patternB.setOnRatingBarChangeListener((ratingBar, v, b) -> onPatternChange(1, v));
        binding.patternC.setOnRatingBarChangeListener((ratingBar, v, b) -> onPatternChange(2, v));
        binding.patternD.setOnRatingBarChangeListener((ratingBar, v, b) -> onPatternChange(3, v));
        binding.patternE.setOnRatingBarChangeListener((ratingBar, v, b) -> onPatternChange(4, v));

        binding.buttonOk.setOnClickListener(view1 -> sendBackResult());
    }

    // Call this method to send the data back to the parent fragment
    public void sendBackResult() {
        //TODO Use ViewModel
        ExtendedBluetoothDevice testDevice = getDeviceByPattern(patternList);
        if (testDevice != null) {
            Log.e("DIALOG", "Test device: " + testDevice.getPattern() + " " + testDevice.getAddress());

            //TODO it's not correct
            final Intent controlMiniIntent = new Intent(getActivity(), cc.calliope.mini.MainActivity.class);
            controlMiniIntent.putExtra("cc.calliope.mini.EXTRA_DEVICE", testDevice);
            startActivity(controlMiniIntent);
        }

//        Bundle bundle = new Bundle();
//        bundle.putStringArrayList("pattern_list_key", patternList);
//        getParentFragmentManager().setFragmentResult("res_key", bundle);
        dismiss();
    }

    private void onPatternChange(int index, float newPatten) {
        patternList.set(index, Pattern.forCode(newPatten).toString());
        if(getDeviceByPattern(patternList) != null){
            binding.buttonOk.setBackgroundColor(getResources().getColor(R.color.green));
        }else {
            binding.buttonOk.setBackgroundColor(getResources().getColor(R.color.lightgrey));
        }
    }

    enum Pattern {
        ZU(1f),
        VO(2f),
        GI(3f),
        PE(4f),
        TA(5f);

        private final float code;

        private Pattern(final float code) {
            this.code = code;
        }

        private static final Map<Float, Pattern> BY_CODE_MAP = new LinkedHashMap<>();

        static {
            for (Pattern pattern : Pattern.values()) {
                BY_CODE_MAP.put(pattern.code, pattern);
            }
        }

        public static Pattern forCode(float code) {
            return BY_CODE_MAP.get(code);
        }
    }

    private ExtendedBluetoothDevice getDeviceByPattern(List<String> pattern) {
        Log.e("DEVICE: ", "getDeviceByPattern: " + pattern);
        for (ExtendedBluetoothDevice device : devices) {
            int coincide = 0;
            for (int i = 0; i < 5; i++) {
                char character = device.getPattern().charAt(i);
                if (pattern.get(i) != null && pattern.get(i).contains(String.valueOf(character))) {
                    coincide++;
                }
            }
            if (coincide == 5) {
                return device;
            }
        }
        return null;
    }

    private void startScan(final ScannerLiveData state) {
        Log.v("Scan Permission: ", Utils.isBluetoothScanPermissionsGranted(getActivity()) + "");
        Log.v("Location Permission: ", Utils.isLocationPermissionsGranted(getActivity()) + "");
        Log.v("Version", Build.VERSION.SDK_INT + "");

        // First, check the Location permission. This is required on Marshmallow onwards in order to scan for Bluetooth LE devices.
        if (Utils.isLocationPermissionsGranted(getActivity())
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || Utils.isBluetoothScanPermissionsGranted(getActivity()))
        ) {
            Log.v("DIALOG: ", "All ok, permission granted");

            // Bluetooth must be enabled
            if (state.isBluetoothEnabled()) {
                // We are now OK to start scanning
                Log.v("DIALOG: ", "Start scanning");
                mScannerViewModel.startScan();
                devices.addAll(state.getDevices());

                for (ExtendedBluetoothDevice device : devices) {
                    Log.i("DIALOG: ", "Device: " + device.getName() + " " + device.getAddress());
                }

                if (state.isEmpty()) {
                    Log.e("DIALOG: ", "Devices list is entry");
                    if (!Utils.isLocationRequired(getActivity()) || Utils.isLocationEnabled(getActivity())) {
                        Log.v("DIALOG: ", "Location enabled");
                    } else {
                        Log.e("DIALOG: ", "Location disabled");
                    }
                } else {
                    Log.v("DIALOG: ", "Scanning finished");
                }
            } else {
                Log.e("DIALOG: ", "Bluetooth disabled");
            }
        } else {
            Log.e("DIALOG: ", "No location or bt permission");
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        mScannerViewModel.stopScan();
        Log.e("DEVICES", devices.toString());
    }
}