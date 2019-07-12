package com.example.arcoreexample;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.widget.EditText;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.google.common.base.Preconditions;

/** A DialogFragment for the Resolve Dialog Box. */
public class ResolveDialogFragment extends DialogFragment {

    interface OkListener {
        /**
         * This method is called by the dialog box when its OK button is pressed.
         *
         * @param dialogValue the long value from the dialog box
         */
        void onOkPressed(Long dialogValue);
    }

    private EditText roomCodeField;
    private OkListener okListener;

    public void setOkListener(OkListener okListener) {
        this.okListener = okListener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        FragmentActivity activity =
                Preconditions.checkNotNull(getActivity(), "The activity cannot be null.");
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        // Passing null as the root is fine, because the view is for a dialog.
        View dialogView = activity.getLayoutInflater().inflate(R.layout.resolve_dialog, null);
        roomCodeField = dialogView.findViewById(R.id.room_code_input);
        builder
                .setView(dialogView)
                .setTitle(R.string.resolve_dialog_title)
                .setPositiveButton(
                        R.string.resolve_dialog_ok,
                        (dialog, which) -> {
                            Editable roomCodeText = roomCodeField.getText();
                            if (okListener != null && roomCodeText != null && roomCodeText.length() > 0) {
                                Long longVal = Long.valueOf(roomCodeText.toString());
                                okListener.onOkPressed(longVal);
                            }
                        })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {});
        return builder.create();
    }
}