package com.revyuk.googlemapassistantclass.models;

/**
 * Created by Notebook on 07.04.2015.
 */
public class GeocodingResult {
    public String[] types;
    public String formatted_address;
    public AddressComponent[] address_components;
    public Geometry geometry;
}
