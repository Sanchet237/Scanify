package com.example.scanify;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PdfGenerator {

    private static final int PAGE_WIDTH  = 595;
    private static final int PAGE_HEIGHT = 842;

    private final Context context;

    public PdfGenerator(Context context) {
        this.context = context.getApplicationContext();
    }

    public static class Result {
        public final boolean success;
        public final Uri uri;
        public final String fileName;
        public final String errorMessage;

        Result(boolean success, Uri uri, String fileName, String errorMessage) {
            this.success = success;
            this.uri = uri;
            this.fileName = fileName;
            this.errorMessage = errorMessage;
        }
    }

    public Result generate(List<Uri> imageUris) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "Scanify_" + timeStamp + ".pdf";

        PdfDocument pdfDocument = new PdfDocument();

        try {
            for (int i = 0; i < imageUris.size(); i++) {
                Bitmap bitmap = loadBitmap(imageUris.get(i));
                if (bitmap == null) continue;

                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                        PAGE_WIDTH, PAGE_HEIGHT, i + 1).create();
                PdfDocument.Page page = pdfDocument.startPage(pageInfo);

                drawBitmapOnPage(page.getCanvas(), bitmap);
                bitmap.recycle();

                pdfDocument.finishPage(page);
            }

            Uri savedUri = savePdf(pdfDocument, fileName);
            return new Result(true, savedUri, fileName, null);

        } catch (Exception e) {
            return new Result(false, null, null, e.getMessage());
        } finally {
            pdfDocument.close();
        }
    }

    // Down-sample large bitmaps to prevent memory issues
    private Bitmap loadBitmap(Uri uri) {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) return null;

            // First decode just the bounds
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStream, null, options);

            options.inSampleSize = calculateInSampleSize(options, PAGE_WIDTH, PAGE_HEIGHT);
            options.inJustDecodeBounds = false;

            try (InputStream is2 = context.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(is2, null, options);
            }
        } catch (IOException e) {
            return null;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width  = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth  = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth  / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private void drawBitmapOnPage(Canvas canvas, Bitmap bitmap) {
        float bitmapWidth  = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();

        float scaleX = PAGE_WIDTH  / bitmapWidth;
        float scaleY = PAGE_HEIGHT / bitmapHeight;
        float scale  = Math.min(scaleX, scaleY);

        float scaledW = bitmapWidth  * scale;
        float scaledH = bitmapHeight * scale;

        // Center on page
        float left = (PAGE_WIDTH  - scaledW) / 2f;
        float top  = (PAGE_HEIGHT - scaledH) / 2f;

        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        matrix.postTranslate(left, top);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(bitmap, matrix, paint);
    }

    // Save PDF to Downloads using appropriate API for each Android version
    private Uri savePdf(PdfDocument pdfDocument, String fileName) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Scanify");

            Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri itemUri = context.getContentResolver().insert(collection, values);
            if (itemUri == null) throw new IOException("MediaStore insert failed");

            try (OutputStream out = context.getContentResolver().openOutputStream(itemUri)) {
                if (out == null) throw new IOException("Could not open output stream");
                pdfDocument.writeTo(out);
            }
            return itemUri;

        } else {
            File downloadsDir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "Scanify");
            if (!downloadsDir.exists()) downloadsDir.mkdirs();

            File pdfFile = new File(downloadsDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
                pdfDocument.writeTo(fos);
            }

            return FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    pdfFile);
        }
    }
}
