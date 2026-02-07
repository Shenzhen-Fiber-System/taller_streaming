package com.ourshop.streaming.streams.api.dto;

import java.util.List;

public record StreamMetaPageResponse(
        List<StreamMetaResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
