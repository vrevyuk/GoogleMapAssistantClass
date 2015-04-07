package com.revyuk.googlemapassistantclass.models;

/**
 * Created by Notebook on 07.04.2015.
 */
public class Leg {
    public DDValue distance;
    public DDValue duration;
    public String end_address;
    public Location end_location;
    public String start_address;
    public Location start_location;
    public Step[] steps;
}
