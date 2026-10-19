package com.example.atw;

import com.example.atw.dto.PageResponse;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageResponseTest {

    @Test
    void testPageResponse_Of_WithContent() {
        List<String> items = Arrays.asList("item1", "item2", "item3");
        PageResponse<String> response = PageResponse.of(items, 0, 10, 25);

        assertNotNull(response);
        assertEquals(3, response.getItems().size());
        assertEquals(0, response.getPageNumber());
        assertEquals(10, response.getPageSize());
        assertEquals(25, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        assertTrue(response.isFirst());
        assertFalse(response.isLast());
        assertFalse(response.isEmpty());
    }

    @Test
    void testPageResponse_Of_EmptyContent() {
        List<String> items = Collections.emptyList();
        PageResponse<String> response = PageResponse.of(items, 0, 10, 0);

        assertNotNull(response);
        assertTrue(response.getItems().isEmpty());
        assertEquals(0, response.getTotalElements());
        assertEquals(0, response.getTotalPages());
        assertTrue(response.isFirst());
        assertTrue(response.isLast());
        assertTrue(response.isEmpty());
    }

    @Test
    void testPageResponse_Of_LastPage() {
        List<String> items = Arrays.asList("item1", "item2");
        PageResponse<String> response = PageResponse.of(items, 2, 10, 22);

        assertFalse(response.isFirst());
        assertTrue(response.isLast());
    }

    @Test
    void testPageResponse_Of_NullContent() {
        PageResponse<String> response = PageResponse.of(null, 0, 10, 0);

        assertTrue(response.isEmpty());
    }
}
