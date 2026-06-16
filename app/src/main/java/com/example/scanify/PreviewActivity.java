package com.example.scanify;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class PreviewActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URIS = "extra_image_uris";

    private List<Uri>      imageUris;
    private ImageAdapter   adapter;
    private ProgressBar    progressBar;
    private MaterialButton btnGenerate;
    private ItemTouchHelper touchHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        ArrayList<String> uriStrings = getIntent().getStringArrayListExtra(EXTRA_IMAGE_URIS);
        imageUris = new ArrayList<>();
        if (uriStrings != null) {
            for (String s : uriStrings) imageUris.add(Uri.parse(s));
        }

        progressBar = findViewById(R.id.progressBar);
        btnGenerate = findViewById(R.id.btnGeneratePdf);

        setupRecyclerView();
        btnGenerate.setOnClickListener(v -> generatePdf());
    }

    private void setupRecyclerView() {
        RecyclerView rv = findViewById(R.id.previewRecycler);
        rv.setLayoutManager(new GridLayoutManager(this, 2));

        adapter = new ImageAdapter(this, imageUris, position -> {
            if (position < 0 || position >= imageUris.size()) return;
            imageUris.remove(position);
            adapter.notifyItemRemoved(position);
            adapter.notifyItemRangeChanged(position, imageUris.size());
            if (imageUris.isEmpty())
                Toast.makeText(this, "No images left. Go back to add more.", Toast.LENGTH_SHORT).show();
        });

        rv.setAdapter(adapter);

        // Enable drag-to-reorder (no swipe gesture)
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN
                        | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT,
                0 /* no swipe */) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder dragged,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = dragged.getAdapterPosition();
                int to   = target.getAdapterPosition();
                if (from == RecyclerView.NO_ID || to == RecyclerView.NO_ID) return false;
                adapter.swapItems(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

            @Override
            public boolean isLongPressDragEnabled() {
                // Prevent long-press from triggering drag (we use drag handle instead)
                return false;
            }
        };

        touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(rv);
        adapter.setDragStartCallback(viewHolder -> touchHelper.startDrag(viewHolder));
    }

    private void generatePdf() {
        if (imageUris.isEmpty()) {
            Toast.makeText(this, "Add at least one image", Toast.LENGTH_SHORT).show();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        btnGenerate.setEnabled(false);
        btnGenerate.setText("Generating…");

        List<Uri> snapshot = new ArrayList<>(imageUris);

        new Thread(() -> {
            PdfGenerator generator = new PdfGenerator(this);
            PdfGenerator.Result result = generator.generate(snapshot);

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                btnGenerate.setEnabled(true);
                btnGenerate.setText("Generate PDF");

                if (result.success) {
                    Intent intent = new Intent(this, PdfResultActivity.class);
                    intent.putExtra(PdfResultActivity.EXTRA_PDF_URI,    result.uri.toString());
                    intent.putExtra(PdfResultActivity.EXTRA_FILE_NAME,  result.fileName);
                    intent.putExtra(PdfResultActivity.EXTRA_PAGE_COUNT, snapshot.size());
                    startActivity(intent);
                } else {
                    Toast.makeText(this,
                            "Failed to generate PDF: " + result.errorMessage,
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }
}
