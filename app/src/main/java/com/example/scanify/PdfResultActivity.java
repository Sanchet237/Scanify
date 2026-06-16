package com.example.scanify;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import android.content.ContentValues;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

/**
 * PdfResultActivity
 * -----------------
 * Shown after a PDF is successfully generated.
 * Lets the user:
 *   1. Rename the PDF
 *   2. Save / Download it to Downloads
 *   3. Share it via any app
 */
public class PdfResultActivity extends AppCompatActivity {

    public static final String EXTRA_PDF_URI    = "extra_pdf_uri";
    public static final String EXTRA_FILE_NAME  = "extra_file_name";
    public static final String EXTRA_PAGE_COUNT = "extra_page_count";

    private Uri    pdfUri;
    private String currentFileName;
    private int    pageCount;

    private TextView tvPdfName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_result);

        String uriStr = getIntent().getStringExtra(EXTRA_PDF_URI);
        currentFileName = getIntent().getStringExtra(EXTRA_FILE_NAME);
        pageCount       = getIntent().getIntExtra(EXTRA_PAGE_COUNT, 0);
        pdfUri          = Uri.parse(uriStr);

        tvPdfName = findViewById(R.id.tvPdfName);
        TextView tvPageCount  = findViewById(R.id.tvPageCount);
        ImageView btnRename   = findViewById(R.id.btnRename);
        MaterialButton btnDownload       = findViewById(R.id.btnDownload);
        MaterialButton btnShare          = findViewById(R.id.btnShare);
        MaterialButton btnConvertAnother = findViewById(R.id.btnConvertAnother);

        tvPdfName.setText(currentFileName);
        tvPageCount.setText(pageCount + (pageCount == 1 ? " page" : " pages"));

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        btnRename.setOnClickListener(v -> showRenameDialog());
        btnDownload.setOnClickListener(v -> downloadPdf());
        btnShare.setOnClickListener(v -> sharePdf());
        btnConvertAnother.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void showRenameDialog() {
        String nameWithoutExt = currentFileName.endsWith(".pdf")
                ? currentFileName.substring(0, currentFileName.length() - 4)
                : currentFileName;

        EditText editText = new EditText(this);
        editText.setText(nameWithoutExt);
        editText.setSelectAllOnFocus(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        editText.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle("Rename PDF")
                .setView(editText)
                .setPositiveButton("Rename", (dialog, which) -> {
                    String newName = editText.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newName.endsWith(".pdf")) newName = newName + ".pdf";
                    currentFileName = newName;
                    tvPdfName.setText(currentFileName);
                    Toast.makeText(this, "Renamed to: " + currentFileName, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void downloadPdf() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, currentFileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                values.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/Scanify");

                Uri dest = getContentResolver().insert(
                        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                        values);

                if (dest == null) throw new IOException("MediaStore insert failed");

                try (InputStream in  = getContentResolver().openInputStream(pdfUri);
                     OutputStream out = getContentResolver().openOutputStream(dest)) {
                    if (in == null || out == null) throw new IOException("Stream error");
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                }

                Toast.makeText(this,
                        "Saved to Downloads/Scanify/" + currentFileName,
                        Toast.LENGTH_LONG).show();

            } else {
                File dir = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "Scanify");
                if (!dir.exists()) dir.mkdirs();

                File dest = new File(dir, currentFileName);
                try (InputStream in  = getContentResolver().openInputStream(pdfUri);
                     FileOutputStream out = new FileOutputStream(dest)) {
                    if (in == null) throw new IOException("Cannot read source PDF");
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                }

                Toast.makeText(this,
                        "Saved to Downloads/Scanify/" + currentFileName,
                        Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            Toast.makeText(this,
                    "Download failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void sharePdf() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, currentFileName);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share PDF via"));
    }
}
