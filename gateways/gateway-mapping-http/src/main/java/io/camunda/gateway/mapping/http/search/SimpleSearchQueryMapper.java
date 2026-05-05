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
import org.jspecify.annotations.Nullable;

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
      final @Nullable SearchQueryPageRequest page) {
    if (page == null) {
      return LimitPagination.Builder.create().build();
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
    final var before = page.getBefore();
    if (!isEmpty(before)) {
      return CursorBackwardPagination.Builder.create()
          .before(before)
          .limit(page.getLimit())
          .build();
    }
    final var after = page.getAfter();
    if (!isEmpty(after)) {
      return CursorForwardPagination.Builder.create().after(after).limit(page.getLimit()).build();
    }
    final var from = page.getFrom();
    if (!isEmpty(from)) {
      return OffsetPagination.Builder.create().from(from).limit(page.getLimit()).build();
    }
    return LimitPagination.Builder.create().limit(page.getLimit()).build();
  }

  private static boolean isEmpty(final @Nullable Object value) {
    if (value == null) {
      return true;
    }
    if (value instanceof final String str) {
      return str.isBlank();
    }
    return false;
  }

  public static io.camunda.gateway.protocol.model.IncidentFilter toIncidentFilter(
      final @Nullable IncidentFilter filter) {
    final var filterModel =
        io.camunda.gateway.protocol.model.IncidentFilter.Builder.create().build();
    if (filter != null) {
      ofNullable(filter.getProcessDefinitionId())
          .map(SimpleSearchQueryMapper::getStringFilter)
          .ifPresent(filterModel::processDefinitionId);
      ofNullable(filter.getErrorType())
          .map(e -> AdvancedIncidentErrorTypeFilter.Builder.create().$eq(e).build())
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
          .map(s -> AdvancedIncidentStateFilter.Builder.create().$eq(s).build())
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
      final io.camunda.gateway.protocol.model.simple.@Nullable ProcessInstanceFilter filter) {
    final var filterModel =
        io.camunda.gateway.protocol.model.ProcessInstanceFilter.Builder.create().build();
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
                                final var mapped =
                                    ProcessInstanceFilterFields.Builder.create().build();
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
        .map(state -> AdvancedProcessInstanceStateFilter.Builder.create().$eq(state).build())
        .ifPresent(target::state);

    ofNullable(source.getHasIncident()).ifPresent(target::hasIncident);

    ofNullable(source.getTenantId())
        .map(SimpleSearchQueryMapper::getStringFilter)
        .ifPresent(target::tenantId);

    ofNullable(source.getVariables())
        .filter(not(List::isEmpty))
        .map(SimpleSearchQueryMapper::mapVariableValueFilterProperties)
        .ifPresent(target::variables);

    ofNullable(source.getProcessInstanceKey())
        .map(SimpleSearchQueryMapper::getBasicStringFilter)
        .ifPresent(target::processInstanceKey);
    ofNullable(source.getParentProcessInstanceKey())
        .map(SimpleSearchQueryMapper::getBasicStringFilter)
        .ifPresent(target::parentProcessInstanceKey);
    ofNullable(source.getParentElementInstanceKey())
        .map(SimpleSearchQueryMapper::getBasicStringFilter)
        .ifPresent(target::parentElementInstanceKey);

    ofNullable(source.getBatchOperationKey())
        .map(SimpleSearchQueryMapper::getStringFilter)
        .ifPresent(target::batchOperationKey);
    ofNullable(source.getBatchOperationId())
        .map(SimpleSearchQueryMapper::getStringFilter)
        .ifPresent(target::batchOperationId);
    ofNullable(source.getErrorMessage())
        .map(SimpleSearchQueryMapper::getStringFilter)
        .ifPresent(target::errorMessage);
    ofNullable(source.getHasRetriesLeft()).ifPresent(target::hasRetriesLeft);
    ofNullable(source.getElementInstanceState())
        .map(state -> AdvancedElementInstanceStateFilter.Builder.create().$eq(state).build())
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
    if (source == null) {
      return io.camunda.gateway.protocol.model.simple.ProcessInstanceFilterFields.Builder.create()
          .build();
    }
    final var fields =
        io.camunda.gateway.protocol.model.simple.ProcessInstanceFilterFields.Builder.create()
            .build();

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
        .batchOperationKey(source.getBatchOperationKey())
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
      final io.camunda.gateway.protocol.model.simple.@Nullable ProcessDefinitionFilter filter) {
    final var filterModel =
        io.camunda.gateway.protocol.model.ProcessDefinitionFilter.Builder.create().build();
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
      final io.camunda.gateway.protocol.model.simple.@Nullable VariableFilter filter) {
    final var filterModel =
        io.camunda.gateway.protocol.model.VariableFilter.Builder.create().build();
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
          .map(SimpleSearchQueryMapper::getBasicStringFilter)
          .ifPresent(filterModel::scopeKey);
      ofNullable(filter.getProcessInstanceKey())
          .map(SimpleSearchQueryMapper::getBasicStringFilter)
          .ifPresent(filterModel::processInstanceKey);
    }
    return filterModel;
  }

  public static io.camunda.gateway.protocol.model.UserTaskFilter toUserTaskFilter(
      final io.camunda.gateway.protocol.model.simple.@Nullable UserTaskFilter filter) {
    final var filterModel =
        io.camunda.gateway.protocol.model.UserTaskFilter.Builder.create().build();
    if (filter != null) {
      ofNullable(filter.getState())
          .map(
              s ->
                  io.camunda.gateway.protocol.model.AdvancedUserTaskStateFilter.Builder.create()
                      .$eq(s)
                      .build())
          .ifPresent(filterModel::state);
      ofNullable(filter.getAssignee())
          .map(SimpleSearchQueryMapper::getStringFilter)
          .ifPresent(filterModel::assignee);
      ofNullable(filter.getPriority())
          .map(SimpleSearchQueryMapper::getIntegerFilter)
          .ifPresent(filterModel::priority);
      ofNullable(filter.getElementId()).ifPresent(filterModel::elementId);
      ofNullable(filter.getName())
          .map(SimpleSearchQueryMapper::getStringFilter)
          .ifPresent(filterModel::name);
      ofNullable(filter.getCandidateGroup())
          .map(SimpleSearchQueryMapper::getStringFilter)
          .ifPresent(filterModel::candidateGroup);
      ofNullable(filter.getCandidateUser())
          .map(SimpleSearchQueryMapper::getStringFilter)
          .ifPresent(filterModel::candidateUser);
      ofNullable(filter.getTenantId())
          .map(SimpleSearchQueryMapper::getStringFilter)
          .ifPresent(filterModel::tenantId);
      ofNullable(filter.getProcessDefinitionId())
          .map(SimpleSearchQueryMapper::getStringFilter)
          .ifPresent(filterModel::processDefinitionId);
      ofNullable(filter.getCreationDate())
          .map(SimpleSearchQueryMapper::getDateTimeFilter)
          .ifPresent(filterModel::creationDate);
      ofNullable(filter.getCompletionDate())
          .map(SimpleSearchQueryMapper::getDateTimeFilter)
          .ifPresent(filterModel::completionDate);
      ofNullable(filter.getFollowUpDate())
          .map(SimpleSearchQueryMapper::getDateTimeFilter)
          .ifPresent(filterModel::followUpDate);
      ofNullable(filter.getDueDate())
          .map(SimpleSearchQueryMapper::getDateTimeFilter)
          .ifPresent(filterModel::dueDate);
      ofNullable(filter.getProcessInstanceVariables())
          .map(SimpleSearchQueryMapper::mapVariableValueFilterProperties)
          .ifPresent(filterModel::processInstanceVariables);
      ofNullable(filter.getLocalVariables())
          .map(SimpleSearchQueryMapper::mapVariableValueFilterProperties)
          .ifPresent(filterModel::localVariables);
      ofNullable(filter.getUserTaskKey()).ifPresent(filterModel::userTaskKey);
      ofNullable(filter.getProcessDefinitionKey())
          .map(SimpleSearchQueryMapper::getBasicStringFilter)
          .ifPresent(filterModel::processDefinitionKey);
      ofNullable(filter.getProcessInstanceKey())
          .map(SimpleSearchQueryMapper::getBasicStringFilter)
          .ifPresent(filterModel::processInstanceKey);
      ofNullable(filter.getElementInstanceKey()).ifPresent(filterModel::elementInstanceKey);
      ofNullable(filter.getTags()).ifPresent(filterModel::tags);
    }
    return filterModel;
  }

  public static io.camunda.gateway.protocol.model.UserTaskVariableFilter toUserTaskVariableFilter(
      final io.camunda.gateway.protocol.model.simple.@Nullable UserTaskVariableFilter simple) {
    if (simple == null) {
      return io.camunda.gateway.protocol.model.UserTaskVariableFilter.Builder.create().build();
    }

    return io.camunda.gateway.protocol.model.UserTaskVariableFilter.Builder.create()
        .name(getStringFilter(simple.getName()))
        .build();
  }

  public static @Nullable OffsetPagination toOffsetPagination(
      final io.camunda.gateway.protocol.model.simple.@Nullable OffsetPagination simple) {
    if (simple == null) {
      return null;
    }
    return OffsetPagination.Builder.create()
        .from(simple.getFrom())
        .limit(simple.getLimit())
        .build();
  }

  private static @Nullable List<VariableValueFilterProperty> mapVariableValueFilterProperties(
      final @Nullable List<io.camunda.gateway.protocol.model.simple.VariableValueFilterProperty>
          variableFilters) {
    if (variableFilters == null) {
      return null;
    }
    return variableFilters.stream()
        .map(
            simpleVar ->
                VariableValueFilterProperty.Builder.create()
                    .name(simpleVar.getName())
                    .value(AdvancedStringFilter.Builder.create().$eq(simpleVar.getValue()).build())
                    .build())
        .toList();
  }

  private static StringFilterProperty getStringFilter(final @Nullable String value) {
    return AdvancedStringFilter.Builder.create().$eq(value).build();
  }

  private static BasicStringFilterProperty getBasicStringFilter(final @Nullable String value) {
    return BasicStringFilter.Builder.create().$eq(value).build();
  }

  private static io.camunda.gateway.protocol.model.IntegerFilterProperty getIntegerFilter(
      final @Nullable Integer value) {
    return AdvancedIntegerFilter.Builder.create().$eq(value).build();
  }

  private static io.camunda.gateway.protocol.model.@Nullable DateTimeFilterProperty
      getDateTimeFilter(final @Nullable SimpleDateTimeFilterProperty value) {
    if (value == null
        || (value.from() == null || value.from().isBlank())
            && (value.to() == null || value.to().isBlank())) {
      return null;
    }
    final var filterModel = AdvancedDateTimeFilter.Builder.create().build();
    if (value.from() != null && !value.from().isBlank()) {
      filterModel.$gte(value.from());
    }

    if (value.to() != null && !value.to().isBlank()) {
      filterModel.$lt(value.to());
    }
    return filterModel;
  }
}
