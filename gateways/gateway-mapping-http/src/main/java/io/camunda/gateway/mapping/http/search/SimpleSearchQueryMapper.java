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
import io.camunda.gateway.protocol.model.AdvancedElementInstanceStateFilter;
import io.camunda.gateway.protocol.model.AdvancedIncidentErrorTypeFilter;
import io.camunda.gateway.protocol.model.AdvancedIncidentStateFilter;
import io.camunda.gateway.protocol.model.AdvancedIntegerFilter;
import io.camunda.gateway.protocol.model.AdvancedProcessInstanceStateFilter;
import io.camunda.gateway.protocol.model.AdvancedStringFilter;
import io.camunda.gateway.protocol.model.BasicStringFilter;
import io.camunda.gateway.protocol.model.BasicStringFilterProperty;
import io.camunda.gateway.protocol.model.CursorBackwardPagination;
import io.camunda.gateway.protocol.model.CursorForwardPagination;
import io.camunda.gateway.protocol.model.LimitPagination;
import io.camunda.gateway.protocol.model.OffsetPagination;
import io.camunda.gateway.protocol.model.ProcessInstanceFilterFields;
import io.camunda.gateway.protocol.model.StringFilterProperty;
import io.camunda.gateway.protocol.model.VariableValueFilterProperty;
import io.camunda.gateway.protocol.model.simple.IncidentFilter;
import io.camunda.gateway.protocol.model.simple.SearchQueryPageRequest;
import io.camunda.gateway.protocol.model.simple.SimpleDateTimeFilterProperty;
import io.camunda.service.exception.ServiceError;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import java.util.List;
import java.util.Set;
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

  public static io.camunda.gateway.protocol.model.ProcessInstanceFilter toProcessInstanceFilter(
      final io.camunda.gateway.protocol.model.simple.ProcessInstanceFilter filter) {
    final var filterModel = new io.camunda.gateway.protocol.model.ProcessInstanceFilter();
    if (filter != null) {
      mapProcessInstanceFilterFields(filter, filterModel);
      ofNullable(filter.get$Or())
          .filter(not(List::isEmpty))
          .ifPresent(
              orGroups ->
                  filterModel.$or(
                      orGroups.stream()
                          .map(
                              fields -> {
                                final var mapped = new ProcessInstanceFilterFields();
                                mapProcessInstanceFilterFields(fields, mapped);
                                return mapped;
                              })
                          .toList()));
    }
    return filterModel;
  }

  private static void mapProcessInstanceFilterFields(
      final io.camunda.gateway.protocol.model.simple.ProcessInstanceFilter source,
      final io.camunda.gateway.protocol.model.ProcessInstanceFilterFields target) {
    mapProcessInstanceFilterFields(toProcessInstanceFilterFields(source), target);
  }

  private static void mapProcessInstanceFilterFields(
      final io.camunda.gateway.protocol.model.simple.ProcessInstanceFilterFields source,
      final io.camunda.gateway.protocol.model.ProcessInstanceFilterFields target) {
    ofNullable(source.getStartDate())
        .map(SimpleSearchQueryMapper::getDateTimeFilter)
        .ifPresent(target::startDate);
    ofNullable(source.getEndDate())
        .map(SimpleSearchQueryMapper::getDateTimeFilter)
        .ifPresent(target::endDate);
    ofNullable(source.getState())
        .map(state -> new AdvancedProcessInstanceStateFilter().$eq(state))
        .ifPresent(target::state);

    ofNullable(source.getHasIncident()).ifPresent(target::hasIncident);

    ofNullable(source.getTenantId())
        .map(SimpleSearchQueryMapper::getStringFilter)
        .ifPresent(target::tenantId);

    ofNullable(source.getVariables())
        .filter(not(List::isEmpty))
        .ifPresent(
            vars ->
                target.variables(
                    vars.stream()
                        .map(
                            v ->
                                new VariableValueFilterProperty(
                                    v.getName(), getStringFilter(v.getValue())))
                        .toList()));

    ofNullable(source.getProcessInstanceKey())
        .map(SimpleSearchQueryMapper::getBasicStringFilter)
        .ifPresent(target::processInstanceKey);
    ofNullable(source.getParentProcessInstanceKey())
        .map(SimpleSearchQueryMapper::getBasicStringFilter)
        .ifPresent(target::parentProcessInstanceKey);
    ofNullable(source.getParentElementInstanceKey())
        .map(SimpleSearchQueryMapper::getBasicStringFilter)
        .ifPresent(target::parentElementInstanceKey);

    ofNullable(source.getBatchOperationId())
        .map(SimpleSearchQueryMapper::getStringFilter)
        .ifPresent(target::batchOperationId);
    ofNullable(source.getErrorMessage())
        .map(SimpleSearchQueryMapper::getStringFilter)
        .ifPresent(target::errorMessage);
    ofNullable(source.getHasRetriesLeft()).ifPresent(target::hasRetriesLeft);
    ofNullable(source.getElementInstanceState())
        .map(state -> new AdvancedElementInstanceStateFilter().$eq(state))
        .ifPresent(target::elementInstanceState);
    ofNullable(source.getElementId())
        .map(SimpleSearchQueryMapper::getStringFilter)
        .ifPresent(target::elementId);
    ofNullable(source.getHasElementInstanceIncident())
        .ifPresent(target::hasElementInstanceIncident);
    ofNullable(source.getIncidentErrorHashCode())
        .map(SimpleSearchQueryMapper::getIntegerFilter)
        .ifPresent(target::incidentErrorHashCode);

    ofNullable(source.getTags()).filter(not(Set::isEmpty)).ifPresent(target::tags);

    ofNullable(source.getProcessDefinitionId())
        .map(SimpleSearchQueryMapper::getStringFilter)
        .ifPresent(target::processDefinitionId);
    ofNullable(source.getProcessDefinitionName())
        .map(SimpleSearchQueryMapper::getStringFilter)
        .ifPresent(target::processDefinitionName);
    ofNullable(source.getProcessDefinitionVersion())
        .map(SimpleSearchQueryMapper::getIntegerFilter)
        .ifPresent(target::processDefinitionVersion);
    ofNullable(source.getProcessDefinitionVersionTag())
        .map(SimpleSearchQueryMapper::getStringFilter)
        .ifPresent(target::processDefinitionVersionTag);
    ofNullable(source.getProcessDefinitionKey())
        .map(SimpleSearchQueryMapper::getBasicStringFilter)
        .ifPresent(target::processDefinitionKey);
  }

  private static io.camunda.gateway.protocol.model.simple.ProcessInstanceFilterFields
      toProcessInstanceFilterFields(
          final io.camunda.gateway.protocol.model.simple.ProcessInstanceFilter source) {
    final var fields = new io.camunda.gateway.protocol.model.simple.ProcessInstanceFilterFields();
    if (source == null) {
      return fields;
    }

    fields
        .startDate(source.getStartDate())
        .endDate(source.getEndDate())
        .state(source.getState())
        .hasIncident(source.getHasIncident())
        .tenantId(source.getTenantId())
        .variables(source.getVariables())
        .processInstanceKey(source.getProcessInstanceKey())
        .parentProcessInstanceKey(source.getParentProcessInstanceKey())
        .parentElementInstanceKey(source.getParentElementInstanceKey())
        .batchOperationId(source.getBatchOperationId())
        .errorMessage(source.getErrorMessage())
        .hasRetriesLeft(source.getHasRetriesLeft())
        .elementInstanceState(source.getElementInstanceState())
        .elementId(source.getElementId())
        .hasElementInstanceIncident(source.getHasElementInstanceIncident())
        .incidentErrorHashCode(source.getIncidentErrorHashCode())
        .tags(source.getTags())
        .processDefinitionId(source.getProcessDefinitionId())
        .processDefinitionName(source.getProcessDefinitionName())
        .processDefinitionVersion(source.getProcessDefinitionVersion())
        .processDefinitionVersionTag(source.getProcessDefinitionVersionTag())
        .processDefinitionKey(source.getProcessDefinitionKey());

    return fields;
  }

  public static io.camunda.gateway.protocol.model.ProcessDefinitionFilter toProcessDefinitionFilter(
      final io.camunda.gateway.protocol.model.simple.ProcessDefinitionFilter filter) {
    final var filterModel = new io.camunda.gateway.protocol.model.ProcessDefinitionFilter();
    if (filter != null) {
      ofNullable(filter.getName())
          .map(SimpleSearchQueryMapper::getStringFilter)
          .ifPresent(filterModel::name);
      ofNullable(filter.getIsLatestVersion()).ifPresent(filterModel::isLatestVersion);
      ofNullable(filter.getResourceName()).ifPresent(filterModel::resourceName);
      ofNullable(filter.getVersion()).ifPresent(filterModel::version);
      ofNullable(filter.getVersionTag()).ifPresent(filterModel::versionTag);
      ofNullable(filter.getProcessDefinitionId())
          .map(SimpleSearchQueryMapper::getStringFilter)
          .ifPresent(filterModel::processDefinitionId);
      ofNullable(filter.getTenantId()).ifPresent(filterModel::tenantId);
      ofNullable(filter.getProcessDefinitionKey()).ifPresent(filterModel::processDefinitionKey);
      ofNullable(filter.getHasStartForm()).ifPresent(filterModel::hasStartForm);
    }
    return filterModel;
  }

  public static io.camunda.gateway.protocol.model.VariableFilter toVariableFilter(
      final io.camunda.gateway.protocol.model.simple.VariableFilter filter) {
    final var filterModel = new io.camunda.gateway.protocol.model.VariableFilter();
    if (filter != null) {
      ofNullable(filter.getName())
          .map(SimpleSearchQueryMapper::getStringFilter)
          .ifPresent(filterModel::name);
      ofNullable(filter.getValue())
          .map(SimpleSearchQueryMapper::getStringFilter)
          .ifPresent(filterModel::value);
      ofNullable(filter.getTenantId()).ifPresent(filterModel::tenantId);
      ofNullable(filter.getIsTruncated()).ifPresent(filterModel::isTruncated);
      ofNullable(filter.getVariableKey())
          .map(SimpleSearchQueryMapper::getBasicStringFilter)
          .ifPresent(filterModel::variableKey);
      ofNullable(filter.getScopeKey())
          .map(SimpleSearchQueryMapper::getStringFilter)
          .ifPresent(filterModel::scopeKey);
      ofNullable(filter.getProcessInstanceKey())
          .map(SimpleSearchQueryMapper::getBasicStringFilter)
          .ifPresent(filterModel::processInstanceKey);
    }
    return filterModel;
  }

  private static StringFilterProperty getStringFilter(final String value) {
    return new AdvancedStringFilter().$eq(value);
  }

  private static BasicStringFilterProperty getBasicStringFilter(final String value) {
    return new BasicStringFilter().$eq(value);
  }

  private static io.camunda.gateway.protocol.model.IntegerFilterProperty getIntegerFilter(
      final Integer value) {
    return new AdvancedIntegerFilter().$eq(value);
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
