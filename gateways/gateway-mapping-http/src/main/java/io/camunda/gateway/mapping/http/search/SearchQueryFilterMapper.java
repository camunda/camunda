/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search;

import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToIntegerOperations;
import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToKeyOperations;
import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOffsetDateTimeOperations;
import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToStringOperations;
import static io.camunda.gateway.mapping.http.util.KeyUtil.mapKeyToLong;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_AT_LEAST_ONE_FIELD;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_NULL_VARIABLE_NAME;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_NULL_VARIABLE_VALUE;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_ONLY_ONE_FIELD;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateDate;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateDuration;
import static java.util.Optional.ofNullable;

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
import io.camunda.gateway.protocol.model.GlobalExecutionListenerSearchQueryFilterRequest;
import io.camunda.gateway.protocol.model.GlobalTaskListenerSearchQueryFilterRequest;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByDefinitionFilter;
import io.camunda.gateway.protocol.model.ProcessInstanceFilterFields;
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
        toBaseProcessInstanceFilterFields(processDefinitionKey, filter);
    if (builder.isLeft()) {
      validationErrors.addAll(builder.getLeft());
    }

    if (filter != null) {
      if (filter.get$Or() != null && !filter.get$Or().isEmpty()) {
        for (final BaseProcessInstanceFilterFields or : filter.get$Or()) {
          final var orBuilder = toBaseProcessInstanceFilterFields(processDefinitionKey, or);
          if (orBuilder.isLeft()) {
            validationErrors.addAll(orBuilder.getLeft());
          } else if (builder.isRight()) {
            builder.get().addOrOperation(orBuilder.get().build());
          }
        }
      }
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.get().build())
        : Either.left(validationErrors);
  }

  private static Either<List<String>, ProcessDefinitionStatisticsFilter.Builder>
      toBaseProcessInstanceFilterFields(
          final long processDefinitionKey, final BaseProcessInstanceFilterFields filter) {
    final var builder = FilterBuilders.processDefinitionStatisticsFilter(processDefinitionKey);
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      ofNullable(filter.getProcessInstanceKey())
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.getParentProcessInstanceKey())
          .map(mapToKeyOperations("parentProcessInstanceKey", validationErrors))
          .ifPresent(builder::parentProcessInstanceKeyOperations);
      ofNullable(filter.getParentElementInstanceKey())
          .map(mapToKeyOperations("parentElementInstanceKey", validationErrors))
          .ifPresent(builder::parentFlowNodeInstanceKeyOperations);
      ofNullable(filter.getStartDate())
          .map(mapToOffsetDateTimeOperations("startDate", validationErrors))
          .ifPresent(builder::startDateOperations);
      ofNullable(filter.getEndDate())
          .map(mapToOffsetDateTimeOperations("endDate", validationErrors))
          .ifPresent(builder::endDateOperations);
      ofNullable(filter.getState())
          .map(
              mapToStringOperations("state", validationErrors, new ProcessInstanceStateConverter()))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.getHasIncident()).ifPresent(builder::hasIncident);
      ofNullable(filter.getTenantId())
          .map(mapToStringOperations())
          .ifPresent(builder::tenantIdOperations);
      if (filter.getBatchOperationId() != null && filter.getBatchOperationKey() != null) {
        validationErrors.add(
            ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(
                List.of("batchOperationId", "batchOperationKey")));
      } else {
        final var batchOperationFilter =
            filter.getBatchOperationKey() != null
                ? filter.getBatchOperationKey()
                : filter.getBatchOperationId();
        ofNullable(batchOperationFilter)
            .map(mapToStringOperations())
            .ifPresent(builder::batchOperationIdOperations);
      }
      ofNullable(filter.getErrorMessage())
          .map(mapToStringOperations())
          .ifPresent(builder::errorMessageOperations);
      ofNullable(filter.getHasRetriesLeft()).ifPresent(builder::hasRetriesLeft);
      ofNullable(filter.getElementId())
          .map(mapToStringOperations())
          .ifPresent(builder::flowNodeIdOperations);
      ofNullable(filter.getHasElementInstanceIncident())
          .ifPresent(builder::hasFlowNodeInstanceIncident);
      ofNullable(filter.getElementInstanceState())
          .map(mapToStringOperations())
          .ifPresent(builder::flowNodeInstanceStateOperations);
      ofNullable(filter.getIncidentErrorHashCode())
          .map(mapToIntegerOperations("incidentErrorHashCode", validationErrors))
          .ifPresent(builder::incidentErrorHashCodeOperations);
      if (!CollectionUtils.isEmpty(filter.getVariables())) {
        final Either<List<String>, List<VariableValueFilter>> either =
            toVariableValueFilters(filter.getVariables());
        if (either.isLeft()) {
          validationErrors.addAll(either.getLeft());
        } else {
          builder.variables(either.get());
        }
      }
    }
    return validationErrors.isEmpty() ? Either.right(builder) : Either.left(validationErrors);
  }

  static Either<List<String>, JobFilter> toJobFilter(
      final io.camunda.gateway.protocol.model.JobFilter filter) {
    final var builder = FilterBuilders.job();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      ofNullable(filter.getJobKey())
          .map(mapToKeyOperations("jobKey", validationErrors))
          .ifPresent(builder::jobKeyOperations);
      ofNullable(filter.getType()).map(mapToStringOperations()).ifPresent(builder::typeOperations);
      ofNullable(filter.getWorker())
          .map(mapToStringOperations())
          .ifPresent(builder::workerOperations);
      ofNullable(filter.getState())
          .map(mapToStringOperations())
          .ifPresent(builder::stateOperations);
      ofNullable(filter.getKind()).map(mapToStringOperations()).ifPresent(builder::kindOperations);
      ofNullable(filter.getListenerEventType())
          .map(mapToStringOperations())
          .ifPresent(builder::listenerEventTypeOperations);
      ofNullable(filter.getProcessDefinitionId())
          .map(mapToStringOperations())
          .ifPresent(builder::processDefinitionIdOperations);
      ofNullable(filter.getProcessDefinitionKey())
          .map(mapToKeyOperations("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeyOperations);
      ofNullable(filter.getProcessInstanceKey())
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.getElementId())
          .map(mapToStringOperations())
          .ifPresent(builder::elementIdOperations);
      ofNullable(filter.getElementInstanceKey())
          .map(mapToKeyOperations("elementInstanceKey", validationErrors))
          .ifPresent(builder::elementInstanceKeyOperations);
      ofNullable(filter.getTenantId())
          .map(mapToStringOperations())
          .ifPresent(builder::tenantIdOperations);
      ofNullable(filter.getDeadline())
          .map(mapToOffsetDateTimeOperations("deadline", validationErrors))
          .ifPresent(builder::deadlineOperations);
      ofNullable(filter.getDeniedReason())
          .map(mapToStringOperations())
          .ifPresent(builder::deniedReasonOperations);
      ofNullable(filter.getIsDenied()).ifPresent(builder::isDenied);
      ofNullable(filter.getEndTime())
          .map(mapToOffsetDateTimeOperations("endTime", validationErrors))
          .ifPresent(builder::endTimeOperations);
      ofNullable(filter.getErrorCode())
          .map(mapToStringOperations())
          .ifPresent(builder::errorCodeOperations);
      ofNullable(filter.getErrorMessage())
          .map(mapToStringOperations())
          .ifPresent(builder::errorMessageOperations);
      ofNullable(filter.getHasFailedWithRetriesLeft()).ifPresent(builder::hasFailedWithRetriesLeft);
      ofNullable(filter.getRetries())
          .map(mapToIntegerOperations("retries", validationErrors))
          .ifPresent(builder::retriesOperations);
      ofNullable(filter.getCreationTime())
          .map(mapToOffsetDateTimeOperations("creationTime", validationErrors))
          .ifPresent(builder::creationTimeOperations);
      ofNullable(filter.getLastUpdateTime())
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
      ofNullable(filter.getDecisionEvaluationKey())
          .map(mapKeyToLong("decisionEvaluationKey", validationErrors))
          .ifPresent(builder::decisionInstanceKeys);
      ofNullable(filter.getDecisionEvaluationInstanceKey())
          .map(mapToStringOperations())
          .ifPresent(builder::decisionInstanceIdOperations);
      ofNullable(filter.getState())
          .map(
              mapToStringOperations(
                  "state", validationErrors, new DecisionInstanceStateConverter()))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.getEvaluationFailure()).ifPresent(builder::evaluationFailures);
      ofNullable(filter.getEvaluationDate())
          .map(mapToOffsetDateTimeOperations("evaluationDate", validationErrors))
          .ifPresent(builder::evaluationDateOperations);
      ofNullable(filter.getProcessDefinitionKey())
          .map(mapKeyToLong("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeys);
      ofNullable(filter.getProcessInstanceKey())
          .map(mapKeyToLong("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeys);
      ofNullable(filter.getElementInstanceKey())
          .map(mapToKeyOperations("elementInstanceKey", validationErrors))
          .ifPresent(builder::flowNodeInstanceKeyOperations);
      ofNullable(filter.getDecisionDefinitionKey())
          .map(mapToKeyOperations("decisionDefinitionKey", validationErrors))
          .ifPresent(builder::decisionDefinitionKeyOperations);
      ofNullable(filter.getDecisionDefinitionId()).ifPresent(builder::decisionDefinitionIds);
      ofNullable(filter.getDecisionDefinitionName()).ifPresent(builder::decisionDefinitionNames);
      ofNullable(filter.getDecisionDefinitionVersion())
          .ifPresent(builder::decisionDefinitionVersions);
      ofNullable(filter.getDecisionDefinitionType())
          .map(t -> convertEnum(t, DecisionDefinitionType.class))
          .ifPresent(builder::decisionTypes);
      ofNullable(filter.getRootDecisionDefinitionKey())
          .map(mapToKeyOperations("rootDecisionDefinitionKey", validationErrors))
          .ifPresent(builder::rootDecisionDefinitionKeyOperations);
      ofNullable(filter.getDecisionRequirementsKey())
          .map(mapToKeyOperations("decisionRequirementsKey", validationErrors))
          .ifPresent(builder::decisionRequirementsKeyOperations);
      ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
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

    ofNullable(filter.getProcessInstanceKey())
        .map(mapToKeyOperations("processInstanceKey", validationErrors))
        .ifPresent(builder::processInstanceKeyOperations);
    ofNullable(filter.getScopeKey())
        .map(mapToKeyOperations("scopeKey", validationErrors))
        .ifPresent(builder::scopeKeyOperations);
    ofNullable(filter.getVariableKey())
        .map(mapToKeyOperations("variableKey", validationErrors))
        .ifPresent(builder::variableKeyOperations);
    ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
    ofNullable(filter.getIsTruncated()).ifPresent(builder::isTruncated);
    ofNullable(filter.getName()).map(mapToStringOperations()).ifPresent(builder::nameOperations);
    ofNullable(filter.getValue()).map(mapToStringOperations()).ifPresent(builder::valueOperations);

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

    ofNullable(filter.getName()).map(mapToStringOperations()).ifPresent(builder::nameOperations);
    ofNullable(filter.getValue()).map(mapToStringOperations()).ifPresent(builder::valueOperations);
    ofNullable(filter.getScope()).map(mapToStringOperations()).ifPresent(builder::scopeOperations);
    ofNullable(filter.getTenantId())
        .map(mapToStringOperations())
        .ifPresent(builder::tenantIdOperations);
    ofNullable(filter.getIsTruncated()).ifPresent(builder::isTruncated);

    return builder.build();
  }

  static BatchOperationFilter toBatchOperationFilter(
      final io.camunda.gateway.protocol.model.BatchOperationFilter filter) {
    final var builder = FilterBuilders.batchOperation();

    if (filter != null) {
      ofNullable(filter.getBatchOperationKey())
          .map(mapToStringOperations())
          .ifPresent(builder::batchOperationKeyOperations);
      ofNullable(filter.getState())
          .map(mapToStringOperations())
          .ifPresent(builder::stateOperations);
      ofNullable(filter.getOperationType())
          .map(mapToStringOperations())
          .ifPresent(builder::operationTypeOperations);
      ofNullable(filter.getActorType())
          .map(io.camunda.gateway.protocol.model.AuditLogActorTypeEnum::getValue)
          .map(String::toUpperCase)
          .ifPresent(builder::actorTypes);
      ofNullable(filter.getActorId())
          .map(mapToStringOperations())
          .ifPresent(builder::actorIdOperations);
    }

    return builder.build();
  }

  static Either<List<String>, io.camunda.search.filter.BatchOperationItemFilter>
      toBatchOperationItemFilter(
          final io.camunda.gateway.protocol.model.BatchOperationItemFilter filter) {
    final var builder = FilterBuilders.batchOperationItem();
    final List<String> validationErrors = new ArrayList<>();

    if (filter != null) {
      ofNullable(filter.getBatchOperationKey())
          .map(mapToStringOperations())
          .ifPresent(builder::batchOperationKeyOperations);
      ofNullable(filter.getState())
          .map(mapToStringOperations())
          .ifPresent(builder::stateOperations);
      ofNullable(filter.getItemKey())
          .map(mapToKeyOperations("itemKey", validationErrors))
          .ifPresent(builder::itemKeyOperations);
      ofNullable(filter.getProcessInstanceKey())
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.getOperationType())
          .map(mapToStringOperations())
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

    Optional.ofNullable(filter.getJobType())
        .map(mapToStringOperations())
        .ifPresent(builder::jobTypeOperations);

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

    final var resolution = validateDuration(filter.getResolution(), "resolution", validationErrors);
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

    Optional.ofNullable(filter.getErrorCode())
        .map(mapToStringOperations())
        .ifPresent(builder::errorCodeOperations);
    Optional.ofNullable(filter.getErrorMessage())
        .map(mapToStringOperations())
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
      ofNullable(filter.getIsLatestVersion()).ifPresent(builder::isLatestVersion);
      ofNullable(filter.getProcessDefinitionKey())
          .map(mapKeyToLong("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeys);
      ofNullable(filter.getName()).map(mapToStringOperations()).ifPresent(builder::nameOperations);
      ofNullable(filter.getResourceName()).ifPresent(builder::resourceNames);
      ofNullable(filter.getVersion()).ifPresent(builder::versions);
      ofNullable(filter.getVersionTag()).ifPresent(builder::versionTags);
      ofNullable(filter.getProcessDefinitionId())
          .map(mapToStringOperations())
          .ifPresent(builder::processDefinitionIdOperations);
      ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
      ofNullable(filter.getHasStartForm()).ifPresent(builder::hasStartForm);
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
      if (filter.get$Or() != null && !filter.get$Or().isEmpty()) {
        for (final ProcessInstanceFilterFields or : filter.get$Or()) {
          final var orBuilder = toProcessInstanceFilterFields(or);
          if (orBuilder.isLeft()) {
            validationErrors.addAll(orBuilder.getLeft());
          } else if (builder.isRight()) {
            builder.get().addOrOperation(orBuilder.get().build());
          }
        }
      }
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.get().build())
        : Either.left(validationErrors);
  }

  public static Either<List<String>, Builder> toProcessInstanceFilterFields(
      final ProcessInstanceFilterFields filter) {
    final var builder = FilterBuilders.processInstance();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      ofNullable(filter.getProcessInstanceKey())
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.getProcessDefinitionId())
          .map(mapToStringOperations())
          .ifPresent(builder::processDefinitionIdOperations);
      ofNullable(filter.getProcessDefinitionName())
          .map(mapToStringOperations())
          .ifPresent(builder::processDefinitionNameOperations);
      ofNullable(filter.getProcessDefinitionVersion())
          .map(mapToIntegerOperations("processDefinitionVersion", validationErrors))
          .ifPresent(builder::processDefinitionVersionOperations);
      ofNullable(filter.getProcessDefinitionVersionTag())
          .map(mapToStringOperations())
          .ifPresent(builder::processDefinitionVersionTagOperations);
      ofNullable(filter.getProcessDefinitionKey())
          .map(mapToKeyOperations("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeyOperations);
      ofNullable(filter.getParentProcessInstanceKey())
          .map(mapToKeyOperations("parentProcessInstanceKey", validationErrors))
          .ifPresent(builder::parentProcessInstanceKeyOperations);
      ofNullable(filter.getParentElementInstanceKey())
          .map(mapToKeyOperations("parentElementInstanceKey", validationErrors))
          .ifPresent(builder::parentFlowNodeInstanceKeyOperations);
      ofNullable(filter.getStartDate())
          .map(mapToOffsetDateTimeOperations("startDate", validationErrors))
          .ifPresent(builder::startDateOperations);
      ofNullable(filter.getEndDate())
          .map(mapToOffsetDateTimeOperations("endDate", validationErrors))
          .ifPresent(builder::endDateOperations);
      ofNullable(filter.getState())
          .map(
              mapToStringOperations("state", validationErrors, new ProcessInstanceStateConverter()))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.getHasIncident()).ifPresent(builder::hasIncident);
      ofNullable(filter.getTenantId())
          .map(mapToStringOperations())
          .ifPresent(builder::tenantIdOperations);
      if (filter.getBatchOperationId() != null && filter.getBatchOperationKey() != null) {
        validationErrors.add(
            ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(
                List.of("batchOperationId", "batchOperationKey")));
      } else {
        final var batchOperationFilter =
            filter.getBatchOperationKey() != null
                ? filter.getBatchOperationKey()
                : filter.getBatchOperationId();
        ofNullable(batchOperationFilter)
            .map(mapToStringOperations())
            .ifPresent(builder::batchOperationIdOperations);
      }
      ofNullable(filter.getErrorMessage())
          .map(mapToStringOperations())
          .ifPresent(builder::errorMessageOperations);
      ofNullable(filter.getHasRetriesLeft()).ifPresent(builder::hasRetriesLeft);
      ofNullable(filter.getElementId())
          .map(mapToStringOperations())
          .ifPresent(builder::flowNodeIdOperations);
      ofNullable(filter.getHasElementInstanceIncident())
          .ifPresent(builder::hasFlowNodeInstanceIncident);
      ofNullable(filter.getElementInstanceState())
          .map(mapToStringOperations())
          .ifPresent(builder::flowNodeInstanceStateOperations);
      ofNullable(filter.getIncidentErrorHashCode())
          .map(mapToIntegerOperations("incidentErrorHashCode", validationErrors))
          .ifPresent(builder::incidentErrorHashCodeOperations);

      if (!CollectionUtils.isEmpty(filter.getTags())) {
        final var tagErrors = TagsValidator.validate(filter.getTags());
        if (tagErrors.isEmpty()) {
          ofNullable(filter.getTags()).ifPresent(builder::tags);
        } else {
          validationErrors.addAll(tagErrors);
        }
      }

      ofNullable(filter.getBusinessId())
          .map(mapToStringOperations())
          .ifPresent(builder::businessIdOperations);

      if (!CollectionUtils.isEmpty(filter.getVariables())) {
        final Either<List<String>, List<VariableValueFilter>> either =
            toVariableValueFilters(filter.getVariables());
        if (either.isLeft()) {
          validationErrors.addAll(either.getLeft());
        } else {
          builder.variables(either.get());
        }
      }
    }
    return validationErrors.isEmpty() ? Either.right(builder) : Either.left(validationErrors);
  }

  static TenantFilter toTenantFilter(final io.camunda.gateway.protocol.model.TenantFilter filter) {
    final var builder = FilterBuilders.tenant();
    if (filter != null) {
      ofNullable(filter.getTenantId()).ifPresent(builder::tenantId);
      ofNullable(filter.getName()).ifPresent(builder::name);
    }
    return builder.build();
  }

  static GroupFilter toGroupFilter(final io.camunda.gateway.protocol.model.GroupFilter filter) {
    final var builder = FilterBuilders.group();
    if (filter != null) {
      ofNullable(filter.getGroupId())
          .map(mapToStringOperations())
          .ifPresent(builder::groupIdOperations);
      ofNullable(filter.getName()).ifPresent(builder::name);
    }
    return builder.build();
  }

  static RoleFilter toRoleFilter(final io.camunda.gateway.protocol.model.RoleFilter filter) {
    final var builder = FilterBuilders.role();
    if (filter != null) {
      ofNullable(filter.getRoleId()).ifPresent(builder::roleId);
      ofNullable(filter.getName()).ifPresent(builder::name);
    }
    return builder.build();
  }

  static MappingRuleFilter toMappingRuleFilter(
      final io.camunda.gateway.protocol.model.MappingRuleFilter filter) {
    final var builder = FilterBuilders.mappingRule();
    if (filter != null) {
      ofNullable(filter.getClaimName()).ifPresent(builder::claimName);
      ofNullable(filter.getClaimValue()).ifPresent(builder::claimValue);
      ofNullable(filter.getName()).ifPresent(builder::name);
      ofNullable(filter.getMappingRuleId()).ifPresent(builder::mappingRuleId);
    }
    return builder.build();
  }

  static Either<List<String>, DecisionDefinitionFilter> toDecisionDefinitionFilter(
      final io.camunda.gateway.protocol.model.DecisionDefinitionFilter filter) {
    final var builder = FilterBuilders.decisionDefinition();
    final List<String> validationErrors = new ArrayList<>();

    if (filter != null) {
      ofNullable(filter.getDecisionDefinitionKey())
          .map(mapKeyToLong("decisionDefinitionKey", validationErrors))
          .ifPresent(builder::decisionDefinitionKeys);
      ofNullable(filter.getDecisionDefinitionId()).ifPresent(builder::decisionDefinitionIds);
      ofNullable(filter.getName()).ifPresent(builder::names);
      ofNullable(filter.getIsLatestVersion()).ifPresent(builder::isLatestVersion);
      ofNullable(filter.getVersion()).ifPresent(builder::versions);
      ofNullable(filter.getDecisionRequirementsId()).ifPresent(builder::decisionRequirementsIds);
      ofNullable(filter.getDecisionRequirementsKey())
          .map(mapKeyToLong("decisionRequirementsKey", validationErrors))
          .ifPresent(builder::decisionRequirementsKeys);
      ofNullable(filter.getDecisionRequirementsName())
          .ifPresent(builder::decisionRequirementsNames);
      ofNullable(filter.getDecisionRequirementsVersion())
          .ifPresent(builder::decisionRequirementsVersions);
      ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
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
      ofNullable(filter.getDecisionRequirementsKey())
          .map(mapKeyToLong("decisionRequirementsKey", validationErrors))
          .ifPresent(builder::decisionRequirementsKeys);
      ofNullable(filter.getDecisionRequirementsName()).ifPresent(builder::names);
      ofNullable(filter.getVersion()).ifPresent(builder::versions);
      ofNullable(filter.getDecisionRequirementsId()).ifPresent(builder::decisionRequirementsIds);
      ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
      ofNullable(filter.getResourceName()).ifPresent(builder::resourceNames);
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
          .map(mapToStringOperations())
          .ifPresent(builder::stateOperations);
      Optional.ofNullable(filter.getType())
          .ifPresent(t -> builder.types(FlowNodeType.fromZeebeBpmnElementType(t.getValue())));
      Optional.ofNullable(filter.getElementId()).ifPresent(builder::flowNodeIds);
      Optional.ofNullable(filter.getElementName()).ifPresent(builder::flowNodeNames);
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
      final io.camunda.gateway.protocol.model.UserTaskFilter filter) {
    final var builder = FilterBuilders.userTask();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      Optional.ofNullable(filter.getUserTaskKey())
          .map(mapKeyToLong("userTaskKey", validationErrors))
          .ifPresent(builder::userTaskKeys);
      Optional.ofNullable(filter.getState())
          .map(mapToStringOperations())
          .ifPresent(builder::stateOperations);
      Optional.ofNullable(filter.getProcessDefinitionId()).ifPresent(builder::bpmnProcessIds);
      Optional.ofNullable(filter.getElementId()).ifPresent(builder::elementIds);
      Optional.ofNullable(filter.getName())
          .map(mapToStringOperations())
          .ifPresent(builder::nameOperations);
      Optional.ofNullable(filter.getAssignee())
          .map(mapToStringOperations())
          .ifPresent(builder::assigneeOperations);
      Optional.ofNullable(filter.getPriority())
          .map(mapToIntegerOperations("priority", validationErrors))
          .ifPresent(builder::priorityOperations);
      Optional.ofNullable(filter.getCandidateGroup())
          .map(mapToStringOperations())
          .ifPresent(builder::candidateGroupOperations);
      Optional.ofNullable(filter.getCandidateUser())
          .map(mapToStringOperations())
          .ifPresent(builder::candidateUserOperations);
      Optional.ofNullable(filter.getProcessDefinitionKey())
          .map(mapKeyToLong("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeys);
      Optional.ofNullable(filter.getProcessInstanceKey())
          .map(mapKeyToLong("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeys);
      Optional.ofNullable(filter.getTenantId())
          .map(mapToStringOperations())
          .ifPresent(builder::tenantIdOperations);
      Optional.ofNullable(filter.getElementInstanceKey())
          .map(mapKeyToLong("elementInstanceKey", validationErrors))
          .ifPresent(builder::elementInstanceKeys);
      if (!CollectionUtils.isEmpty(filter.getProcessInstanceVariables())) {
        final Either<List<String>, List<VariableValueFilter>> either =
            toVariableValueFilters(filter.getProcessInstanceVariables());
        if (either.isLeft()) {
          validationErrors.addAll(either.getLeft());
        } else {
          builder.processInstanceVariables(either.get());
        }
      }
      if (!CollectionUtils.isEmpty(filter.getLocalVariables())) {
        final Either<List<String>, List<VariableValueFilter>> either =
            toVariableValueFilters(filter.getLocalVariables());
        if (either.isLeft()) {
          validationErrors.addAll(either.getLeft());
        } else {
          builder.localVariables(either.get());
        }
      }
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

      if (!CollectionUtils.isEmpty(filter.getTags())) {
        final var tagErrors = TagsValidator.validate(filter.getTags());
        if (tagErrors.isEmpty()) {
          ofNullable(filter.getTags()).ifPresent(builder::tags);
        } else {
          validationErrors.addAll(tagErrors);
        }
      }
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static UserFilter toUserFilter(final io.camunda.gateway.protocol.model.UserFilter filter) {

    final var builder = FilterBuilders.user();
    if (filter != null) {
      Optional.ofNullable(filter.getUsername())
          .map(mapToStringOperations())
          .ifPresent(builder::usernameOperations);
      Optional.ofNullable(filter.getName())
          .map(mapToStringOperations())
          .ifPresent(builder::nameOperations);
      Optional.ofNullable(filter.getEmail())
          .map(mapToStringOperations())
          .ifPresent(builder::emailOperations);
    }
    return builder.build();
  }

  static Either<List<String>, IncidentFilter> toIncidentFilter(
      final io.camunda.gateway.protocol.model.IncidentFilter filter) {
    final var builder = FilterBuilders.incident();
    final List<String> validationErrors = new ArrayList<>();

    if (filter != null) {
      ofNullable(filter.getIncidentKey())
          .map(mapToKeyOperations("incidentKey", validationErrors))
          .ifPresent(builder::incidentKeyOperations);
      ofNullable(filter.getProcessDefinitionKey())
          .map(mapToKeyOperations("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeyOperations);
      ofNullable(filter.getProcessDefinitionId())
          .map(mapToStringOperations())
          .ifPresent(builder::processDefinitionIdOperations);
      ofNullable(filter.getProcessInstanceKey())
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.getErrorType())
          .map(mapToStringOperations())
          .ifPresent(builder::errorTypeOperations);
      ofNullable(filter.getErrorMessage())
          .map(mapToStringOperations())
          .ifPresent(builder::errorMessageOperations);
      ofNullable(filter.getElementId())
          .map(mapToStringOperations())
          .ifPresent(builder::flowNodeIdOperations);
      ofNullable(filter.getElementInstanceKey())
          .map(mapToKeyOperations("elementInstanceKey", validationErrors))
          .ifPresent(builder::flowNodeInstanceKeyOperations);
      ofNullable(filter.getCreationTime())
          .map(mapToOffsetDateTimeOperations("creationTime", validationErrors))
          .ifPresent(builder::creationTimeOperations);
      ofNullable(filter.getState())
          .map(mapToStringOperations())
          .ifPresent(builder::stateOperations);
      ofNullable(filter.getJobKey())
          .map(mapToKeyOperations("jobKey", validationErrors))
          .ifPresent(builder::jobKeyOperations);
      ofNullable(filter.getTenantId())
          .map(mapToStringOperations())
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
      ofNullable(filter.getProcessDefinitionKey())
          .map(mapToKeyOperations("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeyOperations);
      ofNullable(filter.getMessageSubscriptionKey())
          .map(mapToKeyOperations("messageSubscriptionKey", validationErrors))
          .ifPresent(builder::messageSubscriptionKeyOperations);
      ofNullable(filter.getProcessDefinitionId())
          .map(mapToStringOperations())
          .ifPresent(builder::processDefinitionIdOperations);
      ofNullable(filter.getProcessInstanceKey())
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.getElementId())
          .map(mapToStringOperations())
          .ifPresent(builder::flowNodeIdOperations);
      ofNullable(filter.getElementInstanceKey())
          .map(mapToKeyOperations("elementInstanceKey", validationErrors))
          .ifPresent(builder::flowNodeInstanceKeyOperations);
      ofNullable(filter.getMessageSubscriptionState())
          .map(mapToStringOperations())
          .ifPresent(builder::messageSubscriptionStateOperations);
      ofNullable(filter.getLastUpdatedDate())
          .map(mapToOffsetDateTimeOperations("lastUpdatedDate", validationErrors))
          .ifPresent(builder::dateTimeOperations);
      ofNullable(filter.getMessageName())
          .map(mapToStringOperations())
          .ifPresent(builder::messageNameOperations);
      ofNullable(filter.getCorrelationKey())
          .map(mapToStringOperations())
          .ifPresent(builder::correlationKeyOperations);
      ofNullable(filter.getTenantId())
          .map(mapToStringOperations())
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
      ofNullable(filter.getCorrelationKey())
          .map(mapToStringOperations())
          .ifPresent(builder::correlationKeyOperations);
      ofNullable(filter.getCorrelationTime())
          .map(mapToOffsetDateTimeOperations("correlationTime", validationErrors))
          .ifPresent(builder::correlationTimeOperations);
      ofNullable(filter.getElementId())
          .map(mapToStringOperations())
          .ifPresent(builder::flowNodeIdOperations);
      ofNullable(filter.getElementInstanceKey())
          .map(mapToKeyOperations("elementInstanceKey", validationErrors))
          .ifPresent(builder::flowNodeInstanceKeyOperations);
      ofNullable(filter.getMessageKey())
          .map(mapToKeyOperations("messageKey", validationErrors))
          .ifPresent(builder::messageKeyOperations);
      ofNullable(filter.getMessageName())
          .map(mapToStringOperations())
          .ifPresent(builder::messageNameOperations);
      ofNullable(filter.getPartitionId())
          .map(mapToIntegerOperations("partitionId", validationErrors))
          .ifPresent(builder::partitionIdOperations);
      ofNullable(filter.getProcessDefinitionId())
          .map(mapToStringOperations())
          .ifPresent(builder::processDefinitionIdOperations);
      ofNullable(filter.getProcessDefinitionKey())
          .map(mapToKeyOperations("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeyOperations);
      ofNullable(filter.getProcessInstanceKey())
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.getSubscriptionKey())
          .map(mapToKeyOperations("subscriptionKey", validationErrors))
          .ifPresent(builder::subscriptionKeyOperations);
      ofNullable(filter.getTenantId())
          .map(mapToStringOperations())
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
    ofNullable(filter.getAuditLogKey())
        .map(mapToStringOperations())
        .ifPresent(builder::auditLogKeyOperations);
    ofNullable(filter.getProcessDefinitionKey())
        .map(mapToKeyOperations("processDefinitionKey", validationErrors))
        .ifPresent(builder::processDefinitionKeyOperations);
    ofNullable(filter.getProcessInstanceKey())
        .map(mapToKeyOperations("processInstanceKey", validationErrors))
        .ifPresent(builder::processInstanceKeyOperations);
    ofNullable(filter.getElementInstanceKey())
        .map(mapToKeyOperations("elementInstanceKey", validationErrors))
        .ifPresent(builder::elementInstanceKeyOperations);
    ofNullable(filter.getOperationType())
        .map(
            mapToStringOperations(
                "operationType", validationErrors, new AuditLogOperationTypeConverter()))
        .ifPresent(builder::operationTypeOperations);
    ofNullable(filter.getResult())
        .map(mapToStringOperations("result", validationErrors, new AuditLogResultConverter()))
        .ifPresent(builder::resultOperations);
    ofNullable(filter.getTimestamp())
        .map(mapToOffsetDateTimeOperations("timestamp", validationErrors))
        .ifPresent(builder::timestampOperations);
    ofNullable(filter.getActorId())
        .map(mapToStringOperations())
        .ifPresent(builder::actorIdOperations);
    ofNullable(filter.getActorType())
        .map(mapToStringOperations("actorType", validationErrors, new AuditLogActorTypeConverter()))
        .ifPresent(builder::actorTypeOperations);
    ofNullable(filter.getAgentElementId())
        .map(mapToStringOperations())
        .ifPresent(builder::agentElementIdOperations);
    ofNullable(filter.getEntityType())
        .map(
            mapToStringOperations(
                "entityType", validationErrors, new AuditLogEntityTypeConverter()))
        .ifPresent(builder::entityTypeOperations);
    ofNullable(filter.getEntityKey())
        .map(mapToStringOperations())
        .ifPresent(builder::entityKeyOperations);
    ofNullable(filter.getTenantId())
        .map(mapToStringOperations())
        .ifPresent(builder::tenantIdOperations);
    ofNullable(filter.getCategory())
        .map(mapToStringOperations("category", validationErrors, new AuditLogCategoryConverter()))
        .ifPresent(builder::categoryOperations);
    ofNullable(filter.getDeploymentKey())
        .map(mapToKeyOperations("deploymentKey", validationErrors))
        .ifPresent(builder::deploymentKeyOperations);
    ofNullable(filter.getFormKey())
        .map(mapToKeyOperations("formKey", validationErrors))
        .ifPresent(builder::formKeyOperations);
    ofNullable(filter.getResourceKey())
        .map(mapToKeyOperations("resourceKey", validationErrors))
        .ifPresent(builder::resourceKeyOperations);
    ofNullable(filter.getBatchOperationType())
        .map(
            mapToStringOperations(
                "batchOperationType", validationErrors, new BatchOperationTypeConverter()))
        .ifPresent(builder::batchOperationTypeOperations);
    ofNullable(filter.getProcessDefinitionId())
        .map(mapToStringOperations())
        .ifPresent(builder::processDefinitionIdOperations);
    ofNullable(filter.getJobKey())
        .map(mapToKeyOperations("jobKey", validationErrors))
        .ifPresent(builder::jobKeyOperations);
    ofNullable(filter.getUserTaskKey())
        .map(mapToKeyOperations("userTaskKey", validationErrors))
        .ifPresent(builder::userTaskKeyOperations);
    ofNullable(filter.getDecisionRequirementsId())
        .map(mapToStringOperations())
        .ifPresent(builder::decisionRequirementsIdOperations);
    ofNullable(filter.getDecisionRequirementsKey())
        .map(mapToKeyOperations("decisionRequirementsKey", validationErrors))
        .ifPresent(builder::decisionRequirementsKeyOperations);
    ofNullable(filter.getDecisionDefinitionId())
        .map(mapToStringOperations())
        .ifPresent(builder::decisionDefinitionIdOperations);
    ofNullable(filter.getDecisionDefinitionKey())
        .map(mapToKeyOperations("decisionDefinitionKey", validationErrors))
        .ifPresent(builder::decisionDefinitionKeyOperations);
    ofNullable(filter.getDecisionEvaluationKey())
        .map(mapToKeyOperations("decisionEvaluationKey", validationErrors))
        .ifPresent(builder::decisionEvaluationKeyOperations);
    ofNullable(filter.getRelatedEntityKey())
        .map(mapToStringOperations())
        .ifPresent(builder::relatedEntityKeyOperations);
    ofNullable(filter.getRelatedEntityType())
        .map(
            mapToStringOperations(
                "relatedEntityType", validationErrors, new AuditLogEntityTypeConverter()))
        .ifPresent(builder::relatedEntityTypeOperations);
    ofNullable(filter.getEntityDescription())
        .map(mapToStringOperations())
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
    Optional.ofNullable(filter.getTenantId()).ifPresent(builder::tenantId);

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
    final List<Operation<String>> operations = mapToStringOperations().apply(value);
    return new VariableValueFilter.Builder()
        .name(name)
        .valueTypedOperations(operations)
        .buildList();
  }

  static AuthorizationFilter toAuthorizationFilter(
      final io.camunda.gateway.protocol.model.AuthorizationFilter filter) {
    return Optional.ofNullable(filter)
        .map(
            f ->
                FilterBuilders.authorization()
                    .ownerIds(f.getOwnerId())
                    .ownerType(f.getOwnerType() == null ? null : f.getOwnerType().getValue())
                    .resourceIds(f.getResourceIds())
                    .resourcePropertyNames(f.getResourcePropertyNames())
                    .resourceType(
                        f.getResourceType() == null ? null : f.getResourceType().getValue())
                    .build())
        .orElse(null);
  }

  static Either<List<String>, AuditLogFilter> toUserTaskAuditLogFilter(
      final UserTaskAuditLogFilter filter) {
    if (filter == null) {
      return Either.right(FilterBuilders.auditLog().build());
    }

    final var builder = FilterBuilders.auditLog();
    final List<String> validationErrors = new ArrayList<>();
    ofNullable(filter.getOperationType())
        .map(
            mapToStringOperations(
                "operationType", validationErrors, new AuditLogOperationTypeConverter()))
        .ifPresent(builder::operationTypeOperations);
    ofNullable(filter.getResult())
        .map(mapToStringOperations())
        .ifPresent(builder::resultOperations);
    ofNullable(filter.getTimestamp())
        .map(mapToOffsetDateTimeOperations("timestamp", validationErrors))
        .ifPresent(builder::timestampOperations);
    ofNullable(filter.getActorId())
        .map(mapToStringOperations())
        .ifPresent(builder::actorIdOperations);
    ofNullable(filter.getActorType())
        .map(mapToStringOperations("actorType", validationErrors, new AuditLogActorTypeConverter()))
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
    ofNullable(filter.getName()).map(mapToStringOperations()).ifPresent(builder::nameOperations);

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
      ofNullable(filter.getId())
          .map(mapToStringOperations())
          .ifPresent(builder::listenerIdOperations);
      ofNullable(filter.getType()).map(mapToStringOperations()).ifPresent(builder::typeOperations);
      ofNullable(filter.getRetries())
          .map(mapToIntegerOperations("retries", validationErrors))
          .ifPresent(builder::retriesOperations);
      ofNullable(filter.getEventTypes())
          .map(mapToStringOperations())
          .ifPresent(builder::eventTypeOperations);
      ofNullable(filter.getAfterNonGlobal()).ifPresent(builder::afterNonGlobal);
      ofNullable(filter.getPriority())
          .map(mapToIntegerOperations("priority", validationErrors))
          .ifPresent(builder::priorityOperations);
      ofNullable(filter.getSource())
          .map(mapToStringOperations())
          .ifPresent(builder::sourceOperations);
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, GlobalListenerFilter> toGlobalExecutionListenerFilter(
      final GlobalExecutionListenerSearchQueryFilterRequest filter) {

    final var builder =
        FilterBuilders.globalListener().listenerTypes(GlobalListenerType.EXECUTION.name());
    final List<String> validationErrors = new ArrayList<>();

    if (filter != null) {
      ofNullable(filter.getId())
          .map(mapToStringOperations())
          .ifPresent(builder::listenerIdOperations);
      ofNullable(filter.getType()).map(mapToStringOperations()).ifPresent(builder::typeOperations);
      ofNullable(filter.getRetries())
          .map(mapToIntegerOperations("retries", validationErrors))
          .ifPresent(builder::retriesOperations);
      ofNullable(filter.getEventTypes())
          .map(mapToStringOperations())
          .ifPresent(builder::eventTypeOperations);
      ofNullable(filter.getElementTypes())
          .map(mapToStringOperations())
          .ifPresent(builder::elementTypeOperations);
      ofNullable(filter.getCategories())
          .map(mapToStringOperations())
          .ifPresent(builder::categoryOperations);
      ofNullable(filter.getAfterNonGlobal()).ifPresent(builder::afterNonGlobal);
      ofNullable(filter.getPriority())
          .map(mapToIntegerOperations("priority", validationErrors))
          .ifPresent(builder::priorityOperations);
      ofNullable(filter.getSource())
          .map(mapToStringOperations())
          .ifPresent(builder::sourceOperations);
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }
}
