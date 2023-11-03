package com.example.android.camerax.video.fragments;

import androidx.annotation.NonNull;
import androidx.navigation.ActionOnlyNavDirections;
import androidx.navigation.NavDirections;
import com.example.android.camerax.video.R;

public class CaptureFragmentDirections {
  private CaptureFragmentDirections() {
  }

  @NonNull
  public static NavDirections actionCaptureToPermissions() {
    return new ActionOnlyNavDirections(R.id.action_capture_to_permissions);
  }
}
