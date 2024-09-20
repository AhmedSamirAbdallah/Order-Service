package com.service.order.model.dto;

import java.util.List;

public record PaginatedResponse<T>(
        List<T> content,
        Integer pageNumber,
        Integer pageSize,
        Long totalElements,
        Integer totalPages

) {
}
