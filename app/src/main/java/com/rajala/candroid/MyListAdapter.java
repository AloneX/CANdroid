package com.rajala.candroid;

import java.util.ArrayList;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

public class MyListAdapter extends BaseExpandableListAdapter {

    private Context context;
    private ArrayList<MessageInfo> messageList;

    public MyListAdapter(Context context, ArrayList<MessageInfo> messageList) {
        this.context = context;
        this.messageList = messageList;
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        ArrayList<DataInfo> dataList = messageList.get(groupPosition).getDataList();
        return dataList.get(childPosition);
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                             View view, ViewGroup parent) {

        DataInfo dataInfo = (DataInfo)getChild(groupPosition, childPosition);
        if (view == null) {
            LayoutInflater infalInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = infalInflater.inflate(R.layout.child_row, null);
        }

        TextView sequence = (TextView) view.findViewById(R.id.sequence);
        sequence.setText(dataInfo.getTimestamp().trim());
        TextView childItem = (TextView) view.findViewById(R.id.childItem);
        childItem.setText(dataInfo.getData().trim());

        return view;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        ArrayList<DataInfo> dataList = messageList.get(groupPosition).getDataList();
        return dataList.size();
    }

    @Override
    public Object getGroup(int groupPosition) {
        return messageList.get(groupPosition);
    }

    @Override
    public int getGroupCount() {
        return messageList.size();
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isLastChild, View view,
                             ViewGroup parent) {

        MessageInfo messageInfo = (MessageInfo)getGroup(groupPosition);
        if (view == null) {
            LayoutInflater inf = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inf.inflate(R.layout.group_heading, null);
        }

        TextView heading = (TextView) view.findViewById(R.id.heading);
        heading.setText(messageInfo.getID().trim());

        return view;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }
}
