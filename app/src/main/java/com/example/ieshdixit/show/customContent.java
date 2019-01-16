package com.example.ieshdixit.show;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

public class customContent extends AppCompatDialogFragment {

    private EditText content;
    private contentdialogListener listener;
    private String TAG = "custom content";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.custom_dialog, null);

        builder.setView(view).setNegativeButton("cancel", (dialog, which) -> {

        })
                .setPositiveButton("Go", (dialog, which) -> {

                    String customcontent = content.getText().toString();
                    Log.i(TAG, "content in dialog"+customcontent);
                    listener.applycontent(customcontent);

                });

        content= view.findViewById(R.id.custom_content);

        return builder.create();
    }

    public void onAttach(Context context){
        super.onAttach(context);

        try {
            listener = (contentdialogListener) context;
        } catch (ClassCastException e) {
             throw new ClassCastException(context.toString()+"must implement contentdialogListener");
        }
    }

    public interface contentdialogListener{
        void applycontent(String customcontent);
    }
}
