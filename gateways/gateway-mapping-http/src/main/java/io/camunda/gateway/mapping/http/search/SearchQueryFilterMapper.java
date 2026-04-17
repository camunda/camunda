/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search;

import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToKeyOperations;
import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOffsetDateTimeOperations;
import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOperations;
import static io.camunda.gateway.mapping.http.util.KeyUtil.mapKeyToLong;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_AT_LEAST_ONE_FIELD;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_NULL_VARIABLE_NAME;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_NULL_VARIABLE_VALUE;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_ONLY_ONE_FIELD;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateDate;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateDuration;

import io.camunda.gateway.mapping.http.converters.AuditLogActorTypeConverter;
import io.camunda.gateway.mapping.http.converters.AuditLogCategoryConverter;
import io.camunda.gateway.mapping.http.converters.AuditLogEntityTypeConverter;
import io.camunda.gateway.mapping.http.converters.AuditLogOperationTypeConverter;
import io.camunda.gateway.mapping.http.converters.AuditLogResultConverter;
import io.camunda.gateway.mapping.http.converters.BatchOperationTypeConverter;
import io.camunda.gateway.mapping.http.converters.DecisionInstanceStateConverter;
import io.camunda.gateway.mapping.http.converters.ProcessInstanceStateConverter;
import io.camunda.gateway.mapping.http.validator.TagsValidator;
import io.camunda.gateway.protocol.model.BaseProcessInstanceFilterFields;
import io.camunda.gateway.protocol.model.ClusterVariableSearchQueryFilterRequest;
import io.camunda.gateway.protocol.model.GlobalTaskListenerSearchQueryFilterRequest;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByDefinitionFilter;
import io.camunda.gateway.protocol.model.OwnerTypeEnum;
import io.camunda.gateway.protocol.model.ProcessInstanceFilterFields;
import io.camunda.gateway.protocol.model.ResourceTypeEnum;
import io.camunda.gateway.protocol.model.StringFilterProperty;
import io.camunda.gateway.protocol.model.UserTaskAuditLogFilter;
import io.camunda.gateway.protocol.model.UserTaskVariableFilter;
import io.camunda.gateway.protocol.model.VariableValueFilterProperty;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.entities.GlobalListenerType;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.filter.AuditLogFilter;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.search.filter.BatchOperationFilter;
import io.camunda.search.filter.ClusterVariableFilter;
import io.camunda.search.filter.CorrelatedMessageSubscriptionFilter;
import io.camunda.search.filter.DecisionDefinitionFilter;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.DecisionRequirementsFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.search.filter.GlobalJobStatisticsFilter;
import io.camunda.search.filter.GlobalListenerFilter;
import io.camunda.search.filter.GroupFilter;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.filter.JobErrorStatisticsFilter;
import io.camunda.search.filter.JobFilter;
import io.camunda.search.filter.JobTimeSeriesStatisticsFilter;
import io.camunda.search.filter.JobTypeStatisticsFilter;
import io.camunda.search.filter.JobWorkerStatisticsFilter;
import io.camunda.search.filter.MappingRuleFilter;
import io.camunda.search.filter.MessageSubscriptionFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.search.filter.ProcessDefinitionInstanceVersionStatisticsFilter;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.filter.ProcessInstanceFilter.Builder;
import io.camunda.search.filter.RoleFilter;
import io.camunda.search.filter.TenantFilter;
import io.camunda.search.filter.UserFilter;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.zeebe.util.Either;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

public class SearchQueryFilterMapper {

  public static Either<List<String>, ProcessDefinitionStatisticsFilter>
      toProcessDefinitionStatisticsFilter(
          final long processDefinitionKey,
          final io.camunda.gateway.protocol.model.ProcessDefinitionStatisticsFilter filter) {
    final List<String> validationErrors = new ArrayList<>();

    final Either<List<String>, ProcessDefinitionStatisticsFilter.Builder> builder =
        toProcessDefStatFilterFields(processDefinitionKey, filter);
    if (builder.isLeft()) {
      validationErrors.addAll(builder.getLeft());
    }

    if (filter != null) {
      filter
          .get$or()
          .filter(orList -> !orList.isEmpty())
          .ifPresent(
              orList -> {
                for (final BaseProcessInstanceFilterFields or : orList) {
                  final var orBuilder = toBaseProcessInstanceFilterFields(processDefinitionKey, or);
                  if (orBuilder.isLeft()) {
                    validationErrors.addAll(orBuilder.getLeft());
                  } else if (builder.isRight()) {
                    builder.get().addOrOperation(orBuilder.get().build());
                  }
                }
              });
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.get().build())
        : Either.left(validationErrors);
  }

  private static Either<List<String>, ProcessDefinitionStatisticsFilter.Builder>
      toProcessDefStatFilterFields(
          final long processDefinitionKey,
          final io.camunda.gateway.protocol.model.ProcessDefinitionStatisticsFilter filter) {
    final var builder = FilterBuilders.processDefinitionStatisticsFilter(processDefinitionKey);
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      applyBaseProcessInstanceFilterFields(filter, builder, validationErrors);
    }
    return validationErrors.isEmpty() ? Either.right(builder) : Either.left(validationErrors);
  }

  private static Either<List<String>, ProcessDefinitionStatisticsFilter.Builder>
      toBaseProcessInstanceFilterFields(
          final long processDefinitionKey, final BaseProcessInstanceFilterFields filter) {
    final var builder = FilterBuilders.processDefinitionStatisticsFilter(processDefinitionKey);
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      applyBaseProcessInstanceFilterFields(filter, builder, validationErrors);
    }
    return validationErrors.isEmpty() ? Either.right(builder) : Either.left(validationErrors);
  }

  private static void applyBaseProcessInstanceFilterFields(
      final BaseProcessInstanceFilterFields filter,
      final ProcessDefinitionStatisticsFilter.Builder builder,
      final List<String> validationErrors) {
    filter
        .getProcessInstanceKey()
        .map(mapToKeyOperations("processInstanceKey", validationErrors))
        .ifPresent(builder::processInstanceKeyOperations);
    filter
        .getParentProcessInstanceKey()
        .map(mapToKeyOperations("parentProcessInstanceKey", validationErrors))
        .ifPresent(builder::parentProcessInstanceKeyOperations);
    filter
        .getParentElementInstanceKey()
        .map(mapToKeyOperations("parentElementInstanceKey", validationErrors))
        .ifPresent(builder::parentFlowNodeInstanceKeyOperations);
    filter
        .getStartDate()
        .map(mapToOffsetDateTimeOperations("startDate", validationErrors))
        .ifPresent(builder::startDateOperations);
    filter
        .getEndDate()
        .map(mapToOffsetDateTimeOperations("endDate", validationErrors))
        .ifPresent(builder::endDateOperations);
    filter
        .getState()
        .map(mapToOperations(String.class, new ProcessInstanceStateConverter()))
        .ifPresent(builder::stateOperations);
    filter.getHasIncident().ifPresent(builder::hasIncident);
    filter.getTenantId().map(mapToOperations(String.class)).ifPresent(builder::tenantIdOperations);
    if (filter.getBatchOperationId().isPresent() && filter.getBatchOperationKey().isPresent()) {
      validationErrors.add(
          ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(List.of("batchOperationId", "batchOperationKey")));
    } else {
      final var batchOperationFilter =
          filter.getBatchOperationKey().isPresent()
              ? filter.getBatchOperationKey()
              : filter.getBatchOperationId();
      batchOperationFilter
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationIdOperations);
    }
    filter
        .getErrorMessage()
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorMessageOperations);
    filter.getHasRetriesLeft().ifPresent(builder::hasRetriesLeft);
    filter
        .getElementId()
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeIdOperations);
    filter.getHasElementInstanceIncident().ifPresent(builder::hasFlowNodeInstanceIncident);
    filter
        .getElementInstanceState()
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeInstanceStateOperations);
    filter
        .getIncidentErrorHashCode()
        .map(mapToOperations(Integer.class))
        .ifPresent(builder::incidentErrorHashCodeOperations);
    filter
        .getVariables()
        .filter(vars -> !vars.isEmpty())
        .ifPresent(
            vars -> {
              final Either<List<String>, List<VariableValueFilter>> either =
                  toVariableValueFilters(vars);
              if (either.isLeft()) {
                validationErrors.addAll(either.getLeft());
              } else {
                builder.variables(either.get());
              }
            });
  }

  private static void applyBaseProcessInstanceFilterFields(
      final io.camunda.gateway.protocol.model.ProcessDefinitionStatisticsFilter filter,
      final ProcessDefinitionStatisticsFilter.Builder builder,
      final List<String> validationErrors) {
    filter
        .getProcessInstanceKey()
        .map(mapToKeyOperations("processInstanceKey", validationErrors))
        .ifPresent(builder::processInstanceKeyOperations);
    filter
        .getParentProcessInstanceKey()
        .map(mapToKeyOperations("parentProcessInstanceKey", validationErrors))
        .ifPresent(builder::parentProcessInstanceKeyOperations);
    filter
        .getParentElementInstanceKey()
        .map(mapToKeyOperations("parentElementInstanceKey", validationErrors))
        .ifPresent(builder::parentFlowNodeInstanceKeyOperations);
    filter
        .getStartDate()
        .map(mapToOffsetDateTimeOperations("startDate", validationErrors))
        .ifPresent(builder::startDateOperations);
    filter
        .getEndDate()
        .map(mapToOffsetDateTimeOperations("endDate", validationErrors))
        .ifPresent(builder::endDateOperations);
    filter
        .getState()
        .map(mapToOperations(String.class, new ProcessInstanceStateConverter()))
        .ifPresent(builder::stateOperations);
    filter.getHasIncident().ifPresent(builder::hasIncident);
    filter.getTenantId().map(mapToOperations(String.class)).ifPresent(builder::tenantIdOperations);
    if (filter.getBatchOperationId().isPresent() && filter.getBatchOperationKey().isPresent()) {
      validationErrors.add(
          ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(List.of("batchOperationId", "batchOperationKey")));
    } else {
      final var batchOperationFilter =
          filter.getBatchOperationKey().isPresent()
              ? filter.getBatchOperationKey()
              : filter.getBatchOperationId();
      batchOperationFilter
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationIdOperations);
    }
    filter
        .getErrorMessage()
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorMessageOperations);
    filter.getHasRetriesLeft().ifPresent(builder::hasRetriesLeft);
    filter
        .getElementId()
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeIdOperations);
    filter.getHasElementInstanceIncident().ifPresent(builder::hasFlowNodeInstanceIncident);
    filter
        .getElementInstanceState()
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeInstanceStateOperations);
    filter
        .getIncidentErrorHashCode()
        .map(mapToOperations(Integer.class))
        .ifPresent(builder::incidentErrorHashCodeOperations);
    filter
        .getVariables()
        .filter(vars -> !vars.isEmpty())
        .ifPresent(
            vars -> {
              final Either<List<String>, List<VariableValueFilter>> either =
                  toVariableValueFilters(vars);
              if (either.isLeft()) {
                validationErrors.addAll(either.getLeft());
              } else {
                builder.variables(either.get());
              }
            });
  }

  static Either<List<String>, JobFilter> toJobFilter(
      final io.camunda.gateway.protocol.model.JobFilter filter) {
    final var builder = FilterBuilders.job();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      filter
          .getJobKey()
          .map(mapToKeyOperations("jobKey", validationErrors))
          .ifPresent(builder::jobKeyOperations);
      filter.getType().map(mapToOperations(String.class)).ifPresent(builder::typeOperations);
      filter.getWorker().map(mapToOperations(String.class)).ifPresent(builder::workerOperations);
      filter.getState().map(mapToOperations(String.class)).ifPresent(builder::stateOperations);
      filter.getKind().map(mapToOperations(String.class)).ifPresent(builder::kindOperations);
      filter
          .getListenerEventType()
          .map(mapToOperations(String.class))
          .ifPresent(builder::listenerEventTypeOperations);
      filter
          .getProcessDefinitionId()
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      filter
          .getProcessDefinitionKey()
          .map(mapToKeyOperations("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeyOperations);
      filter
          .getProcessInstanceKey()
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      filter
          .getElementId()
          .map(mapToOperations(String.class))
          .ifPresent(builder::elementIdOperations);
      filter
          .getElementInstanceKey()
          .map(mapToKeyOperations("elementInstanceKey", validationErrors))
          .ifPresent(builder::elementInstanceKeyOperations);
      filter
          .getTenantId()
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
      filter
          .getDeadline()
          .map(mapToOffsetDateTimeOperations("deadline", validationErrors))
          .ifPresent(builder::deadlineOperations);
      filter
          .getDeniedReason()
          .map(mapToOperations(String.class))
          .ifPresent(builder::deniedReasonOperations);
      filter.getIsDenied().ifPresent(builder::isDenied);
      filter
          .getEndTime()
          .map(mapToOffsetDateTimeOperations("endTime", validationErrors))
          .ifPresent(builder::endTimeOperations);
      filter
          .getErrorCode()
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorCodeOperations);
      filter
          .getErrorMessage()
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorMessageOperations);
      filter.getHasFailedWithRetriesLeft().ifPresent(builder::hasFailedWithRetriesLeft);
      filter.getRetries().map(mapToOperations(Integer.class)).ifPresent(builder::retriesOperations);
      filter
          .getCreationTime()
          .map(mapToOffsetDateTimeOperations("creationTime", validationErrors))
          .ifPresent(builder::creationTimeOperations);
      filter
          .getLastUpdateTime()
          .map(mapToOffsetDateTimeOperations("lastUpdateTime", validationErrors))
          .ifPresent(builder::lastUpdateTimeOperations);
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, DecisionInstanceFilter> toDecisionInstanceFilter(
      final io.camunda.gateway.protocol.model.DecisionInstanceFilter filter) {
    final var builder = FilterBuilders.decisionInstance();
    final List<String> validationErrors = new ArrayList<>();

    if (filter != null) {
      filter
          .getDecisionEvaluationKey()
          .map(mapKeyToLong("decisionEvaluationKey", validationErrors))
          .ifPresent(builder::decisionInstanceKeys);
      filter
          .getDecisionEvaluationInstanceKey()
          .map(mapToOperations(String.class))
          .ifPresent(builder::decisionInstanceIdOperations);
      filter
          .getState()
          .map(mapToOperations(String.class, new DecisionInstanceStateConverter()))
          .ifPresent(builder::stateOperations);
      filter.getEvaluationFailure().ifPresent(builder::evaluationFailures);
      filter
          .getEvaluationDate()
          .map(mapToOffsetDateTimeOperations("evaluationDate", validationErrors))
          .ifPresent(builder::evaluationDateOperations);
      filter
          .getProcessDefinitionKey()
          .map(mapKeyToLong("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeys);
      filter
          .getProcessInstanceKey()
          .map(mapKeyToLong("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeys);
      filter
          .getElementInstanceKey()
          .map(mapToKeyOperations("elementInstanceKey", validationErrors))
          .ifPresent(builder::flowNodeInstanceKeyOperations);
      filter
          .getDecisionDefinitionKey()
          .map(mapToKeyOperations("decisionDefinitionKey", validationErrors))
          .ifPresent(builder::decisionDefinitionKeyOperations);
      filter.getDecisionDefinitionId().ifPresent(builder::decisionDefinitionIds);
      filter.getDecisionDefinitionName().ifPresent(builder::decisionDefinitionNames);
      filter.getDecisionDefinitionVersion().ifPresent(builder::decisionDefinitionVersions);
      filter
          .getDecisionDefinitionType()
          .map(t -> convertEnum(t, DecisionDefinitionType.class))
          .ifPresent(builder::decisionTypes);
      filter
          .getRootDecisionDefinitionKey()
          .map(mapToKeyOperations("rootDecisionDefinitionKey", validationErrors))
          .ifPresent(builder::rootDecisionDefinitionKeyOperations);
      filter
          .getDecisionRequirementsKey()
          .map(mapToKeyOperations("decisionRequirementsKey", validationErrors))
          .ifPresent(builder::decisionRequirementsKeyOperations);
      filter.getTenantId().ifPresent(builder::tenantIds);
    }
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  private static <Q extends Enum<Q>, E extends Enum<E>> E convertEnum(
      @NotNull final Q sourceEnum, @NotNull final Class<E> targetEnumType) {
    return Enum.valueOf(targetEnumType, sourceEnum.name());
  }

  static Either<List<String>, VariableFilter> toVariableFilter(
      final io.camunda.gateway.protocol.model.VariableFilter filter) {
    if (filter == null) {
      return Either.right(FilterBuilders.variable().build());
    }

    final var builder = FilterBuilders.variable();
    final List<String> validationErrors = new ArrayList<>();

    filter
        .getProcessInstanceKey()
        .map(mapToKeyOperations("processInstanceKey", validationErrors))
        .ifPresent(builder::processInstanceKeyOperations);
    filter
        .getScopeKey()
        .map(mapToKeyOperations("scopeKey", validationErrors))
        .ifPresent(builder::scopeKeyOperations);
    filter
        .getVariableKey()
        .map(mapToKeyOperations("variableKey", validationErrors))
        .ifPresent(builder::variableKeyOperations);
    filter.getTenantId().ifPresent(builder::tenantIds);
    filter.getIsTruncated().ifPresent(builder::isTruncated);
    filter.getName().map(mapToOperations(String.class)).ifPresent(builder::nameOperations);
    filter.getValue().map(mapToOperations(String.class)).ifPresent(builder::valueOperations);

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static ClusterVariableFilter toClusterVariableFilter(
      final ClusterVariableSearchQueryFilterRequest filter) {

    if (filter == null) {
      return FilterBuilders.clusterVariable().build();
    }

    final var builder = FilterBuilders.clusterVariable();

    filter.getName().map(mapToOperations(String.class)).ifPresent(builder::nameOperations);
    filter.getValue().map(mapToOperations(String.class)).ifPresent(builder::valueOperations);
    filter.getScope().map(mapToOperations(String.class)).ifPresent(builder::scopeOperations);
    filter.getTenantId().map(mapToOperations(String.class)).ifPresent(builder::tenantIdOperations);
    filter.getIsTruncated().ifPresent(builder::isTruncated);

    return builder.build();
  }

  static BatchOperationFilter toBatchOperationFilter(
      final io.camunda.gateway.protocol.model.BatchOperationFilter filter) {
    final var builder = FilterBuilders.batchOperation();

    if (filter != null) {
      filter
          .getBatchOperationKey()
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationKeyOperations);
      filter.getState().map(mapToOperations(String.class)).ifPresent(builder::stateOperations);
      filter
          .getOperationType()
          .map(mapToOperations(String.class))
          .ifPresent(builder::operationTypeOperations);
      filter
          .getActorType()
          .map(io.camunda.gateway.protocol.model.AuditLogActorTypeEnum::getValue)
          .map(String::toUpperCase)
          .ifPresent(builder::actorTypes);
      filter.getActorId().map(mapToOperations(String.class)).ifPresent(builder::actorIdOperations);
    }

    return builder.build();
  }

  static Either<List<String>, io.camunda.search.filter.BatchOperationItemFilter>
      toBatchOperationItemFilter(
          final io.camunda.gateway.protocol.model.BatchOperationItemFilter filter) {
    final var builder = FilterBuilders.batchOperationItem();
    final List<String> validationErrors = new ArrayList<>();

    if (filter != null) {
      filter
          .getBatchOperationKey()
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationKeyOperations);
      filter.getState().map(mapToOperations(String.class)).ifPresent(builder::stateOperations);
      filter
          .getItemKey()
          .map(mapToKeyOperations("itemKey", validationErrors))
          .ifPresent(builder::itemKeyOperations);
      filter
          .getProcessInstanceKey()
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      filter
          .getOperationType()
          .map(mapToOperations(String.class))
          .ifPresent(builder::operationTypeOperations);
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  public static Either<List<String>, GlobalJobStatisticsFilter> toGlobalJobStatisticsFilter(
      final OffsetDateTime from, final OffsetDateTime to, final String jobType) {
    final var builder = FilterBuilders.globalJobStatistics();
    final List<String> validationErrors = new ArrayList<>();

    if (from == null) {
      validationErrors.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("from"));
    } else {
      builder.from(from);
    }

    if (to == null) {
      validationErrors.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("to"));
    } else {
      builder.to(to);
    }

    Optional.ofNullable(jobType).ifPresent(builder::jobType);

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  public static Either<List<String>, JobTypeStatisticsFilter> toJobTypeStatisticsFilter(
      final io.camunda.gateway.protocol.model.JobTypeStatisticsFilter filter) {
    final var builder = FilterBuilders.jobTypeStatistics();
    final List<String> validationErrors = new ArrayList<>();

    if (filter == null) {
      validationErrors.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter"));
      return Either.left(validationErrors);
    }

    final var from = validateDate(filter.getFrom(), "from", validationErrors);
    Optional.ofNullable(from).ifPresent(builder::from);

    final var to = validateDate(filter.getTo(), "to", validationErrors);
    Optional.ofNullable(to).ifPresent(builder::to);

    filter.getJobType().map(mapToOperations(String.class)).ifPresent(builder::jobTypeOperations);

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  public static Either<List<String>, JobWorkerStatisticsFilter> toJobWorkerStatisticsFilter(
      final io.camunda.gateway.protocol.model.JobWorkerStatisticsFilter filter) {
    final var builder = FilterBuilders.jobWorkerStatistics();
    final List<String> validationErrors = new ArrayList<>();

    if (filter == null) {
      validationErrors.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter"));
      return Either.left(validationErrors);
    }

    final var from = validateDate(filter.getFrom(), "from", validationErrors);
    Optional.ofNullable(from).ifPresent(builder::from);

    final var to = validateDate(filter.getTo(), "to", validationErrors);
    Optional.ofNullable(to).ifPresent(builder::to);

    if (filter.getJobType() == null || filter.getJobType().isBlank()) {
      validationErrors.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("jobType"));
    } else {
      builder.jobType(filter.getJobType());
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  public static Either<List<String>, JobTimeSeriesStatisticsFilter> toJobTimeSeriesStatisticsFilter(
      final io.camunda.gateway.protocol.model.JobTimeSeriesStatisticsFilter filter) {
    final var builder = FilterBuilders.jobTimeSeriesStatistics();
    final List<String> validationErrors = new ArrayList<>();

    if (filter == null) {
      validationErrors.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter"));
      return Either.left(validationErrors);
    }

    final var from = validateDate(filter.getFrom(), "from", validationErrors);
    Optional.ofNullable(from).ifPresent(builder::from);

    final var to = validateDate(filter.getTo(), "to", validationErrors);
    Optional.ofNullable(to).ifPresent(builder::to);

    if (filter.getJobType() == null || filter.getJobType().isBlank()) {
      validationErrors.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("jobType"));
    } else {
      builder.jobType(filter.getJobType());
    }

    final var resolution =
        validateDuration(filter.getResolution().orElse(null), "resolution", validationErrors);
    Optional.ofNullable(resolution).ifPresent(builder::resolution);

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  public static Either<List<String>, JobErrorStatisticsFilter> toJobErrorStatisticsFilter(
      final io.camunda.gateway.protocol.model.JobErrorStatisticsFilter filter) {
    final var builder = FilterBuilders.jobErrorStatistics();
    final List<String> validationErrors = new ArrayList<>();

    if (filter == null) {
      validationErrors.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter"));
      return Either.<List<String>, JobErrorStatisticsFilter>left(validationErrors);
    }

    final var from = validateDate(filter.getFrom(), "from", validationErrors);
    Optional.ofNullable(from).ifPresent(builder::from);

    final var to = validateDate(filter.getTo(), "to", validationErrors);
    Optional.ofNullable(to).ifPresent(builder::to);

    if (filter.getJobType() == null || filter.getJobType().isBlank()) {
      validationErrors.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("jobType"));
    } else {
      builder.jobType(filter.getJobType());
    }

    filter
        .getErrorCode()
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorCodeOperations);
    filter
        .getErrorMessage()
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorMessageOperations);

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, ProcessDefinitionFilter> toProcessDefinitionFilter(
      final io.camunda.gateway.protocol.model.ProcessDefinitionFilter filter) {
    final var builder = FilterBuilders.processDefinition();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      filter.getIsLatestVersion().ifPresent(builder::isLatestVersion);
      filter
          .getProcessDefinitionKey()
          .map(mapKeyToLong("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeys);
      filter.getName().map(mapToOperations(String.class)).ifPresent(builder::nameOperations);
      filter.getResourceName().ifPresent(builder::resourceNames);
      filter.getVersion().ifPresent(builder::versions);
      filter.getVersionTag().ifPresent(builder::versionTags);
      filter
          .getProcessDefinitionId()
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      filter.getTenantId().ifPresent(builder::tenantIds);
      filter.getHasStartForm().ifPresent(builder::hasStartForm);
    }
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static OffsetDateTime toOffsetDateTime(final String text) {
    return StringUtils.isEmpty(text) ? null : OffsetDateTime.parse(text);
  }

  public static Either<List<String>, ProcessInstanceFilter> toRequiredProcessInstanceFilter(
      final io.camunda.gateway.protocol.model.ProcessInstanceFilter filter) {
    if (filter == null) {
      return Either.left(List.of(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter")));
    }
    if (filter.equals(SearchQueryRequestMapper.EMPTY_PROCESS_INSTANCE_FILTER)) {
      return Either.left(List.of(ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted("filter criteria")));
    }
    return toProcessInstanceFilter(filter);
  }

  public static Either<List<String>, DecisionInstanceFilter> toRequiredDecisionInstanceFilter(
      final io.camunda.gateway.protocol.model.DecisionInstanceFilter filter) {
    if (filter == null) {
      return Either.left(List.of(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter")));
    }
    if (filter.equals(SearchQueryRequestMapper.EMPTY_DECISION_INSTANCE_FILTER)) {
      return Either.left(List.of(ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted("filter criteria")));
    }
    return toDecisionInstanceFilter(filter);
  }

  public static Either<List<String>, ProcessInstanceFilter> toProcessInstanceFilter(
      final io.camunda.gateway.protocol.model.ProcessInstanceFilter filter) {
    final List<String> validationErrors = new ArrayList<>();

    final Either<List<String>, Builder> builder = toProcessInstanceFilterFields(filter);
    if (builder.isLeft()) {
      validationErrors.addAll(builder.getLeft());
    }

    if (filter != null) {
      filter
          .get$or()
          .filter(orList -> !orList.isEmpty())
          .ifPresent(
              orList -> {
                for (final ProcessInstanceFilterFields or : orList) {
                  final var orBuilder = toProcessInstanceFilterFields(or);
                  if (orBuilder.isLeft()) {
                    validationErrors.addAll(orBuilder.getLeft());
                  } else if (builder.isRight()) {
                    builder.get().addOrOperation(orBuilder.get().build());
                  }
                }
              });
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.get().build())
        : Either.left(validationErrors);
  }

  public static Either<List<String>, Builder> toProcessInstanceFilterFields(
      final io.camunda.gateway.protocol.model.ProcessInstanceFilter filter) {
    // ProcessInstanceFilter has the same fields as ProcessInstanceFilterFields plus extras.
    // Delegate to the ProcessInstanceFilterFields overload by creating a wrapper.
    if (filter == null) {
      return toProcessInstanceFilterFields((ProcessInstanceFilterFields) null);
    }
    final var fields = new ProcessInstanceFilterFields();
    filter.getStartDate().ifPresent(fields::startDate);
    filter.getEndDate().ifPresent(fields::endDate);
    filter.getState().ifPresent(fields::state);
    filter.getHasIncident().ifPresent(fields::hasIncident);
    filter.getTenantId().ifPresent(fields::tenantId);
    filter.getVariables().ifPresent(fields::variables);
    filter.getProcessInstanceKey().ifPresent(fields::processInstanceKey);
    filter.getParentProcessInstanceKey().ifPresent(fields::parentProcessInstanceKey);
    filter.getParentElementInstanceKey().ifPresent(fields::parentElementInstanceKey);
    filter.getBatchOperationId().ifPresent(fields::batchOperationId);
    filter.getBatchOperationKey().ifPresent(fields::batchOperationKey);
    filter.getErrorMessage().ifPresent(fields::errorMessage);
    filter.getHasRetriesLeft().ifPresent(fields::hasRetriesLeft);
    filter.getElementInstanceState().ifPresent(fields::elementInstanceState);
    filter.getElementId().ifPresent(fields::elementId);
    filter.getHasElementInstanceIncident().ifPresent(fields::hasElementInstanceIncident);
    filter.getIncidentErrorHashCode().ifPresent(fields::incidentErrorHashCode);
    filter.getTags().ifPresent(fields::tags);
    filter.getBusinessId().ifPresent(fields::businessId);
    filter.getProcessDefinitionId().ifPresent(fields::processDefinitionId);
    filter.getProcessDefinitionName().ifPresent(fields::processDefinitionName);
    filter.getProcessDefinitionVersion().ifPresent(fields::processDefinitionVersion);
    filter.getProcessDefinitionVersionTag().ifPresent(fields::processDefinitionVersionTag);
    filter.getProcessDefinitionKey().ifPresent(fields::processDefinitionKey);
    return toProcessInstanceFilterFields(fields);
  }

  public static Either<List<String>, Builder> toProcessInstanceFilterFields(
      final ProcessInstanceFilterFields filter) {
    final var builder = FilterBuilders.processInstance();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      filter
          .getProcessInstanceKey()
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      filter
          .getProcessDefinitionId()
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      filter
          .getProcessDefinitionName()
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionNameOperations);
      filter
          .getProcessDefinitionVersion()
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::processDefinitionVersionOperations);
      filter
          .getProcessDefinitionVersionTag()
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionVersionTagOperations);
      filter
          .getProcessDefinitionKey()
          .map(mapToKeyOperations("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeyOperations);
      filter
          .getParentProcessInstanceKey()
          .map(mapToKeyOperations("parentProcessInstanceKey", validationErrors))
          .ifPresent(builder::parentProcessInstanceKeyOperations);
      filter
          .getParentElementInstanceKey()
          .map(mapToKeyOperations("parentElementInstanceKey", validationErrors))
          .ifPresent(builder::parentFlowNodeInstanceKeyOperations);
      filter
          .getStartDate()
          .map(mapToOffsetDateTimeOperations("startDate", validationErrors))
          .ifPresent(builder::startDateOperations);
      filter
          .getEndDate()
          .map(mapToOffsetDateTimeOperations("endDate", validationErrors))
          .ifPresent(builder::endDateOperations);
      filter
          .getState()
          .map(mapToOperations(String.class, new ProcessInstanceStateConverter()))
          .ifPresent(builder::stateOperations);
      filter.getHasIncident().ifPresent(builder::hasIncident);
      filter
          .getTenantId()
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
      if (filter.getBatchOperationId().isPresent() && filter.getBatchOperationKey().isPresent()) {
        validationErrors.add(
            ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(
                List.of("batchOperationId", "batchOperationKey")));
      } else {
        final var batchOperationFilter =
            filter.getBatchOperationKey().isPresent()
                ? filter.getBatchOperationKey()
                : filter.getBatchOperationId();
        batchOperationFilter
            .map(mapToOperations(String.class))
            .ifPresent(builder::batchOperationIdOperations);
      }
      filter
          .getErrorMessage()
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorMessageOperations);
      filter.getHasRetriesLeft().ifPresent(builder::hasRetriesLeft);
      filter
          .getElementId()
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeIdOperations);
      filter.getHasElementInstanceIncident().ifPresent(builder::hasFlowNodeInstanceIncident);
      filter
          .getElementInstanceState()
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeInstanceStateOperations);
      filter
          .getIncidentErrorHashCode()
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::incidentErrorHashCodeOperations);

      filter
          .getTags()
          .filter(tags -> !tags.isEmpty())
          .ifPresent(
              tags -> {
                final var tagErrors = TagsValidator.validate(tags);
                if (tagErrors.isEmpty()) {
                  builder.tags(tags);
                } else {
                  validationErrors.addAll(tagErrors);
                }
              });

      filter
          .getBusinessId()
          .map(mapToOperations(String.class))
          .ifPresent(builder::businessIdOperations);

      filter
          .getVariables()
          .filter(vars -> !vars.isEmpty())
          .ifPresent(
              vars -> {
                final Either<List<String>, List<VariableValueFilter>> either =
                    toVariableValueFilters(vars);
                if (either.isLeft()) {
                  validationErrors.addAll(either.getLeft());
                } else {
                  builder.variables(either.get());
                }
              });
    }
    return validationErrors.isEmpty() ? Either.right(builder) : Either.left(validationErrors);
  }

  static TenantFilter toTenantFilter(final io.camunda.gateway.protocol.model.TenantFilter filter) {
    final var builder = FilterBuilders.tenant();
    if (filter != null) {
      filter.getTenantId().ifPresent(builder::tenantId);
      filter.getName().ifPresent(builder::name);
    }
    return builder.build();
  }

  static GroupFilter toGroupFilter(final io.camunda.gateway.protocol.model.GroupFilter filter) {
    final var builder = FilterBuilders.group();
    if (filter != null) {
      filter.getGroupId().map(mapToOperations(String.class)).ifPresent(builder::groupIdOperations);
      filter.getName().ifPresent(builder::name);
    }
    return builder.build();
  }

  static RoleFilter toRoleFilter(final io.camunda.gateway.protocol.model.RoleFilter filter) {
    final var builder = FilterBuilders.role();
    if (filter != null) {
      filter.getRoleId().ifPresent(builder::roleId);
      filter.getName().ifPresent(builder::name);
    }
    return builder.build();
  }

  static MappingRuleFilter toMappingRuleFilter(
      final io.camunda.gateway.protocol.model.MappingRuleFilter filter) {
    final var builder = FilterBuilders.mappingRule();
    if (filter != null) {
      filter.getClaimName().ifPresent(builder::claimName);
      filter.getClaimValue().ifPresent(builder::claimValue);
      filter.getName().ifPresent(builder::name);
      filter.getMappingRuleId().ifPresent(builder::mappingRuleId);
    }
    return builder.build();
  }

  static Either<List<String>, DecisionDefinitionFilter> toDecisionDefinitionFilter(
      final io.camunda.gateway.protocol.model.DecisionDefinitionFilter filter) {
    final var builder = FilterBuilders.decisionDefinition();
    final List<String> validationErrors = new ArrayList<>();

    if (filter != null) {
      filter
          .getDecisionDefinitionKey()
          .map(mapKeyToLong("decisionDefinitionKey", validationErrors))
          .ifPresent(builder::decisionDefinitionKeys);
      filter.getDecisionDefinitionId().ifPresent(builder::decisionDefinitionIds);
      filter.getName().ifPresent(builder::names);
      filter.getIsLatestVersion().ifPresent(builder::isLatestVersion);
      filter.getVersion().ifPresent(builder::versions);
      filter.getDecisionRequirementsId().ifPresent(builder::decisionRequirementsIds);
      filter
          .getDecisionRequirementsKey()
          .map(mapKeyToLong("decisionRequirementsKey", validationErrors))
          .ifPresent(builder::decisionRequirementsKeys);
      filter.getDecisionRequirementsName().ifPresent(builder::decisionRequirementsNames);
      filter.getDecisionRequirementsVersion().ifPresent(builder::decisionRequirementsVersions);
      filter.getTenantId().ifPresent(builder::tenantIds);
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, DecisionRequirementsFilter> toDecisionRequirementsFilter(
      final io.camunda.gateway.protocol.model.DecisionRequirementsFilter filter) {
    final var builder = FilterBuilders.decisionRequirements();
    final List<String> validationErrors = new ArrayList<>();

    if (filter != null) {
      filter
          .getDecisionRequirementsKey()
          .map(mapKeyToLong("decisionRequirementsKey", validationErrors))
          .ifPresent(builder::decisionRequirementsKeys);
      filter.getDecisionRequirementsName().ifPresent(builder::names);
      filter.getVersion().ifPresent(builder::versions);
      filter.getDecisionRequirementsId().ifPresent(builder::decisionRequirementsIds);
      filter.getTenantId().ifPresent(builder::tenantIds);
      filter.getResourceName().ifPresent(builder::resourceNames);
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, FlowNodeInstanceFilter> toElementInstanceFilter(
      final io.camunda.gateway.protocol.model.ElementInstanceFilter filter) {
    final var builder = FilterBuilders.flowNodeInstance();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      filter
          .getElementInstanceKey()
          .map(mapKeyToLong("elementInstanceKey", validationErrors))
          .ifPresent(builder::flowNodeInstanceKeys);
      filter
          .getProcessInstanceKey()
          .map(mapKeyToLong("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeys);
      filter
          .getProcessDefinitionKey()
          .map(mapKeyToLong("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeys);
      filter.getProcessDefinitionId().ifPresent(builder::processDefinitionIds);
      filter.getState().map(mapToOperations(String.class)).ifPresent(builder::stateOperations);
      filter
          .getType()
          .ifPresent(t -> builder.types(FlowNodeType.fromZeebeBpmnElementType(t.getValue())));
      filter
          .getElementId()
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeIdOperations);
      filter
          .getElementName()
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeNameOperations);
      filter.getHasIncident().ifPresent(builder::hasIncident);
      filter
          .getIncidentKey()
          .map(mapKeyToLong("incidentKey", validationErrors))
          .ifPresent(builder::incidentKeys);
      filter.getTenantId().ifPresent(builder::tenantIds);
      filter
          .getStartDate()
          .map(mapToOffsetDateTimeOperations("startDate", validationErrors))
          .ifPresent(builder::startDateOperations);
      filter
          .getEndDate()
          .map(mapToOffsetDateTimeOperations("endDate", validationErrors))
          .ifPresent(builder::endDateOperations);
      filter
          .getElementInstanceScopeKey()
          .map(mapKeyToLong("elementInstanceScopeKey", validationErrors))
          .ifPresent(builder::elementInstanceScopeKeys);
    }
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, UserTaskFilter> toUserTaskFilter(
      final io.camunda.gateway.protocol.model.UserTaskFilter filter) {
    final var builder = FilterBuilders.userTask();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      filter
          .getUserTaskKey()
          .map(mapKeyToLong("userTaskKey", validationErrors))
          .ifPresent(builder::userTaskKeys);
      filter.getState().map(mapToOperations(String.class)).ifPresent(builder::stateOperations);
      filter.getProcessDefinitionId().ifPresent(builder::bpmnProcessIds);
      filter.getElementId().ifPresent(builder::elementIds);
      filter.getName().map(mapToOperations(String.class)).ifPresent(builder::nameOperations);
      filter
          .getAssignee()
          .map(mapToOperations(String.class))
          .ifPresent(builder::assigneeOperations);
      filter
          .getPriority()
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::priorityOperations);
      filter
          .getCandidateGroup()
          .map(mapToOperations(String.class))
          .ifPresent(builder::candidateGroupOperations);
      filter
          .getCandidateUser()
          .map(mapToOperations(String.class))
          .ifPresent(builder::candidateUserOperations);
      filter
          .getProcessDefinitionKey()
          .map(mapKeyToLong("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeys);
      filter
          .getProcessInstanceKey()
          .map(mapKeyToLong("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeys);
      filter
          .getTenantId()
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
      filter
          .getElementInstanceKey()
          .map(mapKeyToLong("elementInstanceKey", validationErrors))
          .ifPresent(builder::elementInstanceKeys);
      filter
          .getProcessInstanceVariables()
          .filter(vars -> !vars.isEmpty())
          .ifPresent(
              vars -> {
                final Either<List<String>, List<VariableValueFilter>> either =
                    toVariableValueFilters(vars);
                if (either.isLeft()) {
                  validationErrors.addAll(either.getLeft());
                } else {
                  builder.processInstanceVariables(either.get());
                }
              });
      filter
          .getLocalVariables()
          .filter(vars -> !vars.isEmpty())
          .ifPresent(
              vars -> {
                final Either<List<String>, List<VariableValueFilter>> either =
                    toVariableValueFilters(vars);
                if (either.isLeft()) {
                  validationErrors.addAll(either.getLeft());
                } else {
                  builder.localVariables(either.get());
                }
              });
      filter
          .getCreationDate()
          .map(mapToOffsetDateTimeOperations("creationDate", validationErrors))
          .ifPresent(builder::creationDateOperations);
      filter
          .getCompletionDate()
          .map(mapToOffsetDateTimeOperations("completionDate", validationErrors))
          .ifPresent(builder::completionDateOperations);
      filter
          .getDueDate()
          .map(mapToOffsetDateTimeOperations("dueDate", validationErrors))
          .ifPresent(builder::dueDateOperations);
      filter
          .getFollowUpDate()
          .map(mapToOffsetDateTimeOperations("followUpDate", validationErrors))
          .ifPresent(builder::followUpDateOperations);

      filter
          .getTags()
          .filter(tags -> !tags.isEmpty())
          .ifPresent(
              tags -> {
                final var tagErrors = TagsValidator.validate(tags);
                if (tagErrors.isEmpty()) {
                  builder.tags(tags);
                } else {
                  validationErrors.addAll(tagErrors);
                }
              });
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static UserFilter toUserFilter(final io.camunda.gateway.protocol.model.UserFilter filter) {

    final var builder = FilterBuilders.user();
    if (filter != null) {
      filter
          .getUsername()
          .map(mapToOperations(String.class))
          .ifPresent(builder::usernameOperations);
      filter.getName().map(mapToOperations(String.class)).ifPresent(builder::nameOperations);
      filter.getEmail().map(mapToOperations(String.class)).ifPresent(builder::emailOperations);
    }
    return builder.build();
  }

  static Either<List<String>, IncidentFilter> toIncidentFilter(
      final io.camunda.gateway.protocol.model.IncidentFilter filter) {
    final var builder = FilterBuilders.incident();
    final List<String> validationErrors = new ArrayList<>();

    if (filter != null) {
      filter
          .getIncidentKey()
          .map(mapToKeyOperations("incidentKey", validationErrors))
          .ifPresent(builder::incidentKeyOperations);
      filter
          .getProcessDefinitionKey()
          .map(mapToKeyOperations("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeyOperations);
      filter
          .getProcessDefinitionId()
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      filter
          .getProcessInstanceKey()
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      filter
          .getErrorType()
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorTypeOperations);
      filter
          .getErrorMessage()
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorMessageOperations);
      filter
          .getElementId()
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeIdOperations);
      filter
          .getElementInstanceKey()
          .map(mapToKeyOperations("elementInstanceKey", validationErrors))
          .ifPresent(builder::flowNodeInstanceKeyOperations);
      filter
          .getCreationTime()
          .map(mapToOffsetDateTimeOperations("creationTime", validationErrors))
          .ifPresent(builder::creationTimeOperations);
      filter.getState().map(mapToOperations(String.class)).ifPresent(builder::stateOperations);
      filter
          .getJobKey()
          .map(mapToKeyOperations("jobKey", validationErrors))
          .ifPresent(builder::jobKeyOperations);
      filter
          .getTenantId()
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
    }
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, MessageSubscriptionFilter> toMessageSubscriptionFilter(
      final io.camunda.gateway.protocol.model.MessageSubscriptionFilter filter) {
    final var builder = FilterBuilders.messageSubscription();
    final List<String> validationErrors = new ArrayList<>();

    if (filter != null) {
      filter
          .getProcessDefinitionKey()
          .map(mapToKeyOperations("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeyOperations);
      filter
          .getMessageSubscriptionKey()
          .map(mapToKeyOperations("messageSubscriptionKey", validationErrors))
          .ifPresent(builder::messageSubscriptionKeyOperations);
      filter
          .getProcessDefinitionId()
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      filter
          .getProcessInstanceKey()
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      filter
          .getElementId()
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeIdOperations);
      filter
          .getElementInstanceKey()
          .map(mapToKeyOperations("elementInstanceKey", validationErrors))
          .ifPresent(builder::flowNodeInstanceKeyOperations);
      filter
          .getMessageSubscriptionState()
          .map(mapToOperations(String.class))
          .ifPresent(builder::messageSubscriptionStateOperations);
      filter
          .getLastUpdatedDate()
          .map(mapToOffsetDateTimeOperations("lastUpdatedDate", validationErrors))
          .ifPresent(builder::dateTimeOperations);
      filter
          .getMessageName()
          .map(mapToOperations(String.class))
          .ifPresent(builder::messageNameOperations);
      filter
          .getCorrelationKey()
          .map(mapToOperations(String.class))
          .ifPresent(builder::correlationKeyOperations);
      filter
          .getTenantId()
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
    }
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, CorrelatedMessageSubscriptionFilter>
      toCorrelatedMessageSubscriptionFilter(
          final io.camunda.gateway.protocol.model.CorrelatedMessageSubscriptionFilter filter) {
    final var builder = FilterBuilders.correlatedMessageSubscription();
    final List<String> validationErrors = new ArrayList<>();

    if (filter != null) {
      filter
          .getCorrelationKey()
          .map(mapToOperations(String.class))
          .ifPresent(builder::correlationKeyOperations);
      filter
          .getCorrelationTime()
          .map(mapToOffsetDateTimeOperations("correlationTime", validationErrors))
          .ifPresent(builder::correlationTimeOperations);
      filter
          .getElementId()
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeIdOperations);
      filter
          .getElementInstanceKey()
          .map(mapToKeyOperations("elementInstanceKey", validationErrors))
          .ifPresent(builder::flowNodeInstanceKeyOperations);
      filter
          .getMessageKey()
          .map(mapToKeyOperations("messageKey", validationErrors))
          .ifPresent(builder::messageKeyOperations);
      filter
          .getMessageName()
          .map(mapToOperations(String.class))
          .ifPresent(builder::messageNameOperations);
      filter
          .getPartitionId()
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::partitionIdOperations);
      filter
          .getProcessDefinitionId()
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      filter
          .getProcessDefinitionKey()
          .map(mapToKeyOperations("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeyOperations);
      filter
          .getProcessInstanceKey()
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      filter
          .getSubscriptionKey()
          .map(mapToKeyOperations("subscriptionKey", validationErrors))
          .ifPresent(builder::subscriptionKeyOperations);
      filter
          .getTenantId()
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
    }
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, io.camunda.search.filter.AuditLogFilter> toAuditLogFilter(
      final io.camunda.gateway.protocol.model.AuditLogFilter filter) {
    if (filter == null) {
      return Either.right(FilterBuilders.auditLog().build());
    }

    final var builder = FilterBuilders.auditLog();
    final List<String> validationErrors = new ArrayList<>();
    filter
        .getAuditLogKey()
        .map(mapToOperations(String.class))
        .ifPresent(builder::auditLogKeyOperations);
    filter
        .getProcessDefinitionKey()
        .map(mapToKeyOperations("processDefinitionKey", validationErrors))
        .ifPresent(builder::processDefinitionKeyOperations);
    filter
        .getProcessInstanceKey()
        .map(mapToKeyOperations("processInstanceKey", validationErrors))
        .ifPresent(builder::processInstanceKeyOperations);
    filter
        .getElementInstanceKey()
        .map(mapToKeyOperations("elementInstanceKey", validationErrors))
        .ifPresent(builder::elementInstanceKeyOperations);
    filter
        .getOperationType()
        .map(mapToOperations(String.class, new AuditLogOperationTypeConverter()))
        .ifPresent(builder::operationTypeOperations);
    filter
        .getResult()
        .map(mapToOperations(String.class, new AuditLogResultConverter()))
        .ifPresent(builder::resultOperations);
    filter
        .getTimestamp()
        .map(mapToOffsetDateTimeOperations("timestamp", validationErrors))
        .ifPresent(builder::timestampOperations);
    filter.getActorId().map(mapToOperations(String.class)).ifPresent(builder::actorIdOperations);
    filter
        .getActorType()
        .map(mapToOperations(String.class, new AuditLogActorTypeConverter()))
        .ifPresent(builder::actorTypeOperations);
    filter
        .getAgentElementId()
        .map(mapToOperations(String.class))
        .ifPresent(builder::agentElementIdOperations);
    filter
        .getEntityType()
        .map(mapToOperations(String.class, new AuditLogEntityTypeConverter()))
        .ifPresent(builder::entityTypeOperations);
    filter
        .getEntityKey()
        .map(mapToOperations(String.class))
        .ifPresent(builder::entityKeyOperations);
    filter.getTenantId().map(mapToOperations(String.class)).ifPresent(builder::tenantIdOperations);
    filter
        .getCategory()
        .map(mapToOperations(String.class, new AuditLogCategoryConverter()))
        .ifPresent(builder::categoryOperations);
    filter
        .getDeploymentKey()
        .map(mapToKeyOperations("deploymentKey", validationErrors))
        .ifPresent(builder::deploymentKeyOperations);
    filter
        .getFormKey()
        .map(mapToKeyOperations("formKey", validationErrors))
        .ifPresent(builder::formKeyOperations);
    filter
        .getResourceKey()
        .map(mapToKeyOperations("resourceKey", validationErrors))
        .ifPresent(builder::resourceKeyOperations);
    filter
        .getBatchOperationType()
        .map(mapToOperations(String.class, new BatchOperationTypeConverter()))
        .ifPresent(builder::batchOperationTypeOperations);
    filter
        .getProcessDefinitionId()
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionIdOperations);
    filter
        .getJobKey()
        .map(mapToKeyOperations("jobKey", validationErrors))
        .ifPresent(builder::jobKeyOperations);
    filter
        .getUserTaskKey()
        .map(mapToKeyOperations("userTaskKey", validationErrors))
        .ifPresent(builder::userTaskKeyOperations);
    filter
        .getDecisionRequirementsId()
        .map(mapToOperations(String.class))
        .ifPresent(builder::decisionRequirementsIdOperations);
    filter
        .getDecisionRequirementsKey()
        .map(mapToKeyOperations("decisionRequirementsKey", validationErrors))
        .ifPresent(builder::decisionRequirementsKeyOperations);
    filter
        .getDecisionDefinitionId()
        .map(mapToOperations(String.class))
        .ifPresent(builder::decisionDefinitionIdOperations);
    filter
        .getDecisionDefinitionKey()
        .map(mapToKeyOperations("decisionDefinitionKey", validationErrors))
        .ifPresent(builder::decisionDefinitionKeyOperations);
    filter
        .getDecisionEvaluationKey()
        .map(mapToKeyOperations("decisionEvaluationKey", validationErrors))
        .ifPresent(builder::decisionEvaluationKeyOperations);
    filter
        .getRelatedEntityKey()
        .map(mapToOperations(String.class))
        .ifPresent(builder::relatedEntityKeyOperations);
    filter
        .getRelatedEntityType()
        .map(mapToOperations(String.class, new AuditLogEntityTypeConverter()))
        .ifPresent(builder::relatedEntityTypeOperations);
    filter
        .getEntityDescription()
        .map(mapToOperations(String.class))
        .ifPresent(builder::entityDescriptionOperations);
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, ProcessDefinitionInstanceVersionStatisticsFilter>
      toProcessDefinitionInstanceVersionStatisticsFilter(
          final io.camunda.gateway.protocol.model.ProcessDefinitionInstanceVersionStatisticsFilter
              filter) {
    if (filter == null) {
      return Either.left(List.of(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter")));
    }
    if (filter.getProcessDefinitionId() == null || filter.getProcessDefinitionId().isBlank()) {
      return Either.left(
          List.of(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter.processDefinitionId")));
    }

    final var builder = FilterBuilders.processDefinitionInstanceVersionStatistics();
    builder.processDefinitionId(filter.getProcessDefinitionId());
    filter
        .getTenantId()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .ifPresent(builder::tenantId);

    return Either.right(builder.build());
  }

  private static Either<List<String>, List<VariableValueFilter>> toVariableValueFilters(
      final List<VariableValueFilterProperty> filters) {
    if (CollectionUtils.isEmpty(filters)) {
      return Either.right(List.of());
    }

    final List<String> validationErrors = new ArrayList<>();
    final List<VariableValueFilter> variableValueFilters =
        filters.stream()
            .flatMap(
                filter -> {
                  if (filter.getName() == null) {
                    validationErrors.add(ERROR_MESSAGE_NULL_VARIABLE_NAME);
                  }
                  if (filter.getValue() == null
                      || filter
                          .getValue()
                          .equals(SearchQueryRequestMapper.EMPTY_ADVANCED_STRING_FILTER)
                      || filter
                          .getValue()
                          .equals(SearchQueryRequestMapper.EMPTY_BASIC_STRING_FILTER)) {
                    validationErrors.add(ERROR_MESSAGE_NULL_VARIABLE_VALUE);
                  }
                  // if there is no validation error overall, process the filter
                  return validationErrors.isEmpty()
                      ? toVariableValueFilters(filter.getName(), filter.getValue()).stream()
                      : Stream.empty();
                })
            .toList();
    return validationErrors.isEmpty()
        ? Either.right(variableValueFilters)
        : Either.left(validationErrors);
  }

  private static List<VariableValueFilter> toVariableValueFilters(
      final String name, final StringFilterProperty value) {
    final List<Operation<String>> operations = mapToOperations(String.class).apply(value);
    return new VariableValueFilter.Builder()
        .name(name)
        .valueTypedOperations(operations)
        .buildList();
  }

  static AuthorizationFilter toAuthorizationFilter(
      final io.camunda.gateway.protocol.model.AuthorizationFilter filter) {
    return Optional.ofNullable(filter)
        .map(
            f -> {
              final var builder = FilterBuilders.authorization();
              f.getOwnerId().ifPresent(builder::ownerIds);
              f.getOwnerType().map(OwnerTypeEnum::getValue).ifPresent(builder::ownerType);
              f.getResourceIds().ifPresent(builder::resourceIds);
              f.getResourcePropertyNames().ifPresent(builder::resourcePropertyNames);
              f.getResourceType().map(ResourceTypeEnum::getValue).ifPresent(builder::resourceType);
              return builder.build();
            })
        .orElse(null);
  }

  static Either<List<String>, AuditLogFilter> toUserTaskAuditLogFilter(
      final UserTaskAuditLogFilter filter) {
    if (filter == null) {
      return Either.right(FilterBuilders.auditLog().build());
    }

    final var builder = FilterBuilders.auditLog();
    final List<String> validationErrors = new ArrayList<>();
    filter
        .getOperationType()
        .map(mapToOperations(String.class, new AuditLogOperationTypeConverter()))
        .ifPresent(builder::operationTypeOperations);
    filter.getResult().map(mapToOperations(String.class)).ifPresent(builder::resultOperations);
    filter
        .getTimestamp()
        .map(mapToOffsetDateTimeOperations("timestamp", validationErrors))
        .ifPresent(builder::timestampOperations);
    filter.getActorId().map(mapToOperations(String.class)).ifPresent(builder::actorIdOperations);
    filter
        .getActorType()
        .map(mapToOperations(String.class, new AuditLogActorTypeConverter()))
        .ifPresent(builder::actorTypeOperations);
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static VariableFilter toUserTaskVariableFilter(final UserTaskVariableFilter filter) {
    if (filter == null) {
      return FilterBuilders.variable().build();
    }

    final var builder = FilterBuilders.variable();
    filter.getName().map(mapToOperations(String.class)).ifPresent(builder::nameOperations);

    return builder.build();
  }

  public static Either<
          List<String>,
          io.camunda.search.filter.IncidentProcessInstanceStatisticsByDefinitionFilter>
      toIncidentProcessInstanceStatisticsByDefinitionFilter(
          final IncidentProcessInstanceStatisticsByDefinitionFilter filter) {
    if (filter == null) {
      return Either.left(List.of(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter")));
    }
    if (filter.equals(
        SearchQueryRequestMapper.EMPTY_INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_DEFINITION_FILTER)) {
      return Either.left(List.of(ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted("filter criteria")));
    }

    return Either.right(
        io.camunda.search.filter.FilterBuilders.incidentProcessInstanceStatisticsByDefinition(
            f -> f.state(IncidentState.ACTIVE.name()).errorHashCode(filter.getErrorHashCode())));
  }

  static Either<List<String>, GlobalListenerFilter> toGlobalTaskListenerFilter(
      final GlobalTaskListenerSearchQueryFilterRequest filter) {

    final var builder =
        FilterBuilders.globalListener().listenerTypes(GlobalListenerType.USER_TASK.name());
    final List<String> validationErrors = new ArrayList<>();

    if (filter != null) {
      filter.getId().map(mapToOperations(String.class)).ifPresent(builder::listenerIdOperations);
      filter.getType().map(mapToOperations(String.class)).ifPresent(builder::typeOperations);
      filter.getRetries().map(mapToOperations(Integer.class)).ifPresent(builder::retriesOperations);
      filter
          .getEventTypes()
          .map(mapToOperations(String.class))
          .ifPresent(builder::eventTypeOperations);
      filter.getAfterNonGlobal().ifPresent(builder::afterNonGlobal);
      filter
          .getPriority()
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::priorityOperations);
      filter.getSource().map(mapToOperations(String.class)).ifPresent(builder::sourceOperations);
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }
}
