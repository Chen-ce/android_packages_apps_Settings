package com.android.settings.development;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class IdentityDataPreference extends Preference {

    private static final String TAG = "IdentityDataPref";
    private ActivityResultLauncher<Intent> mFilePickerLauncher;

    public IdentityDataPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.pref_with_delete);
    }

    public void setFilePickerLauncher(ActivityResultLauncher<Intent> launcher) {
        this.mFilePickerLauncher = launcher;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        TextView title = (TextView) holder.findViewById(R.id.title);
        TextView summary = (TextView) holder.findViewById(R.id.summary);
        ImageButton deleteButton = (ImageButton) holder.findViewById(R.id.delete_button);

        title.setText(getTitle());
        summary.setText(getSummary());

        holder.itemView.setOnClickListener(v -> {
            if (mFilePickerLauncher != null) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("application/json");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                mFilePickerLauncher.launch(intent);
            }
        });

        deleteButton.setOnClickListener(v -> {
            Settings.Secure.putString(getContext().getContentResolver(),
                    Settings.Secure.IDENTITY_CONFIG, null);
            Toast.makeText(getContext(), "Identity config cleared", Toast.LENGTH_SHORT).show();
            callChangeListener(null);
        });
    }

    public void handleFileSelected(Uri uri) {
        if (uri == null ||
                (!uri.toString().endsWith(".json") &&
                        !"application/json".equals(getContext().getContentResolver().getType(uri)))) {
            Toast.makeText(getContext(), "Invalid file selected", Toast.LENGTH_SHORT).show();
            return;
        }

        try (InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line).append('\n');
            }

            String json = jsonContent.toString();
            validateIdentityJson(json);

            Settings.Secure.putString(getContext().getContentResolver(),
                    Settings.Secure.IDENTITY_CONFIG, json);
            Toast.makeText(getContext(), "Identity JSON loaded", Toast.LENGTH_SHORT).show();
            callChangeListener(json);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read JSON file", e);
            Toast.makeText(getContext(), "Failed to read JSON", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Log.e(TAG, "Invalid identity JSON", e);
            Toast.makeText(getContext(), "Invalid identity JSON", Toast.LENGTH_SHORT).show();
        }
    }

    private static void validateIdentityJson(String json) throws JSONException {
        JSONObject obj = new JSONObject(json);

        if (!obj.has("enabled") || !obj.has("global") || !obj.has("serial")) {
            throw new JSONException("Missing required top-level fields");
        }

        JSONObject global = obj.getJSONObject("global");
        checkMode(global.optString("mode", ""));

        JSONObject serial = obj.getJSONObject("serial");
        if (serial.optBoolean("enabled", false)) {
            String serialValue = serial.optString("value", "");
            if (serialValue.isEmpty()) {
                throw new JSONException("serial.value is required when serial.enabled=true");
            }
        }
        checkMode(serial.optString("mode", ""));

        if (obj.has("imei")) {
            JSONObject imei = obj.getJSONObject("imei");
            checkMode(imei.optString("mode", ""));
        }

        if (obj.has("imei2")) {
            JSONObject imei2 = obj.getJSONObject("imei2");
            checkMode(imei2.optString("mode", ""));
        }
    }

    private static void checkMode(String mode) throws JSONException {
        if (!"all".equals(mode) && !"exclude_system".equals(mode) && !"packages".equals(mode)) {
            throw new JSONException("Unsupported mode: " + mode);
        }
    }
}
