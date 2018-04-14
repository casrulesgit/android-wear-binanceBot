package com.example.caspar.binancebot;

import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Created by Caspar on 13.04.18.
 */

public class CustomAdapter extends RecyclerView.Adapter<CustomAdapter.ViewHolder> {
    //data stored in the Adapter
    private List<Asset> mDataset;
    private CustomAdapter.ItemSelectedListener mListener;

    //constructor getting data
    public CustomAdapter(List<Asset> myDataset) {
        mDataset = myDataset;
    }

    //ViewHolder binding the data to the view making onClick events possible
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private TextView name;
        private TextView price;
        private TextView change;

        ViewHolder(View view) {
            super(view);
            name = view.findViewById(R.id.assetName);
            price = view.findViewById(R.id.assetPrice);
            change = view.findViewById(R.id.assetChange);
        }

        void bind(final int position, final ItemSelectedListener listener){

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (listener != null) {
                        listener.onItemSelected(position);
                    }
                }
            });
        }

    }

    //onClick event listener
    public void setListener(CustomAdapter.ItemSelectedListener itemSelectedListener) {
        mListener = itemSelectedListener;
    }

    //adds all transaction in the right order
    public void add(Asset asset, int position){
        mDataset.add(asset);
    }

    //delete items from the list
    public void remove(int position){
        mDataset.remove(position);
    }

    @Override
    public CustomAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                       int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_asset, parent, false);
        // work here if you need to control height of your items
        // keep in mind that parent is RecyclerView in this case

        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        String name = mDataset.get(position).getName();
        holder.name.setText(name);
        holder.price.setText(mDataset.get(position).getPrice());
        holder.change.setText(mDataset.get(position).getChange());
        if (Double.parseDouble(mDataset.get(position).getChange()) < 0){
            holder.change.setText(Html.fromHtml("<font color=red>" + mDataset.get(position).getChange() + "</font>"));
        }else{
            holder.change.setText(Html.fromHtml("<font color=green>" + mDataset.get(position).getChange() + "</font>"));
        }
        holder.bind(position, mListener);

    }

    @Override
    public int getItemCount() {
        return mDataset.size();
    }

    //interface for the callback
    public interface ItemSelectedListener {
        void onItemSelected(int position);
    }

}
