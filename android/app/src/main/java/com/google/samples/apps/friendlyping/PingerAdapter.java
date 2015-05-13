/*
 * Copyright Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.apps.friendlyping;

import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.samples.apps.friendlyping.model.Pinger;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

/**
 * Display available {@link Pinger}s.
 */
public class PingerAdapter extends BaseAdapter {

    private final ArrayList<Pinger> mPingers;
    private final LayoutInflater mLayoutInflater;

    public PingerAdapter(Context context, ArrayList<Pinger> pingers) {
        mPingers = pingers;
        mLayoutInflater = LayoutInflater.from(context);
    }

    /**
     * Add a pinger to the list.
     */
    public void addPinger(Pinger pinger) {
        mPingers.add(pinger);
        notifyDataSetChanged();
    }

    /**
     * Move a pinger to the top of the list.
     */
    public void moveToTop(Pinger pinger) {
        mPingers.remove(pinger);
        mPingers.add(0, pinger);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mPingers.size();
    }

    @Override
    public Pinger getItem(int position) {
        return mPingers.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mPingers.get(position).hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = initializeConvertView(parent);
        }

        final Pinger pinger = getItem(position);

        Glide.with(convertView.getContext()).load(pinger.getPictureUrl()).
                into((ImageView) convertView.getTag(R.id.profile_picture));

        ((TextView) convertView.getTag(R.id.name)).setText(pinger.getName());

        return convertView;
    }

    private View initializeConvertView(ViewGroup parent) {
        View convertView;
        final View view = mLayoutInflater.inflate(R.layout.pinger_item_view, parent, false);
        convertView = view;
        convertView.setTag(R.id.name, view.findViewById(R.id.name));
        final View tmpProfilePicture = view.findViewById(R.id.profile_picture);
        // OutlineProviders are available from API 21 onwards.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tmpProfilePicture.setClipToOutline(true);
            tmpProfilePicture.setOutlineProvider(new PingerOutlineProvider());
        }
        convertView.setTag(R.id.profile_picture, tmpProfilePicture);
        return convertView;
    }

    public ArrayList<Pinger> getItems() {
        return mPingers;
    }
}
