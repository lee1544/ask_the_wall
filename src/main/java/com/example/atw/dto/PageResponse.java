package com.example.atw.dto;

import lombok.Data;
import java.util.List;

@Data
public class PageResponse<T> {
    private List<T> items;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;

    public PageResponse(List<T> items, int pageNumber, int pageSize, long totalElements, int totalPages, boolean first, boolean last, boolean empty) {
        this.items = items;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.first = first;
        this.last = last;
        this.empty = empty;
    }

    public static <T> PageResponse<T> of(List<T> content, int pageNumber, int pageSize, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        boolean first = pageNumber == 0;
        boolean last = pageNumber >= totalPages - 1;
        boolean empty = content == null || content.isEmpty();
        
        return new PageResponse<>(content, pageNumber, pageSize, totalElements, totalPages, first, last, empty);
    }
}
