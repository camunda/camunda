/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search;

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
import io.camunda.gateway.protocol.model.simple.IncidentFilter;
import io.camunda.gateway.protocol.model.simple.SearchQueryPageRequest;
import io.camunda.gateway.protocol.model.simple.SimpleDateTimeFilterProperty;
import io.camunda.service.exception.ServiceError;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import java.util.List;
import java.util.stream.Stream;

/**
 * Mapper for converting simple search request models into advanced search query representations.
 *
 * <p>This class provides static helper methods to:
 *
 * <ul>
 *   <li>Validate and map simple {@link SearchQueryPageRequest} instances to advanced pagination
 *       requests (e.g. limit, cursor, and offset-based pagination).
 *   <li>Map simple filters to advanced query components with equality semantics.
 */
public class SimpleSearchQueryMapper {

  public static io.camunda.gateway.protocol.model.SearchQueryPageRequest toPageRequest(
      final SearchQueryPageRequest page) {
    if (page == null) {
      return new LimitPagination();
    }
    // validate fields
    if (isEmpty(page.getLimit())
        && isEmpty(page.getBefore())
        && isEmpty(page.getAfter())
        && isEmpty(page.getFrom())) {
      throw new ServiceException(
          new ServiceError(
              ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(
                      List.of("after", "before", "from", "limit"))
                  + " in the page.",
              Status.INVALID_ARGUMENT));
    }
    if (Stream.of(page.getFrom(), page.getBefore(), page.getAfter())
            .filter(not(SimpleSearchQueryMapper::isEmpty))
            .count()
        > 1) {
      throw new ServiceException(
          new ServiceError(
              ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(List.of("after", "before", "from"))
                  + " in the page.",
              Status.INVALID_ARGUMENT));
    }
    // create page request
    if (!isEmpty(page.getBefore())) {
      return new CursorBackwardPagination().before(page.getBefore()).limit(page.getLimit());
    }
    if (!isEmpty(page.getAfter())) {
      return new CursorForwardPagination().after(page.getAfter()).limit(page.getLimit());
    }
    if (!isEmpty(page.getFrom())) {
      return new OffsetPagination().from(page.getFrom()).limit(page.getLimit());
    }
    return new LimitPagination().limit(page.getLimit());
  }

  private static boolean isEmpty(final Object value) {
    if (value == null) {
      return true;
    }
    if (value instanceof final String str) {
      return str.isBlank();
    }
    return false;
  }

  public static io.camunda.gateway.protocol.model.IncidentFilter toIncidentFilter(
      final IncidentFilter filter) {
    final var filterModel = new io.camunda.gateway.protocol.model.IncidentFilter();
    if (filter != null) {
      ofNullable(filter.getProcessDefinitionId())
          .map(SimpleSearchQueryMapper::getStringFilter)
          .ifPresent(filterModel::processDefinitionId);
      ofNullable(filter.getErrorType())
          .map(e -> new AdvancedIncidentErrorTypeFilter().$eq(e))
          .ifPresent(filterModel::errorType);
      ofNullable(filter.getErrorMessage())
          .map(SimpleSearchQueryMapper::getStringFilter)
          .ifPresent(filterModel::errorMessage);
      ofNullable(filter.getElementId())
          .map(SimpleSearchQueryMapper::getStringFilter)
          .ifPresent(filterModel::elementId);
      ofNullable(filter.getCreationTime())
          .map(SimpleSearchQueryMapper::getDateTimeFilter)
          .ifPresent(filterModel::creationTime);
      ofNullable(filter.getState())
          .map(s -> new AdvancedIncidentStateFilter().$eq(s))
          .ifPresent(filterModel::state);
      ofNullable(filter.getTenantId())
          .map(SimpleSearchQueryMapper::getStringFilter)
          .ifPresent(filterModel::tenantId);
      ofNullable(filter.getIncidentKey())
          .map(SimpleSearchQueryMapper::getBasicStringFilter)
          .ifPresent(filterModel::incidentKey);
      ofNullable(filter.getProcessDefinitionKey())
          .map(SimpleSearchQueryMapper::getBasicStringFilter)
          .ifPresent(filterModel::processDefinitionKey);
      ofNullable(filter.getProcessInstanceKey())
          .map(SimpleSearchQueryMapper::getBasicStringFilter)
          .ifPresent(filterModel::processInstanceKey);
      ofNullable(filter.getElementInstanceKey())
          .map(SimpleSearchQueryMapper::getBasicStringFilter)
          .ifPresent(filterModel::elementInstanceKey);
      ofNullable(filter.getJobKey())
          .map(SimpleSearchQueryMapper::getBasicStringFilter)
          .ifPresent(filterModel::jobKey);
    }
    return filterModel;
  }

  private static StringFilterProperty getStringFilter(final String value) {
    return new AdvancedStringFilter().$eq(value);
  }

  private static BasicStringFilterProperty getBasicStringFilter(final String value) {
    return new BasicStringFilter().$eq(value);
  }

  private static io.camunda.gateway.protocol.model.DateTimeFilterProperty getDateTimeFilter(
      final SimpleDateTimeFilterProperty value) {
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
