package com.zimbra.cs.index.contactanalytics;

import com.zimbra.cs.mailbox.Contact;

import java.util.ArrayList;
import java.util.List;

public class InteractionFrequencyGraphResult {
    private Contact with;
    private long from;
    private long to;
    private InteractionFrequencyRecorder.AggregationUnit aggregationUnit;
    private String xlabel;
    private String ylabel = "Frequency";
    private List<DataPoint<String, Integer>> data = new ArrayList<>();

    public InteractionFrequencyGraphResult(Contact with, long from, long to, InteractionFrequencyRecorder.AggregationUnit aggregationUnit) {
        this.with = with;
        this.from = from;
        this.to = to;
        this.aggregationUnit = aggregationUnit;
        this.xlabel = aggregationUnit.name();
    }

    public Contact getWith() {
        return with;
    }

    public void setWith(Contact with) {
        this.with = with;
    }

    public long getFrom() {
        return from;
    }

    public void setFrom(long from) {
        this.from = from;
    }

    public long getTo() {
        return to;
    }

    public void setTo(long to) {
        this.to = to;
    }

    public InteractionFrequencyRecorder.AggregationUnit getAggregationUnit() {
        return aggregationUnit;
    }

    public void setAggregationUnit(InteractionFrequencyRecorder.AggregationUnit aggregationUnit) {
        this.aggregationUnit = aggregationUnit;
    }

    public String getXlabel() {
        return xlabel;
    }

    public void setXlabel(String xlabel) {
        this.xlabel = xlabel;
    }

    public String getYlabel() {
        return ylabel;
    }

    public void setYlabel(String ylabel) {
        this.ylabel = ylabel;
    }

    public List<DataPoint<String, Integer>> getData() {
        return data;
    }

    public void setData(List<DataPoint<String, Integer>> data) {
        this.data = data;
    }

    public void addDataPoint(String xCoordinate, Integer yCoordinate) {
        data.add(new DataPoint<>(xCoordinate, yCoordinate));
    }
}
