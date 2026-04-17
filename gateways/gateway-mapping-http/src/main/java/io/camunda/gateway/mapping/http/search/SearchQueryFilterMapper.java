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
import org.jspecify.annotations.Nullable;
import org.springframework.util.CollectionUtils;

public class SearchQueryFilterMapper {

  public static Either<List<String>, ProcessDefinitionStatisticsFilter>
      toProcessDefinitionStatisticsFilter(
          final long processDefinitionKey,
          final io.camunda.gateway.protocol.model.@Nullable ProcessDefinitionStatisticsFilter
              filter) {
    final List<String> validationErrors = new ArrayList<>();

    final Either<List<String>, ProcessDefinitionStatisticsFilter.Builder> builder =
        toProcessDefStatFilterFields(processDefinitionKey, filter);
    if (builder.isLeft()) {
      validationErrors.addAll(builder.getLeft());
    }

    if (filter != null) {
      Optional.ofNullable(filter.get$or())
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
          final io.camunda.gateway.protocol.model.@Nullable ProcessDefinitionStatisticsFilter
              filter) {
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
    Optional.ofNullable(filter.getProcessInstanceKey())
        .map(mapToKeyOperations("processInstanceKey", validationErrors))
        .ifPresent(builder::processInstanceKeyOperations);
    Optional.ofNullable(filter.getParentProcessInstanceKey())
        .map(mapToKeyOperations("parentProcessInstanceKey", validationErrors))
        .ifPresent(builder::parentProcessInstanceKeyOperations);
    Optional.ofNullable(filter.getParentElementInstanceKey())
        .map(mapToKeyOperations("parentElementInstanceKey", validationErrors))
        .ifPresent(builder::parentFlowNodeInstanceKeyOperations);
    Optional.ofNullable(filter.getStartDate())
        .map(mapToOffsetDateTimeOperations("startDate", validationErrors))
        .ifPresent(builder::startDateOperations);
    Optional.ofNullable(filter.getEndDate())
        .map(mapToOffsetDateTimeOperations("endDate", validationErrors))
        .ifPresent(builder::endDateOperations);
    Optional.ofNullable(filter.getState())
        .map(mapToOperations(String.class, new ProcessInstanceStateConverter()))
        .ifPresent(builder::stateOperations);
    Optional.ofNullable(filter.getHasIncident()).ifPresent(builder::hasIncident);
    Optional.ofNullable(filter.getTenantId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::tenantIdOperations);
    if (Optional.ofNullable(filter.getBatchOperationId()).isPresent()
        && Optional.ofNullable(filter.getBatchOperationKey()).isPresent()) {
      validationErrors.add(
          ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(List.of("batchOperationId", "batchOperationKey")));
    } else {
      final var batchOperationFilter =
          filter.getBatchOperationKey() != null
              ? filter.getBatchOperationKey()
              : filter.getBatchOperationId();
      Optional.ofNullable(batchOperationFilter)
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationIdOperations);
    }
    Optional.ofNullable(filter.getErrorMessage())
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorMessageOperations);
    Optional.ofNullable(filter.getHasRetriesLeft()).ifPresent(builder::hasRetriesLeft);
    Optional.ofNullable(filter.getElementId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeIdOperations);
    Optional.ofNullable(filter.getHasElementInstanceIncident())
        .ifPresent(builder::hasFlowNodeInstanceIncident);
    Optional.ofNullable(filter.getElementInstanceState())
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeInstanceStateOperations);
    Optional.ofNullable(filter.getIncidentErrorHashCode())
        .map(mapToOperations(Integer.class))
        .ifPresent(builder::incidentErrorHashCodeOperations);
    Optional.ofNullable(filter.getVariables())
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
    Optional.ofNullable(filter.getProcessInstanceKey())
        .map(mapToKeyOperations("processInstanceKey", validationErrors))
        .ifPresent(builder::processInstanceKeyOperations);
    Optional.ofNullable(filter.getParentProcessInstanceKey())
        .map(mapToKeyOperations("parentProcessInstanceKey", validationErrors))
        .ifPresent(builder::parentProcessInstanceKeyOperations);
    Optional.ofNullable(filter.getParentElementInstanceKey())
        .map(mapToKeyOperations("parentElementInstanceKey", validationErrors))
        .ifPresent(builder::parentFlowNodeInstanceKeyOperations);
    Optional.ofNullable(filter.getStartDate())
        .map(mapToOffsetDateTimeOperations("startDate", validationErrors))
        .ifPresent(builder::startDateOperations);
    Optional.ofNullable(filter.getEndDate())
        .map(mapToOffsetDateTimeOperations("endDate", validationErrors))
        .ifPresent(builder::endDateOperations);
    Optional.ofNullable(filter.getState())
        .map(mapToOperations(String.class, new ProcessInstanceStateConverter()))
        .ifPresent(builder::stateOperations);
    Optional.ofNullable(filter.getHasIncident()).ifPresent(builder::hasIncident);
    Optional.ofNullable(filter.getTenantId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::tenantIdOperations);
    if (Optional.ofNullable(filter.getBatchOperationId()).isPresent()
        && Optional.ofNullable(filter.getBatchOperationKey()).isPresent()) {
      validationErrors.add(
          ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(List.of("batchOperationId", "batchOperationKey")));
    } else {
      final var batchOperationFilter =
          filter.getBatchOperationKey() != null
              ? filter.getBatchOperationKey()
              : filter.getBatchOperationId();
      Optional.ofNullable(batchOperationFilter)
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationIdOperations);
    }
    Optional.ofNullable(filter.getErrorMessage())
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorMessageOperations);
    Optional.ofNullable(filter.getHasRetriesLeft()).ifPresent(builder::hasRetriesLeft);
    Optional.ofNullable(filter.getElementId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeIdOperations);
    Optional.ofNullable(filter.getHasElementInstanceIncident())
        .ifPresent(builder::hasFlowNodeInstanceIncident);
    Optional.ofNullable(filter.getElementInstanceState())
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeInstanceStateOperations);
    Optional.ofNullable(filter.getIncidentErrorHashCode())
        .map(mapToOperations(Integer.class))
        .ifPresent(builder::incidentErrorHashCodeOperations);
    Optional.ofNullable(filter.getVariables())
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
      final io.camunda.gateway.protocol.model.@Nullable JobFilter filter) {
    final var builder = FilterBuilders.job();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      Optional.ofNullable(filter.getJobKey())
          .map(mapToKeyOperations("jobKey", validationErrors))
          .ifPresent(builder::jobKeyOperations);
      Optional.ofNullable(filter.getType())
          .map(mapToOperations(String.class))
          .ifPresent(builder::typeOperations);
      Optional.ofNullable(filter.getWorker())
          .map(mapToOperations(String.class))
          .ifPresent(builder::workerOperations);
      Optional.ofNullable(filter.getState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::stateOperations);
      Optional.ofNullable(filter.getKind())
          .map(mapToOperations(String.class))
          .ifPresent(builder::kindOperations);
      Optional.ofNullable(filter.getListenerEventType())
          .map(mapToOperations(String.class))
          .ifPresent(builder::listenerEventTypeOperations);
      Optional.ofNullable(filter.getProcessDefinitionId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      Optional.ofNullable(filter.getProcessDefinitionKey())
          .map(mapToKeyOperations("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeyOperations);
      Optional.ofNullable(filter.getProcessInstanceKey())
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      Optional.ofNullable(filter.getElementId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::elementIdOperations);
      Optional.ofNullable(filter.getElementInstanceKey())
          .map(mapToKeyOperations("elementInstanceKey", validationErrors))
          .ifPresent(builder::elementInstanceKeyOperations);
      Optional.ofNullable(filter.getTenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
      Optional.ofNullable(filter.getDeadline())
          .map(mapToOffsetDateTimeOperations("deadline", validationErrors))
          .ifPresent(builder::deadlineOperations);
      Optional.ofNullable(filter.getDeniedReason())
          .map(mapToOperations(String.class))
          .ifPresent(builder::deniedReasonOperations);
      Optional.ofNullable(filter.getIsDenied()).ifPresent(builder::isDenied);
      Optional.ofNullable(filter.getEndTime())
          .map(mapToOffsetDateTimeOperations("endTime", validationErrors))
          .ifPresent(builder::endTimeOperations);
      Optional.ofNullable(filter.getErrorCode())
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorCodeOperations);
      Optional.ofNullable(filter.getErrorMessage())
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorMessageOperations);
      Optional.ofNullable(filter.getHasFailedWithRetriesLeft())
          .ifPresent(builder::hasFailedWithRetriesLeft);
      Optional.ofNullable(filter.getRetries())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::retriesOperations);
      Optional.ofNullable(filter.getCreationTime())
          .map(mapToOffsetDateTimeOperations("creationTime", validationErrors))
          .ifPresent(builder::creationTimeOperations);
      Optional.ofNullable(filter.getLastUpdateTime())
          .map(mapToOffsetDateTimeOperations("lastUpdateTime", validationErrors))
          .ifPresent(builder::lastUpdateTimeOperations);
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, DecisionInstanceFilter> toDecisionInstanceFilter(
      final io.camunda.gateway.protocol.model.@Nullable DecisionInstanceFilter filter) {
    final var builder = FilterBuilders.decisionInstance();
    final List<String> validationErrors = new ArrayList<>();

    if (filter != null) {
      Optional.ofNullable(filter.getDecisionEvaluationKey())
          .map(mapKeyToLong("decisionEvaluationKey", validationErrors))
          .ifPresent(builder::decisionInstanceKeys);
      Optional.ofNullable(filter.getDecisionEvaluationInstanceKey())
          .map(mapToOperations(String.class))
          .ifPresent(builder::decisionInstanceIdOperations);
      Optional.ofNullable(filter.getState())
          .map(mapToOperations(String.class, new DecisionInstanceStateConverter()))
          .ifPresent(builder::stateOperations);
      Optional.ofNullable(filter.getEvaluationFailure()).ifPresent(builder::evaluationFailures);
      Optional.ofNullable(filter.getEvaluationDate())
          .map(mapToOffsetDateTimeOperations("evaluationDate", validationErrors))
          .ifPresent(builder::evaluationDateOperations);
      Optional.ofNullable(filter.getProcessDefinitionKey())
          .map(mapKeyToLong("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeys);
      Optional.ofNullable(filter.getProcessInstanceKey())
          .map(mapKeyToLong("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeys);
      Optional.ofNullable(filter.getElementInstanceKey())
          .map(mapToKeyOperations("elementInstanceKey", validationErrors))
          .ifPresent(builder::flowNodeInstanceKeyOperations);
      Optional.ofNullable(filter.getDecisionDefinitionKey())
          .map(mapToKeyOperations("decisionDefinitionKey", validationErrors))
          .ifPresent(builder::decisionDefinitionKeyOperations);
      Optional.ofNullable(filter.getDecisionDefinitionId())
          .ifPresent(builder::decisionDefinitionIds);
      Optional.ofNullable(filter.getDecisionDefinitionName())
          .ifPresent(builder::decisionDefinitionNames);
      Optional.ofNullable(filter.getDecisionDefinitionVersion())
          .ifPresent(builder::decisionDefinitionVersions);
      Optional.ofNullable(filter.getDecisionDefinitionType())
          .map(t -> convertEnum(t, DecisionDefinitionType.class))
          .ifPresent(builder::decisionTypes);
      Optional.ofNullable(filter.getRootDecisionDefinitionKey())
          .map(mapToKeyOperations("rootDecisionDefinitionKey", validationErrors))
          .ifPresent(builder::rootDecisionDefinitionKeyOperations);
      Optional.ofNullable(filter.getDecisionRequirementsKey())
          .map(mapToKeyOperations("decisionRequirementsKey", validationErrors))
          .ifPresent(builder::decisionRequirementsKeyOperations);
      Optional.ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
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
      final io.camunda.gateway.protocol.model.@Nullable VariableFilter filter) {
    if (filter == null) {
      return Either.right(FilterBuilders.variable().build());
    }

    final var builder = FilterBuilders.variable();
    final List<String> validationErrors = new ArrayList<>();

    Optional.ofNullable(filter.getProcessInstanceKey())
        .map(mapToKeyOperations("processInstanceKey", validationErrors))
        .ifPresent(builder::processInstanceKeyOperations);
    Optional.ofNullable(filter.getScopeKey())
        .map(mapToKeyOperations("scopeKey", validationErrors))
        .ifPresent(builder::scopeKeyOperations);
    Optional.ofNullable(filter.getVariableKey())
        .map(mapToKeyOperations("variableKey", validationErrors))
        .ifPresent(builder::variableKeyOperations);
    Optional.ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
    Optional.ofNullable(filter.getIsTruncated()).ifPresent(builder::isTruncated);
    Optional.ofNullable(filter.getName())
        .map(mapToOperations(String.class))
        .ifPresent(builder::nameOperations);
    Optional.ofNullable(filter.getValue())
        .map(mapToOperations(String.class))
        .ifPresent(builder::valueOperations);

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static ClusterVariableFilter toClusterVariableFilter(
      final @Nullable ClusterVariableSearchQueryFilterRequest filter) {

    if (filter == null) {
      return FilterBuilders.clusterVariable().build();
    }

    final var builder = FilterBuilders.clusterVariable();

    Optional.ofNullable(filter.getName())
        .map(mapToOperations(String.class))
        .ifPresent(builder::nameOperations);
    Optional.ofNullable(filter.getValue())
        .map(mapToOperations(String.class))
        .ifPresent(builder::valueOperations);
    Optional.ofNullable(filter.getScope())
        .map(mapToOperations(String.class))
        .ifPresent(builder::scopeOperations);
    Optional.ofNullable(filter.getTenantId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::tenantIdOperations);
    Optional.ofNullable(filter.getIsTruncated()).ifPresent(builder::isTruncated);

    return builder.build();
  }

  static BatchOperationFilter toBatchOperationFilter(
      final io.camunda.gateway.protocol.model.@Nullable BatchOperationFilter filter) {
    final var builder = FilterBuilders.batchOperation();

    if (filter != null) {
      Optional.ofNullable(filter.getBatchOperationKey())
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationKeyOperations);
      Optional.ofNullable(filter.getState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::stateOperations);
      Optional.ofNullable(filter.getOperationType())
          .map(mapToOperations(String.class))
          .ifPresent(builder::operationTypeOperations);
      Optional.ofNullable(filter.getActorType())
          .map(io.camunda.gateway.protocol.model.AuditLogActorTypeEnum::getValue)
          .map(String::toUpperCase)
          .ifPresent(builder::actorTypes);
      Optional.ofNullable(filter.getActorId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::actorIdOperations);
    }

    return builder.build();
  }

  static Either<List<String>, io.camunda.search.filter.BatchOperationItemFilter>
      toBatchOperationItemFilter(
          final io.camunda.gateway.protocol.model.@Nullable BatchOperationItemFilter filter) {
    final var builder = FilterBuilders.batchOperationItem();
    final List<String> validationErrors = new ArrayList<>();

    if (filter != null) {
      Optional.ofNullable(filter.getBatchOperationKey())
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationKeyOperations);
      Optional.ofNullable(filter.getState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::stateOperations);
      Optional.ofNullable(filter.getItemKey())
          .map(mapToKeyOperations("itemKey", validationErrors))
          .ifPresent(builder::itemKeyOperations);
      Optional.ofNullable(filter.getProcessInstanceKey())
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      Optional.ofNullable(filter.getOperationType())
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
      final io.camunda.gateway.protocol.model.@Nullable JobTypeStatisticsFilter filter) {
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

    Optional.ofNullable(filter.getJobType())
        .map(mapToOperations(String.class))
        .ifPresent(builder::jobTypeOperations);

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  public static Either<List<String>, JobWorkerStatisticsFilter> toJobWorkerStatisticsFilter(
      final io.camunda.gateway.protocol.model.@Nullable JobWorkerStatisticsFilter filter) {
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
      final io.camunda.gateway.protocol.model.@Nullable JobTimeSeriesStatisticsFilter filter) {
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
        validateDuration(
            Optional.ofNullable(filter.getResolution()).orElse(null),
            "resolution",
            validationErrors);
    Optional.ofNullable(resolution).ifPresent(builder::resolution);

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  public static Either<List<String>, JobErrorStatisticsFilter> toJobErrorStatisticsFilter(
      final io.camunda.gateway.protocol.model.@Nullable JobErrorStatisticsFilter filter) {
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

    Optional.ofNullable(filter.getErrorCode())
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorCodeOperations);
    Optional.ofNullable(filter.getErrorMessage())
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorMessageOperations);

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, ProcessDefinitionFilter> toProcessDefinitionFilter(
      final io.camunda.gateway.protocol.model.@Nullable ProcessDefinitionFilter filter) {
    final var builder = FilterBuilders.processDefinition();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      Optional.ofNullable(filter.getIsLatestVersion()).ifPresent(builder::isLatestVersion);
      Optional.ofNullable(filter.getProcessDefinitionKey())
          .map(mapKeyToLong("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeys);
      Optional.ofNullable(filter.getName())
          .map(mapToOperations(String.class))
          .ifPresent(builder::nameOperations);
      Optional.ofNullable(filter.getResourceName()).ifPresent(builder::resourceNames);
      Optional.ofNullable(filter.getVersion()).ifPresent(builder::versions);
      Optional.ofNullable(filter.getVersionTag()).ifPresent(builder::versionTags);
      Optional.ofNullable(filter.getProcessDefinitionId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      Optional.ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
      Optional.ofNullable(filter.getHasStartForm()).ifPresent(builder::hasStartForm);
    }
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static @Nullable OffsetDateTime toOffsetDateTime(final @Nullable String text) {
    return StringUtils.isEmpty(text) ? null : OffsetDateTime.parse(text);
  }

  public static Either<List<String>, ProcessInstanceFilter> toRequiredProcessInstanceFilter(
      final io.camunda.gateway.protocol.model.@Nullable ProcessInstanceFilter filter) {
    if (filter == null) {
      return Either.left(List.of(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter")));
    }
    if (filter.equals(SearchQueryRequestMapper.EMPTY_PROCESS_INSTANCE_FILTER)) {
      return Either.left(List.of(ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted("filter criteria")));
    }
    return toProcessInstanceFilter(filter);
  }

  public static Either<List<String>, DecisionInstanceFilter> toRequiredDecisionInstanceFilter(
      final io.camunda.gateway.protocol.model.@Nullable DecisionInstanceFilter filter) {
    if (filter == null) {
      return Either.left(List.of(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter")));
    }
    if (filter.equals(SearchQueryRequestMapper.EMPTY_DECISION_INSTANCE_FILTER)) {
      return Either.left(List.of(ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted("filter criteria")));
    }
    return toDecisionInstanceFilter(filter);
  }

  public static Either<List<String>, ProcessInstanceFilter> toProcessInstanceFilter(
      final io.camunda.gateway.protocol.model.@Nullable ProcessInstanceFilter filter) {
    final List<String> validationErrors = new ArrayList<>();

    final Either<List<String>, Builder> builder = toProcessInstanceFilterFields(filter);
    if (builder.isLeft()) {
      validationErrors.addAll(builder.getLeft());
    }

    if (filter != null) {
      Optional.ofNullable(filter.get$or())
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
      final io.camunda.gateway.protocol.model.@Nullable ProcessInstanceFilter filter) {
    // ProcessInstanceFilter has the same fields as ProcessInstanceFilterFields plus extras.
    // Delegate to the ProcessInstanceFilterFields overload by creating a wrapper.
    if (filter == null) {
      return toProcessInstanceFilterFields((ProcessInstanceFilterFields) null);
    }
    final var fields = new ProcessInstanceFilterFields();
    Optional.ofNullable(filter.getStartDate()).ifPresent(fields::startDate);
    Optional.ofNullable(filter.getEndDate()).ifPresent(fields::endDate);
    Optional.ofNullable(filter.getState()).ifPresent(fields::state);
    Optional.ofNullable(filter.getHasIncident()).ifPresent(fields::hasIncident);
    Optional.ofNullable(filter.getTenantId()).ifPresent(fields::tenantId);
    Optional.ofNullable(filter.getVariables()).ifPresent(fields::variables);
    Optional.ofNullable(filter.getProcessInstanceKey()).ifPresent(fields::processInstanceKey);
    Optional.ofNullable(filter.getParentProcessInstanceKey())
        .ifPresent(fields::parentProcessInstanceKey);
    Optional.ofNullable(filter.getParentElementInstanceKey())
        .ifPresent(fields::parentElementInstanceKey);
    Optional.ofNullable(filter.getBatchOperationId()).ifPresent(fields::batchOperationId);
    Optional.ofNullable(filter.getBatchOperationKey()).ifPresent(fields::batchOperationKey);
    Optional.ofNullable(filter.getErrorMessage()).ifPresent(fields::errorMessage);
    Optional.ofNullable(filter.getHasRetriesLeft()).ifPresent(fields::hasRetriesLeft);
    Optional.ofNullable(filter.getElementInstanceState()).ifPresent(fields::elementInstanceState);
    Optional.ofNullable(filter.getElementId()).ifPresent(fields::elementId);
    Optional.ofNullable(filter.getHasElementInstanceIncident())
        .ifPresent(fields::hasElementInstanceIncident);
    Optional.ofNullable(filter.getIncidentErrorHashCode()).ifPresent(fields::incidentErrorHashCode);
    Optional.ofNullable(filter.getTags()).ifPresent(fields::tags);
    Optional.ofNullable(filter.getBusinessId()).ifPresent(fields::businessId);
    Optional.ofNullable(filter.getProcessDefinitionId()).ifPresent(fields::processDefinitionId);
    Optional.ofNullable(filter.getProcessDefinitionName()).ifPresent(fields::processDefinitionName);
    Optional.ofNullable(filter.getProcessDefinitionVersion())
        .ifPresent(fields::processDefinitionVersion);
    Optional.ofNullable(filter.getProcessDefinitionVersionTag())
        .ifPresent(fields::processDefinitionVersionTag);
    Optional.ofNullable(filter.getProcessDefinitionKey()).ifPresent(fields::processDefinitionKey);
    return toProcessInstanceFilterFields(fields);
  }

  public static Either<List<String>, Builder> toProcessInstanceFilterFields(
      final @Nullable ProcessInstanceFilterFields filter) {
    final var builder = FilterBuilders.processInstance();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      Optional.ofNullable(filter.getProcessInstanceKey())
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      Optional.ofNullable(filter.getProcessDefinitionId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      Optional.ofNullable(filter.getProcessDefinitionName())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionNameOperations);
      Optional.ofNullable(filter.getProcessDefinitionVersion())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::processDefinitionVersionOperations);
      Optional.ofNullable(filter.getProcessDefinitionVersionTag())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionVersionTagOperations);
      Optional.ofNullable(filter.getProcessDefinitionKey())
          .map(mapToKeyOperations("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeyOperations);
      Optional.ofNullable(filter.getParentProcessInstanceKey())
          .map(mapToKeyOperations("parentProcessInstanceKey", validationErrors))
          .ifPresent(builder::parentProcessInstanceKeyOperations);
      Optional.ofNullable(filter.getParentElementInstanceKey())
          .map(mapToKeyOperations("parentElementInstanceKey", validationErrors))
          .ifPresent(builder::parentFlowNodeInstanceKeyOperations);
      Optional.ofNullable(filter.getStartDate())
          .map(mapToOffsetDateTimeOperations("startDate", validationErrors))
          .ifPresent(builder::startDateOperations);
      Optional.ofNullable(filter.getEndDate())
          .map(mapToOffsetDateTimeOperations("endDate", validationErrors))
          .ifPresent(builder::endDateOperations);
      Optional.ofNullable(filter.getState())
          .map(mapToOperations(String.class, new ProcessInstanceStateConverter()))
          .ifPresent(builder::stateOperations);
      Optional.ofNullable(filter.getHasIncident()).ifPresent(builder::hasIncident);
      Optional.ofNullable(filter.getTenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
      if (Optional.ofNullable(filter.getBatchOperationId()).isPresent()
          && Optional.ofNullable(filter.getBatchOperationKey()).isPresent()) {
        validationErrors.add(
            ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(
                List.of("batchOperationId", "batchOperationKey")));
      } else {
        final var batchOperationFilter =
            filter.getBatchOperationKey() != null
                ? filter.getBatchOperationKey()
                : filter.getBatchOperationId();
        Optional.ofNullable(batchOperationFilter)
            .map(mapToOperations(String.class))
            .ifPresent(builder::batchOperationIdOperations);
      }
      Optional.ofNullable(filter.getErrorMessage())
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorMessageOperations);
      Optional.ofNullable(filter.getHasRetriesLeft()).ifPresent(builder::hasRetriesLeft);
      Optional.ofNullable(filter.getElementId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeIdOperations);
      Optional.ofNullable(filter.getHasElementInstanceIncident())
          .ifPresent(builder::hasFlowNodeInstanceIncident);
      Optional.ofNullable(filter.getElementInstanceState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeInstanceStateOperations);
      Optional.ofNullable(filter.getIncidentErrorHashCode())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::incidentErrorHashCodeOperations);

      Optional.ofNullable(filter.getTags())
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

      Optional.ofNullable(filter.getBusinessId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::businessIdOperations);

      Optional.ofNullable(filter.getVariables())
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

  static TenantFilter toTenantFilter(
      final io.camunda.gateway.protocol.model.@Nullable TenantFilter filter) {
    final var builder = FilterBuilders.tenant();
    if (filter != null) {
      Optional.ofNullable(filter.getTenantId()).ifPresent(builder::tenantId);
      Optional.ofNullable(filter.getName()).ifPresent(builder::name);
    }
    return builder.build();
  }

  static GroupFilter toGroupFilter(
      final io.camunda.gateway.protocol.model.@Nullable GroupFilter filter) {
    final var builder = FilterBuilders.group();
    if (filter != null) {
      Optional.ofNullable(filter.getGroupId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::groupIdOperations);
      Optional.ofNullable(filter.getName()).ifPresent(builder::name);
    }
    return builder.build();
  }

  static RoleFilter toRoleFilter(
      final io.camunda.gateway.protocol.model.@Nullable RoleFilter filter) {
    final var builder = FilterBuilders.role();
    if (filter != null) {
      Optional.ofNullable(filter.getRoleId()).ifPresent(builder::roleId);
      Optional.ofNullable(filter.getName()).ifPresent(builder::name);
    }
    return builder.build();
  }

  static MappingRuleFilter toMappingRuleFilter(
      final io.camunda.gateway.protocol.model.@Nullable MappingRuleFilter filter) {
    final var builder = FilterBuilders.mappingRule();
    if (filter != null) {
      Optional.ofNullable(filter.getClaimName()).ifPresent(builder::claimName);
      Optional.ofNullable(filter.getClaimValue()).ifPresent(builder::claimValue);
      Optional.ofNullable(filter.getName()).ifPresent(builder::name);
      Optional.ofNullable(filter.getMappingRuleId()).ifPresent(builder::mappingRuleId);
    }
    return builder.build();
  }

  static Either<List<String>, DecisionDefinitionFilter> toDecisionDefinitionFilter(
      final io.camunda.gateway.protocol.model.@Nullable DecisionDefinitionFilter filter) {
    final var builder = FilterBuilders.decisionDefinition();
    final List<String> validationErrors = new ArrayList<>();

    if (filter != null) {
      Optional.ofNullable(filter.getDecisionDefinitionKey())
          .map(mapKeyToLong("decisionDefinitionKey", validationErrors))
          .ifPresent(builder::decisionDefinitionKeys);
      Optional.ofNullable(filter.getDecisionDefinitionId())
          .ifPresent(builder::decisionDefinitionIds);
      Optional.ofNullable(filter.getName()).ifPresent(builder::names);
      Optional.ofNullable(filter.getIsLatestVersion()).ifPresent(builder::isLatestVersion);
      Optional.ofNullable(filter.getVersion()).ifPresent(builder::versions);
      Optional.ofNullable(filter.getDecisionRequirementsId())
          .ifPresent(builder::decisionRequirementsIds);
      Optional.ofNullable(filter.getDecisionRequirementsKey())
          .map(mapKeyToLong("decisionRequirementsKey", validationErrors))
          .ifPresent(builder::decisionRequirementsKeys);
      Optional.ofNullable(filter.getDecisionRequirementsName())
          .ifPresent(builder::decisionRequirementsNames);
      Optional.ofNullable(filter.getDecisionRequirementsVersion())
          .ifPresent(builder::decisionRequirementsVersions);
      Optional.ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, DecisionRequirementsFilter> toDecisionRequirementsFilter(
      final io.camunda.gateway.protocol.model.@Nullable DecisionRequirementsFilter filter) {
    final var builder = FilterBuilders.decisionRequirements();
    final List<String> validationErrors = new ArrayList<>();

    if (filter != null) {
      Optional.ofNullable(filter.getDecisionRequirementsKey())
          .map(mapKeyToLong("decisionRequirementsKey", validationErrors))
          .ifPresent(builder::decisionRequirementsKeys);
      Optional.ofNullable(filter.getDecisionRequirementsName()).ifPresent(builder::names);
      Optional.ofNullable(filter.getVersion()).ifPresent(builder::versions);
      Optional.ofNullable(filter.getDecisionRequirementsId())
          .ifPresent(builder::decisionRequirementsIds);
      Optional.ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
      Optional.ofNullable(filter.getResourceName()).ifPresent(builder::resourceNames);
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, FlowNodeInstanceFilter> toElementInstanceFilter(
      final io.camunda.gateway.protocol.model.@Nullable ElementInstanceFilter filter) {
    final var builder = FilterBuilders.flowNodeInstance();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      Optional.ofNullable(filter.getElementInstanceKey())
          .map(mapKeyToLong("elementInstanceKey", validationErrors))
          .ifPresent(builder::flowNodeInstanceKeys);
      Optional.ofNullable(filter.getProcessInstanceKey())
          .map(mapKeyToLong("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeys);
      Optional.ofNullable(filter.getProcessDefinitionKey())
          .map(mapKeyToLong("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeys);
      Optional.ofNullable(filter.getProcessDefinitionId()).ifPresent(builder::processDefinitionIds);
      Optional.ofNullable(filter.getState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::stateOperations);
      Optional.ofNullable(filter.getType())
          .ifPresent(t -> builder.types(FlowNodeType.fromZeebeBpmnElementType(t.getValue())));
      Optional.ofNullable(filter.getElementId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeIdOperations);
      Optional.ofNullable(filter.getElementName())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeNameOperations);
      Optional.ofNullable(filter.getHasIncident()).ifPresent(builder::hasIncident);
      Optional.ofNullable(filter.getIncidentKey())
          .map(mapKeyToLong("incidentKey", validationErrors))
          .ifPresent(builder::incidentKeys);
      Optional.ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
      Optional.ofNullable(filter.getStartDate())
          .map(mapToOffsetDateTimeOperations("startDate", validationErrors))
          .ifPresent(builder::startDateOperations);
      Optional.ofNullable(filter.getEndDate())
          .map(mapToOffsetDateTimeOperations("endDate", validationErrors))
          .ifPresent(builder::endDateOperations);
      Optional.ofNullable(filter.getElementInstanceScopeKey())
          .map(mapKeyToLong("elementInstanceScopeKey", validationErrors))
          .ifPresent(builder::elementInstanceScopeKeys);
    }
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, UserTaskFilter> toUserTaskFilter(
      final io.camunda.gateway.protocol.model.@Nullable UserTaskFilter filter) {
    final var builder = FilterBuilders.userTask();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      Optional.ofNullable(filter.getUserTaskKey())
          .map(mapKeyToLong("userTaskKey", validationErrors))
          .ifPresent(builder::userTaskKeys);
      Optional.ofNullable(filter.getState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::stateOperations);
      Optional.ofNullable(filter.getProcessDefinitionId()).ifPresent(builder::bpmnProcessIds);
      Optional.ofNullable(filter.getElementId()).ifPresent(builder::elementIds);
      Optional.ofNullable(filter.getName())
          .map(mapToOperations(String.class))
          .ifPresent(builder::nameOperations);
      Optional.ofNullable(filter.getAssignee())
          .map(mapToOperations(String.class))
          .ifPresent(builder::assigneeOperations);
      Optional.ofNullable(filter.getPriority())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::priorityOperations);
      Optional.ofNullable(filter.getCandidateGroup())
          .map(mapToOperations(String.class))
          .ifPresent(builder::candidateGroupOperations);
      Optional.ofNullable(filter.getCandidateUser())
          .map(mapToOperations(String.class))
          .ifPresent(builder::candidateUserOperations);
      Optional.ofNullable(filter.getProcessDefinitionKey())
          .map(mapKeyToLong("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeys);
      Optional.ofNullable(filter.getProcessInstanceKey())
          .map(mapKeyToLong("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeys);
      Optional.ofNullable(filter.getTenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
      Optional.ofNullable(filter.getElementInstanceKey())
          .map(mapKeyToLong("elementInstanceKey", validationErrors))
          .ifPresent(builder::elementInstanceKeys);
      Optional.ofNullable(filter.getProcessInstanceVariables())
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
      Optional.ofNullable(filter.getLocalVariables())
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
      Optional.ofNullable(filter.getCreationDate())
          .map(mapToOffsetDateTimeOperations("creationDate", validationErrors))
          .ifPresent(builder::creationDateOperations);
      Optional.ofNullable(filter.getCompletionDate())
          .map(mapToOffsetDateTimeOperations("completionDate", validationErrors))
          .ifPresent(builder::completionDateOperations);
      Optional.ofNullable(filter.getDueDate())
          .map(mapToOffsetDateTimeOperations("dueDate", validationErrors))
          .ifPresent(builder::dueDateOperations);
      Optional.ofNullable(filter.getFollowUpDate())
          .map(mapToOffsetDateTimeOperations("followUpDate", validationErrors))
          .ifPresent(builder::followUpDateOperations);

      Optional.ofNullable(filter.getTags())
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

  static UserFilter toUserFilter(
      final io.camunda.gateway.protocol.model.@Nullable UserFilter filter) {

    final var builder = FilterBuilders.user();
    if (filter != null) {
      Optional.ofNullable(filter.getUsername())
          .map(mapToOperations(String.class))
          .ifPresent(builder::usernameOperations);
      Optional.ofNullable(filter.getName())
          .map(mapToOperations(String.class))
          .ifPresent(builder::nameOperations);
      Optional.ofNullable(filter.getEmail())
          .map(mapToOperations(String.class))
          .ifPresent(builder::emailOperations);
    }
    return builder.build();
  }

  static Either<List<String>, IncidentFilter> toIncidentFilter(
      final io.camunda.gateway.protocol.model.@Nullable IncidentFilter filter) {
    final var builder = FilterBuilders.incident();
    final List<String> validationErrors = new ArrayList<>();

    if (filter != null) {
      Optional.ofNullable(filter.getIncidentKey())
          .map(mapToKeyOperations("incidentKey", validationErrors))
          .ifPresent(builder::incidentKeyOperations);
      Optional.ofNullable(filter.getProcessDefinitionKey())
          .map(mapToKeyOperations("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeyOperations);
      Optional.ofNullable(filter.getProcessDefinitionId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      Optional.ofNullable(filter.getProcessInstanceKey())
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      Optional.ofNullable(filter.getErrorType())
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorTypeOperations);
      Optional.ofNullable(filter.getErrorMessage())
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorMessageOperations);
      Optional.ofNullable(filter.getElementId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeIdOperations);
      Optional.ofNullable(filter.getElementInstanceKey())
          .map(mapToKeyOperations("elementInstanceKey", validationErrors))
          .ifPresent(builder::flowNodeInstanceKeyOperations);
      Optional.ofNullable(filter.getCreationTime())
          .map(mapToOffsetDateTimeOperations("creationTime", validationErrors))
          .ifPresent(builder::creationTimeOperations);
      Optional.ofNullable(filter.getState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::stateOperations);
      Optional.ofNullable(filter.getJobKey())
          .map(mapToKeyOperations("jobKey", validationErrors))
          .ifPresent(builder::jobKeyOperations);
      Optional.ofNullable(filter.getTenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
    }
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, MessageSubscriptionFilter> toMessageSubscriptionFilter(
      final io.camunda.gateway.protocol.model.@Nullable MessageSubscriptionFilter filter) {
    final var builder = FilterBuilders.messageSubscription();
    final List<String> validationErrors = new ArrayList<>();

    if (filter != null) {
      Optional.ofNullable(filter.getProcessDefinitionKey())
          .map(mapToKeyOperations("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeyOperations);
      Optional.ofNullable(filter.getMessageSubscriptionKey())
          .map(mapToKeyOperations("messageSubscriptionKey", validationErrors))
          .ifPresent(builder::messageSubscriptionKeyOperations);
      Optional.ofNullable(filter.getProcessDefinitionId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      Optional.ofNullable(filter.getProcessInstanceKey())
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      Optional.ofNullable(filter.getElementId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeIdOperations);
      Optional.ofNullable(filter.getElementInstanceKey())
          .map(mapToKeyOperations("elementInstanceKey", validationErrors))
          .ifPresent(builder::flowNodeInstanceKeyOperations);
      Optional.ofNullable(filter.getMessageSubscriptionState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::messageSubscriptionStateOperations);
      Optional.ofNullable(filter.getLastUpdatedDate())
          .map(mapToOffsetDateTimeOperations("lastUpdatedDate", validationErrors))
          .ifPresent(builder::dateTimeOperations);
      Optional.ofNullable(filter.getMessageName())
          .map(mapToOperations(String.class))
          .ifPresent(builder::messageNameOperations);
      Optional.ofNullable(filter.getCorrelationKey())
          .map(mapToOperations(String.class))
          .ifPresent(builder::correlationKeyOperations);
      Optional.ofNullable(filter.getTenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
    }
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, CorrelatedMessageSubscriptionFilter>
      toCorrelatedMessageSubscriptionFilter(
          final io.camunda.gateway.protocol.model.@Nullable CorrelatedMessageSubscriptionFilter
              filter) {
    final var builder = FilterBuilders.correlatedMessageSubscription();
    final List<String> validationErrors = new ArrayList<>();

    if (filter != null) {
      Optional.ofNullable(filter.getCorrelationKey())
          .map(mapToOperations(String.class))
          .ifPresent(builder::correlationKeyOperations);
      Optional.ofNullable(filter.getCorrelationTime())
          .map(mapToOffsetDateTimeOperations("correlationTime", validationErrors))
          .ifPresent(builder::correlationTimeOperations);
      Optional.ofNullable(filter.getElementId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeIdOperations);
      Optional.ofNullable(filter.getElementInstanceKey())
          .map(mapToKeyOperations("elementInstanceKey", validationErrors))
          .ifPresent(builder::flowNodeInstanceKeyOperations);
      Optional.ofNullable(filter.getMessageKey())
          .map(mapToKeyOperations("messageKey", validationErrors))
          .ifPresent(builder::messageKeyOperations);
      Optional.ofNullable(filter.getMessageName())
          .map(mapToOperations(String.class))
          .ifPresent(builder::messageNameOperations);
      Optional.ofNullable(filter.getPartitionId())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::partitionIdOperations);
      Optional.ofNullable(filter.getProcessDefinitionId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      Optional.ofNullable(filter.getProcessDefinitionKey())
          .map(mapToKeyOperations("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeyOperations);
      Optional.ofNullable(filter.getProcessInstanceKey())
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      Optional.ofNullable(filter.getSubscriptionKey())
          .map(mapToKeyOperations("subscriptionKey", validationErrors))
          .ifPresent(builder::subscriptionKeyOperations);
      Optional.ofNullable(filter.getTenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
    }
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, io.camunda.search.filter.AuditLogFilter> toAuditLogFilter(
      final io.camunda.gateway.protocol.model.@Nullable AuditLogFilter filter) {
    if (filter == null) {
      return Either.right(FilterBuilders.auditLog().build());
    }

    final var builder = FilterBuilders.auditLog();
    final List<String> validationErrors = new ArrayList<>();
    Optional.ofNullable(filter.getAuditLogKey())
        .map(mapToOperations(String.class))
        .ifPresent(builder::auditLogKeyOperations);
    Optional.ofNullable(filter.getProcessDefinitionKey())
        .map(mapToKeyOperations("processDefinitionKey", validationErrors))
        .ifPresent(builder::processDefinitionKeyOperations);
    Optional.ofNullable(filter.getProcessInstanceKey())
        .map(mapToKeyOperations("processInstanceKey", validationErrors))
        .ifPresent(builder::processInstanceKeyOperations);
    Optional.ofNullable(filter.getElementInstanceKey())
        .map(mapToKeyOperations("elementInstanceKey", validationErrors))
        .ifPresent(builder::elementInstanceKeyOperations);
    Optional.ofNullable(filter.getOperationType())
        .map(mapToOperations(String.class, new AuditLogOperationTypeConverter()))
        .ifPresent(builder::operationTypeOperations);
    Optional.ofNullable(filter.getResult())
        .map(mapToOperations(String.class, new AuditLogResultConverter()))
        .ifPresent(builder::resultOperations);
    Optional.ofNullable(filter.getTimestamp())
        .map(mapToOffsetDateTimeOperations("timestamp", validationErrors))
        .ifPresent(builder::timestampOperations);
    Optional.ofNullable(filter.getActorId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::actorIdOperations);
    Optional.ofNullable(filter.getActorType())
        .map(mapToOperations(String.class, new AuditLogActorTypeConverter()))
        .ifPresent(builder::actorTypeOperations);
    Optional.ofNullable(filter.getAgentElementId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::agentElementIdOperations);
    Optional.ofNullable(filter.getEntityType())
        .map(mapToOperations(String.class, new AuditLogEntityTypeConverter()))
        .ifPresent(builder::entityTypeOperations);
    Optional.ofNullable(filter.getEntityKey())
        .map(mapToOperations(String.class))
        .ifPresent(builder::entityKeyOperations);
    Optional.ofNullable(filter.getTenantId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::tenantIdOperations);
    Optional.ofNullable(filter.getCategory())
        .map(mapToOperations(String.class, new AuditLogCategoryConverter()))
        .ifPresent(builder::categoryOperations);
    Optional.ofNullable(filter.getDeploymentKey())
        .map(mapToKeyOperations("deploymentKey", validationErrors))
        .ifPresent(builder::deploymentKeyOperations);
    Optional.ofNullable(filter.getFormKey())
        .map(mapToKeyOperations("formKey", validationErrors))
        .ifPresent(builder::formKeyOperations);
    Optional.ofNullable(filter.getResourceKey())
        .map(mapToKeyOperations("resourceKey", validationErrors))
        .ifPresent(builder::resourceKeyOperations);
    Optional.ofNullable(filter.getBatchOperationType())
        .map(mapToOperations(String.class, new BatchOperationTypeConverter()))
        .ifPresent(builder::batchOperationTypeOperations);
    Optional.ofNullable(filter.getProcessDefinitionId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionIdOperations);
    Optional.ofNullable(filter.getJobKey())
        .map(mapToKeyOperations("jobKey", validationErrors))
        .ifPresent(builder::jobKeyOperations);
    Optional.ofNullable(filter.getUserTaskKey())
        .map(mapToKeyOperations("userTaskKey", validationErrors))
        .ifPresent(builder::userTaskKeyOperations);
    Optional.ofNullable(filter.getDecisionRequirementsId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::decisionRequirementsIdOperations);
    Optional.ofNullable(filter.getDecisionRequirementsKey())
        .map(mapToKeyOperations("decisionRequirementsKey", validationErrors))
        .ifPresent(builder::decisionRequirementsKeyOperations);
    Optional.ofNullable(filter.getDecisionDefinitionId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::decisionDefinitionIdOperations);
    Optional.ofNullable(filter.getDecisionDefinitionKey())
        .map(mapToKeyOperations("decisionDefinitionKey", validationErrors))
        .ifPresent(builder::decisionDefinitionKeyOperations);
    Optional.ofNullable(filter.getDecisionEvaluationKey())
        .map(mapToKeyOperations("decisionEvaluationKey", validationErrors))
        .ifPresent(builder::decisionEvaluationKeyOperations);
    Optional.ofNullable(filter.getRelatedEntityKey())
        .map(mapToOperations(String.class))
        .ifPresent(builder::relatedEntityKeyOperations);
    Optional.ofNullable(filter.getRelatedEntityType())
        .map(mapToOperations(String.class, new AuditLogEntityTypeConverter()))
        .ifPresent(builder::relatedEntityTypeOperations);
    Optional.ofNullable(filter.getEntityDescription())
        .map(mapToOperations(String.class))
        .ifPresent(builder::entityDescriptionOperations);
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, ProcessDefinitionInstanceVersionStatisticsFilter>
      toProcessDefinitionInstanceVersionStatisticsFilter(
          final io.camunda.gateway.protocol.model.@Nullable
              ProcessDefinitionInstanceVersionStatisticsFilter
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
    Optional.ofNullable(filter.getTenantId())
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

  static @Nullable AuthorizationFilter toAuthorizationFilter(
      final io.camunda.gateway.protocol.model.@Nullable AuthorizationFilter filter) {
    return Optional.ofNullable(filter)
        .map(
            f -> {
              final var builder = FilterBuilders.authorization();
              Optional.ofNullable(f.getOwnerId()).ifPresent(builder::ownerIds);
              Optional.ofNullable(f.getOwnerType())
                  .map(OwnerTypeEnum::getValue)
                  .ifPresent(builder::ownerType);
              Optional.ofNullable(f.getResourceIds()).ifPresent(builder::resourceIds);
              Optional.ofNullable(f.getResourcePropertyNames())
                  .ifPresent(builder::resourcePropertyNames);
              Optional.ofNullable(f.getResourceType())
                  .map(ResourceTypeEnum::getValue)
                  .ifPresent(builder::resourceType);
              return builder.build();
            })
        .orElse(null);
  }

  static Either<List<String>, AuditLogFilter> toUserTaskAuditLogFilter(
      final @Nullable UserTaskAuditLogFilter filter) {
    if (filter == null) {
      return Either.right(FilterBuilders.auditLog().build());
    }

    final var builder = FilterBuilders.auditLog();
    final List<String> validationErrors = new ArrayList<>();
    Optional.ofNullable(filter.getOperationType())
        .map(mapToOperations(String.class, new AuditLogOperationTypeConverter()))
        .ifPresent(builder::operationTypeOperations);
    Optional.ofNullable(filter.getResult())
        .map(mapToOperations(String.class))
        .ifPresent(builder::resultOperations);
    Optional.ofNullable(filter.getTimestamp())
        .map(mapToOffsetDateTimeOperations("timestamp", validationErrors))
        .ifPresent(builder::timestampOperations);
    Optional.ofNullable(filter.getActorId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::actorIdOperations);
    Optional.ofNullable(filter.getActorType())
        .map(mapToOperations(String.class, new AuditLogActorTypeConverter()))
        .ifPresent(builder::actorTypeOperations);
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static VariableFilter toUserTaskVariableFilter(final @Nullable UserTaskVariableFilter filter) {
    if (filter == null) {
      return FilterBuilders.variable().build();
    }

    final var builder = FilterBuilders.variable();
    Optional.ofNullable(filter.getName())
        .map(mapToOperations(String.class))
        .ifPresent(builder::nameOperations);

    return builder.build();
  }

  public static Either<
          List<String>,
          io.camunda.search.filter.IncidentProcessInstanceStatisticsByDefinitionFilter>
      toIncidentProcessInstanceStatisticsByDefinitionFilter(
          final @Nullable IncidentProcessInstanceStatisticsByDefinitionFilter filter) {
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
      final @Nullable GlobalTaskListenerSearchQueryFilterRequest filter) {

    final var builder =
        FilterBuilders.globalListener().listenerTypes(GlobalListenerType.USER_TASK.name());
    final List<String> validationErrors = new ArrayList<>();

    if (filter != null) {
      Optional.ofNullable(filter.getId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::listenerIdOperations);
      Optional.ofNullable(filter.getType())
          .map(mapToOperations(String.class))
          .ifPresent(builder::typeOperations);
      Optional.ofNullable(filter.getRetries())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::retriesOperations);
      Optional.ofNullable(filter.getEventTypes())
          .map(mapToOperations(String.class))
          .ifPresent(builder::eventTypeOperations);
      Optional.ofNullable(filter.getAfterNonGlobal()).ifPresent(builder::afterNonGlobal);
      Optional.ofNullable(filter.getPriority())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::priorityOperations);
      Optional.ofNullable(filter.getSource())
          .map(mapToOperations(String.class))
          .ifPresent(builder::sourceOperations);
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }
}
