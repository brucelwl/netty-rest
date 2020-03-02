package com.lwl.entity;

/**
 * Created by bruce in 2020/3/2 10:54
 */
public class Address {

   private int code;

   private String detail;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    @Override
    public String toString() {
        return "Address{" +
                "code=" + code +
                ", detail='" + detail + '\'' +
                '}';
    }
}
