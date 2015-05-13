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

package com.google.samples.apps.friendlyping.fragment;

import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.samples.apps.friendlyping.AnalyticsHelper;
import com.google.samples.apps.friendlyping.R;

import static com.google.samples.apps.friendlyping.model.TrackingEvent.USER_LOGIN;

/**
 * Fragment taking care of signing in users.
 */
public class SignInFragment extends Fragment {

    private static final String TAG = "SignInFragment";
    private View.OnClickListener mOnClickListener;
    private GoogleApiClient mGoogleApiClient;

    public SignInFragment() {
        mOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mGoogleApiClient == null) {
                    Log.w(TAG, "GoogleApiClient is not set. Make sure to set it before trying "
                            + "to connect to it.");
                } else {
                    mGoogleApiClient.connect();
                    AnalyticsHelper.send(getActivity(), USER_LOGIN);
                }
            }
        };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final FragmentActivity activity = getActivity();

        // Status bar colors can only be set on API 21+, so skip for lower API levels.
        if (null != activity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.getWindow().setStatusBarColor(
                    getResources().getColor(R.color.sign_in_status));
        }
        return inflater.inflate(R.layout.fragment_sign_in, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        view.findViewById(R.id.sign_in_button).setOnClickListener(mOnClickListener);
        super.onViewCreated(view, savedInstanceState);
    }

    /**
     * Set the {@link GoogleApiClient} for this fragment to allow operations with it.
     */
    public void setGoogleApiClient(GoogleApiClient googleApiClient) {
        mGoogleApiClient = googleApiClient;
    }
}
