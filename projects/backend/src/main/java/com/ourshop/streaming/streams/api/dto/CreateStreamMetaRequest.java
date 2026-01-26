package com.ourshop.streaming.streams.api.dto;

import jakarta.validation.constraints.Size;

public record CreateStreamMetaRequest(
        @Size(max = 200) String title,
        @Size(max = 2000) String description
) {
}
