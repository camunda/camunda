/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.mapper;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_AT_LEAST_ONE_FIELD;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_ONLY_ONE_FIELD;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;

import io.camunda.gateway.protocol.model.AdvancedDateTimeFilter;
import io.camunda.gateway.protocol.model.AdvancedIncidentErrorTypeFilter;
import io.camunda.gateway.protocol.model.AdvancedIncidentStateFilter;
import io.camunda.gateway.protocol.model.AdvancedStringFilter;
import io.camunda.gateway.protocol.model.BasicStringFilter;
import io.camunda.gateway.protocol.model.BasicStringFilterProperty;
import io.camunda.gateway.protocol.model.CursorBackwardPagination;
import io.camunda.gateway.protocol.model.CursorForwardPagination;
import io.camunda.gateway.protocol.model.LimitPagination;
import io.camunda.gateway.protocol.model.OffsetPagination;
import io.camunda.gateway.protocol.model.StringFilterProperty;
import io.camunda.gateway.protocol.model.simple.DateTimeFilterProperty;
import io.camunda.gateway.protocol.model.simple.IncidentFilter;
import io.camunda.gateway.protocol.model.simple.SearchQueryPageRequest;
import io.camunda.service.exception.ServiceError;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class McpSearchQueryMapper {

  public static io.camunda.gateway.protocol.model.SearchQueryPageRequest toPageRequest(
      final SearchQueryPageRequest page) {
    if (page == null) {
      return new LimitPagination();
    }
    // validate fields
    if (page.from() == null
        && page.before() == null
        && page.after() == null
        && page.limit() == null) {
      throw new ServiceException(
          new ServiceError(
              ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(
                      List.of("after", "before", "from", "limit"))
                  + " in the page.",
              Status.INVALID_ARGUMENT));
    }
    if (Stream.of(page.from(), page.before(), page.after()).filter(not(Objects::isNull)).count()
        > 1) {
      throw new ServiceException(
          new ServiceError(
              ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(List.of("after", "before", "from"))
                  + " in the page.",
              Status.INVALID_ARGUMENT));
    }
    // create page request
    if (page.before() != null) {
      return new CursorBackwardPagination().before(page.before()).limit(page.limit());
    }
    if (page.after() != null) {
      return new CursorForwardPagination().after(page.after()).limit(page.limit());
    }
    if (page.from() != null) {
      return new OffsetPagination().from(page.from()).limit(page.limit());
    }
    return new LimitPagination().limit(page.limit());
  }

  public static <T> List<T> toSortRequest(final List<T> sort) {
    return sort == null ? Collections.emptyList() : sort;
  }

  public static io.camunda.gateway.protocol.model.IncidentFilter toIncidentFilter(
      final IncidentFilter filter) {
    final var filterModel = new io.camunda.gateway.protocol.model.IncidentFilter();
    if (filter != null) {
      ofNullable(filter.processDefinitionId())
          .map(McpSearchQueryMapper::getStringFilter)
          .ifPresent(filterModel::processDefinitionId);
      ofNullable(filter.errorType())
          .map(e -> new AdvancedIncidentErrorTypeFilter().$eq(e))
          .ifPresent(filterModel::errorType);
      ofNullable(filter.elementId())
          .map(McpSearchQueryMapper::getStringFilter)
          .ifPresent(filterModel::elementId);
      ofNullable(filter.creationTime())
          .map(McpSearchQueryMapper::getDateTimeFilter)
          .ifPresent(filterModel::creationTime);
      ofNullable(filter.state())
          .map(s -> new AdvancedIncidentStateFilter().$eq(s))
          .ifPresent(filterModel::state);
      ofNullable(filter.processDefinitionKey())
          .map(McpSearchQueryMapper::getBasicStringFilter)
          .ifPresent(filterModel::processDefinitionKey);
      ofNullable(filter.processInstanceKey())
          .map(McpSearchQueryMapper::getBasicStringFilter)
          .ifPresent(filterModel::processInstanceKey);
    }
    return filterModel;
  }

  private static StringFilterProperty getStringFilter(final String value) {
    return new AdvancedStringFilter().$eq(value);
  }

  private static BasicStringFilterProperty getBasicStringFilter(final long value) {
    return new BasicStringFilter().$eq(String.valueOf(value));
  }

  private static StringFilterProperty getStringFilter(final Enum<?> value) {
    return new AdvancedStringFilter().$eq(value.name());
  }

  private static io.camunda.gateway.protocol.model.DateTimeFilterProperty getDateTimeFilter(
      final DateTimeFilterProperty value) {
    if (value == null
        || (value.from() == null || value.from().isBlank())
            && (value.to() == null || value.to().isBlank())) {
      return null;
    }
    final var filterModel = new AdvancedDateTimeFilter();
    if (value.from() != null && !value.from().isBlank()) {
      filterModel.$gte(value.from());
    }

    if (value.to() != null && !value.to().isBlank()) {
      filterModel.$lt(value.to());
    }
    return filterModel;
  }
}
