package com.example.scanify;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Manages image grid with drag reordering and long-press delete.
 * Long-press = delete, drag handle touch = start drag.
 */
public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {

    public interface RemoveCallback {
        void onRemove(int position);
    }

    public interface DragStartCallback {
        void onStartDrag(ImageViewHolder viewHolder);
    }

    private final Context           context;
    private final List<Uri>         images;
    private final RemoveCallback    removeCallback;
    private       DragStartCallback dragStartCallback;

    public ImageAdapter(Context context, List<Uri> images, RemoveCallback removeCallback) {
        this.context        = context;
        this.images         = images;
        this.removeCallback = removeCallback;
    }

    public void setDragStartCallback(DragStartCallback cb) {
        this.dragStartCallback = cb;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        holder.tvPageNumber.setText(String.valueOf(position + 1));
        holder.imageView.setImageURI(images.get(position));

        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_ID) {
                removeCallback.onRemove(pos);
            }
            return true;
        });

        holder.dragHandle.setOnTouchListener((v, event) -> {
            if (dragStartCallback != null) {
                dragStartCallback.onStartDrag(holder);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() { return images.size(); }

    public void swapItems(int from, int to) {
        Uri temp = images.get(from);
        images.set(from, images.get(to));
        images.set(to, temp);
        notifyItemMoved(from, to);
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ImageView dragHandle;
        TextView  tvPageNumber;

        ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView    = itemView.findViewById(R.id.ivThumbnail);
            dragHandle   = itemView.findViewById(R.id.ivDragHandle);
            tvPageNumber = itemView.findViewById(R.id.tvPageNumber);
        }
    }
}
