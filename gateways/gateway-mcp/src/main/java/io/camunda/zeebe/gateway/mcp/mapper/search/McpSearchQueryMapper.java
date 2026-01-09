/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.mapper.search;

import static java.util.Optional.ofNullable;

import io.camunda.gateway.protocol.model.AdvancedDateTimeFilter;
import io.camunda.gateway.protocol.model.AdvancedIncidentErrorTypeFilter;
import io.camunda.gateway.protocol.model.AdvancedIncidentStateFilter;
import io.camunda.gateway.protocol.model.AdvancedStringFilter;
import io.camunda.gateway.protocol.model.BasicStringFilter;
import io.camunda.gateway.protocol.model.BasicStringFilterProperty;
import io.camunda.gateway.protocol.model.CursorBackwardPagination;
import io.camunda.gateway.protocol.model.CursorForwardPagination;
import io.camunda.gateway.protocol.model.DateTimeFilterProperty;
import io.camunda.gateway.protocol.model.IncidentFilter;
import io.camunda.gateway.protocol.model.LimitPagination;
import io.camunda.gateway.protocol.model.OffsetPagination;
import io.camunda.gateway.protocol.model.SearchQueryPageRequest;
import io.camunda.gateway.protocol.model.StringFilterProperty;
import io.camunda.zeebe.gateway.mcp.model.McpIncidentSearchFilter;
import io.camunda.zeebe.gateway.mcp.model.McpSearchQueryPageRequest;
import java.util.Collections;
import java.util.List;

public class McpSearchQueryMapper {

  public static SearchQueryPageRequest toPageRequest(final McpSearchQueryPageRequest page) {
    if (page == null) {
      return new LimitPagination();
    }
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

  public static IncidentFilter toIncidentFilter(final McpIncidentSearchFilter filter) {
    final var filterModel = new IncidentFilter();
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
      ofNullable(createDateTimeFilter(filter.creationTimeFrom(), filter.creationTimeTo()))
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

  private static DateTimeFilterProperty createDateTimeFilter(final String from, final String to) {
    if ((from == null || from.isBlank()) && (to == null || to.isBlank())) {
      return null;
    }
    final var filterModel = new AdvancedDateTimeFilter();
    if (from != null && !from.isBlank()) {
      filterModel.$gte(from);
    }

    if (to != null && !to.isBlank()) {
      filterModel.$lt(to);
    }
    return filterModel;
  }
}
