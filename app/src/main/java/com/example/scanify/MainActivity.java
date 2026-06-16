package com.example.scanify;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private final List<Uri> selectedImages = new ArrayList<>();
    private ImageAdapter imageAdapter;
    private Uri cameraImageUri;

    private RecyclerView               recyclerView;
    private LinearLayout               emptyState;
    private LinearLayout               countBar;
    private TextView                   tvImageCount;
    private ExtendedFloatingActionButton fabConvert;

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && cameraImageUri != null) {
                    selectedImages.add(cameraImageUri);
                    imageAdapter.notifyItemInserted(selectedImages.size() - 1);
                    updateUI();
                }
            });

    // ACTION_GET_CONTENT is required for multi-select across all gallery apps
    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();

                    if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        for (int i = 0; i < count; i++) {
                            Uri uri = data.getClipData().getItemAt(i).getUri();
                            // Save permission so we can still read this image after restart
                            try {
                                getContentResolver().takePersistableUriPermission(
                                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (SecurityException ignored) {}
                            selectedImages.add(uri);
                        }
                    } else if (data.getData() != null) {
                        Uri uri = data.getData();
                        try {
                            getContentResolver().takePersistableUriPermission(
                                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException ignored) {}
                        selectedImages.add(uri);
                    }

                    imageAdapter.notifyDataSetChanged();
                    updateUI();
                }
            });


    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grants -> {
                if (grants.containsValue(false)) {
                    Toast.makeText(this, "Some permissions denied. Features may be limited.",
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        requestRequiredPermissions();
        setupRecyclerView();
        setupButtons();
        updateUI();
    }

    private void bindViews() {
        recyclerView = findViewById(R.id.recyclerView);
        emptyState   = findViewById(R.id.emptyState);
        countBar     = findViewById(R.id.countBar);
        tvImageCount = findViewById(R.id.tvImageCount);
        fabConvert   = findViewById(R.id.fabConvert);
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        imageAdapter = new ImageAdapter(this, selectedImages, position -> {
            selectedImages.remove(position);
            imageAdapter.notifyItemRemoved(position);
            imageAdapter.notifyItemRangeChanged(position, selectedImages.size());
            updateUI();
        });
        recyclerView.setAdapter(imageAdapter);
    }

    private void setupButtons() {
        LinearLayout btnCamera  = findViewById(R.id.btnCamera);
        LinearLayout btnGallery = findViewById(R.id.btnGallery);
        btnCamera.setOnClickListener(v -> openCamera());
        btnGallery.setOnClickListener(v -> openGallery());
        fabConvert.setOnClickListener(v -> openPreview());
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestRequiredPermissions();
            return;
        }
        try {
            File photoFile = createImageFile();
            cameraImageUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", photoFile);
            cameraLauncher.launch(cameraImageUri);
        } catch (IOException e) {
            Toast.makeText(this, "Could not create image file", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        galleryLauncher.launch(Intent.createChooser(intent, "Select Images"));
    }

    private void openPreview() {
        if (selectedImages.isEmpty()) {
            Toast.makeText(this, "Add at least one image first", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> uriStrings = new ArrayList<>();
        for (Uri uri : selectedImages) uriStrings.add(uri.toString());
        Intent intent = new Intent(this, PreviewActivity.class);
        intent.putStringArrayListExtra(PreviewActivity.EXTRA_IMAGE_URIS, uriStrings);
        startActivity(intent);
    }

    private void updateUI() {
        boolean hasImages = !selectedImages.isEmpty();
        emptyState.setVisibility(hasImages ? View.GONE  : View.VISIBLE);
        recyclerView.setVisibility(hasImages ? View.VISIBLE : View.GONE);
        countBar.setVisibility(hasImages ? View.VISIBLE : View.GONE);

        if (hasImages) {
            int n = selectedImages.size();
            tvImageCount.setText(n + (n == 1 ? " image selected" : " images selected"));
            fabConvert.setText("Convert " + n + (n == 1 ? " image" : " images") + " to PDF");
            fabConvert.setVisibility(View.VISIBLE);
        } else {
            fabConvert.setVisibility(View.GONE);
        }
    }

    private File createImageFile() throws IOException {
        String ts  = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File   dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile("SCAN_" + ts + "_", ".jpg", dir);
    }

    private void requestRequiredPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
        permissionLauncher.launch(perms.toArray(new String[0]));
    }
}
