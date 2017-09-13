package com.zimbra.cs.index.contactanalytics;

public class DataPoint<T, F> {
    T x;
    F y;
    public DataPoint(T x, F y) {
        this.x = x;
        this.y = y;
    }

    public T getX() {
        return x;
    }

    public void setX(T x) {
        this.x = x;
    }

    public F getY() {
        return y;
    }

    public void setY(F y) {
        this.y = y;
    }
}
