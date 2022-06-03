/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package cc.calliope.mini.adapter;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import java.util.List;
import cc.calliope.mini.ScannerActivity;
import cc.calliope.mini.R;
import cc.calliope.mini.databinding.DeviceItemBinding;
import cc.calliope.mini.viewmodels.ScannerLiveData;

public class DevicesAdapter extends RecyclerView.Adapter<DevicesAdapter.ViewHolder> {
    private final List<ExtendedBluetoothDevice> mDevices;
    private OnItemClickListener mOnItemClickListener;

    @FunctionalInterface
    public interface OnItemClickListener {
        void onItemClick(final ExtendedBluetoothDevice device);
    }

    public void setOnItemClickListener(final Context context) {
        mOnItemClickListener = (OnItemClickListener) context;
    }

    public DevicesAdapter(final ScannerActivity activity, final ScannerLiveData scannerLiveData) {
        mDevices = scannerLiveData.getDevices();
        scannerLiveData.observe(activity, devices -> {
            final Integer i = devices.getUpdatedDeviceIndex();
            if (i != null)
                notifyItemChanged(i);
            else
                notifyDataSetChanged();
        });
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(DeviceItemBinding.inflate(LayoutInflater.from(parent.getContext()),
                parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
        final ExtendedBluetoothDevice device = mDevices.get(position);
        final String deviceName = device.getName();
        final int rssiPercent = (int) (100.0f * (127.0f + device.getRssi()) / (127.0f + 20.0f));

        if (!TextUtils.isEmpty(deviceName))
            holder.itemBinding.deviceName.setText(deviceName);
        else
            holder.itemBinding.deviceName.setText(R.string.unknown_device);
        holder.itemBinding.deviceAddress.setText(device.getAddress());
        holder.itemBinding.rssi.setImageLevel(rssiPercent);
        holder.itemBinding.include3.pattern1.setImageResource(device.getDevicePattern(0));
        holder.itemBinding.include3.pattern2.setImageResource(device.getDevicePattern(1));
        holder.itemBinding.include3.pattern3.setImageResource(device.getDevicePattern(2));
        holder.itemBinding.include3.pattern4.setImageResource(device.getDevicePattern(3));
        holder.itemBinding.include3.pattern5.setImageResource(device.getDevicePattern(4));
    }

    @Override
    public long getItemId(final int position) {
        return position;
    }

    @Override
    public int getItemCount() {
        return mDevices.size();
    }

    public boolean isEmpty() {
        return getItemCount() == 0;
    }

    final class ViewHolder extends RecyclerView.ViewHolder {
        DeviceItemBinding itemBinding;

        private ViewHolder(DeviceItemBinding itemBinding) {
            super(itemBinding.getRoot());
            this.itemBinding = itemBinding;

            itemBinding.deviceContainer.setOnClickListener(view -> {
                if (mOnItemClickListener != null) {
                    mOnItemClickListener.onItemClick(mDevices.get(getBindingAdapterPosition()));
                }
            });
        }
    }
}
