package com.pdl.scraper;

public class DocumentDetails {
    private int id;
    private String name;
    private int numberOfPages;
    private String thumbnail;

    public DocumentDetails(int id, String name, int numberOfPages, String thumbnail) {
        this.id = id;
        this.name = name;
        this.numberOfPages = numberOfPages;
        this.thumbnail = thumbnail;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getNumberOfPages() {
        return numberOfPages;
    }

    public void setNumberOfPages(int numberOfPages) {
        this.numberOfPages = numberOfPages;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }
}
