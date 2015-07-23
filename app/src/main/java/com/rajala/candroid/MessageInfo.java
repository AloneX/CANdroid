package com.rajala.candroid;

import java.util.ArrayList;

public class MessageInfo {

    private String id;

    private ArrayList<DataInfo> dataList = new ArrayList<>();;

    public String getID() {
        return id;
    }

    public void setID(String id) {
        this.id = id;
    }

    public ArrayList<DataInfo> getDataList() {
        return dataList;
    }

    public void setDataList(ArrayList<DataInfo> dataList) {
        this.dataList = dataList;
    }

}
