/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search;

import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOperations;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_AT_LEAST_ONE_FIELD;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_NULL_VARIABLE_NAME;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_NULL_VARIABLE_VALUE;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateDate;
import static java.util.Optional.ofNullable;

import io.camunda.gateway.mapping.http.converters.AuditLogActorTypeConverter;
import io.camunda.gateway.mapping.http.converters.AuditLogCategoryConverter;
import io.camunda.gateway.mapping.http.converters.AuditLogEntityTypeConverter;
import io.camunda.gateway.mapping.http.converters.AuditLogOperationTypeConverter;
import io.camunda.gateway.mapping.http.converters.AuditLogResultConverter;
import io.camunda.gateway.mapping.http.converters.BatchOperationTypeConverter;
import io.camunda.gateway.mapping.http.converters.DecisionInstanceStateConverter;
import io.camunda.gateway.mapping.http.converters.ProcessInstanceStateConverter;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.mapping.http.validator.TagsValidator;
import io.camunda.gateway.protocol.model.BaseProcessInstanceFilterFields;
import io.camunda.gateway.protocol.model.ClusterVariableSearchQueryFilterRequest;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByDefinitionFilter;
import io.camunda.gateway.protocol.model.ProcessInstanceFilterFields;
import io.camunda.gateway.protocol.model.StringFilterProperty;
import io.camunda.gateway.protocol.model.UserTaskAuditLogFilter;
import io.camunda.gateway.protocol.model.UserTaskVariableFilter;
import io.camunda.gateway.protocol.model.VariableValueFilterProperty;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
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
import io.camunda.search.filter.GroupFilter;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.filter.JobFilter;
import io.camunda.search.filter.JobTypeStatisticsFilter;
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
          } else {
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
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.getParentProcessInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::parentProcessInstanceKeyOperations);
      ofNullable(filter.getParentElementInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::parentFlowNodeInstanceKeyOperations);
      ofNullable(filter.getStartDate())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::startDateOperations);
      ofNullable(filter.getEndDate())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::endDateOperations);
      ofNullable(filter.getState())
          .map(mapToOperations(String.class, new ProcessInstanceStateConverter()))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.getHasIncident()).ifPresent(builder::hasIncident);
      ofNullable(filter.getTenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
      ofNullable(filter.getBatchOperationId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationIdOperations);
      ofNullable(filter.getErrorMessage())
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorMessageOperations);
      ofNullable(filter.getHasRetriesLeft()).ifPresent(builder::hasRetriesLeft);
      ofNullable(filter.getElementId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeIdOperations);
      ofNullable(filter.getHasElementInstanceIncident())
          .ifPresent(builder::hasFlowNodeInstanceIncident);
      ofNullable(filter.getElementInstanceState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeInstanceStateOperations);
      ofNullable(filter.getIncidentErrorHashCode())
          .map(mapToOperations(Integer.class))
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

  static JobFilter toJobFilter(final io.camunda.gateway.protocol.model.JobFilter filter) {
    final var builder = FilterBuilders.job();
    if (filter != null) {
      ofNullable(filter.getJobKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::jobKeyOperations);
      ofNullable(filter.getType())
          .map(mapToOperations(String.class))
          .ifPresent(builder::typeOperations);
      ofNullable(filter.getWorker())
          .map(mapToOperations(String.class))
          .ifPresent(builder::workerOperations);
      ofNullable(filter.getState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.getKind())
          .map(mapToOperations(String.class))
          .ifPresent(builder::kindOperations);
      ofNullable(filter.getListenerEventType())
          .map(mapToOperations(String.class))
          .ifPresent(builder::listenerEventTypeOperations);
      ofNullable(filter.getProcessDefinitionId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      ofNullable(filter.getProcessDefinitionKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processDefinitionKeyOperations);
      ofNullable(filter.getProcessInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.getElementId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::elementIdOperations);
      ofNullable(filter.getElementInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::elementInstanceKeyOperations);
      ofNullable(filter.getTenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
      ofNullable(filter.getDeadline())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::deadlineOperations);
      ofNullable(filter.getDeniedReason())
          .map(mapToOperations(String.class))
          .ifPresent(builder::deniedReasonOperations);
      ofNullable(filter.getIsDenied()).ifPresent(builder::isDenied);
      ofNullable(filter.getEndTime())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::endTimeOperations);
      ofNullable(filter.getErrorCode())
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorCodeOperations);
      ofNullable(filter.getErrorMessage())
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorMessageOperations);
      ofNullable(filter.getHasFailedWithRetriesLeft()).ifPresent(builder::hasFailedWithRetriesLeft);
      ofNullable(filter.getRetries())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::retriesOperations);
      ofNullable(filter.getCreationTime())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::creationTimeOperations);
      ofNullable(filter.getLastUpdateTime())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::lastUpdateTimeOperations);
    }

    return builder.build();
  }

  static DecisionInstanceFilter toDecisionInstanceFilter(
      final io.camunda.gateway.protocol.model.DecisionInstanceFilter filter) {
    final var builder = FilterBuilders.decisionInstance();

    if (filter != null) {
      ofNullable(filter.getDecisionEvaluationKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::decisionInstanceKeys);
      ofNullable(filter.getDecisionEvaluationInstanceKey())
          .map(mapToOperations(String.class))
          .ifPresent(builder::decisionInstanceIdOperations);
      ofNullable(filter.getState())
          .map(mapToOperations(String.class, new DecisionInstanceStateConverter()))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.getEvaluationFailure()).ifPresent(builder::evaluationFailures);
      ofNullable(filter.getEvaluationDate())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::evaluationDateOperations);
      ofNullable(filter.getProcessDefinitionKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::processDefinitionKeys);
      ofNullable(filter.getProcessInstanceKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::processInstanceKeys);
      ofNullable(filter.getElementInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::flowNodeInstanceKeyOperations);
      ofNullable(filter.getDecisionDefinitionKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::decisionDefinitionKeyOperations);
      ofNullable(filter.getDecisionDefinitionId()).ifPresent(builder::decisionDefinitionIds);
      ofNullable(filter.getDecisionDefinitionName()).ifPresent(builder::decisionDefinitionNames);
      ofNullable(filter.getDecisionDefinitionVersion())
          .ifPresent(builder::decisionDefinitionVersions);
      ofNullable(filter.getDecisionDefinitionType())
          .map(t -> convertEnum(t, DecisionDefinitionType.class))
          .ifPresent(builder::decisionTypes);
      ofNullable(filter.getRootDecisionDefinitionKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::rootDecisionDefinitionKeyOperations);
      ofNullable(filter.getDecisionRequirementsKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::decisionRequirementsKeyOperations);
      ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
    }
    return builder.build();
  }

  private static <Q extends Enum<Q>, E extends Enum<E>> E convertEnum(
      @NotNull final Q sourceEnum, @NotNull final Class<E> targetEnumType) {
    return Enum.valueOf(targetEnumType, sourceEnum.name());
  }

  static VariableFilter toVariableFilter(
      final io.camunda.gateway.protocol.model.VariableFilter filter) {
    if (filter == null) {
      return FilterBuilders.variable().build();
    }

    final var builder = FilterBuilders.variable();

    ofNullable(filter.getProcessInstanceKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::processInstanceKeyOperations);
    ofNullable(filter.getScopeKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::scopeKeyOperations);
    ofNullable(filter.getVariableKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::variableKeyOperations);
    ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
    ofNullable(filter.getIsTruncated()).ifPresent(builder::isTruncated);
    ofNullable(filter.getName())
        .map(mapToOperations(String.class))
        .ifPresent(builder::nameOperations);
    ofNullable(filter.getValue())
        .map(mapToOperations(String.class))
        .ifPresent(builder::valueOperations);

    return builder.build();
  }

  static ClusterVariableFilter toClusterVariableFilter(
      final ClusterVariableSearchQueryFilterRequest filter) {

    if (filter == null) {
      return FilterBuilders.clusterVariable().build();
    }

    final var builder = FilterBuilders.clusterVariable();

    ofNullable(filter.getName())
        .map(mapToOperations(String.class))
        .ifPresent(builder::nameOperations);
    ofNullable(filter.getValue())
        .map(mapToOperations(String.class))
        .ifPresent(builder::valueOperations);
    ofNullable(filter.getScope())
        .map(mapToOperations(String.class))
        .ifPresent(builder::scopeOperations);
    ofNullable(filter.getTenantId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::tenantIdOperations);
    ofNullable(filter.getIsTruncated()).ifPresent(builder::isTruncated);

    return builder.build();
  }

  static BatchOperationFilter toBatchOperationFilter(
      final io.camunda.gateway.protocol.model.BatchOperationFilter filter) {
    final var builder = FilterBuilders.batchOperation();

    if (filter != null) {
      ofNullable(filter.getBatchOperationKey())
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationKeyOperations);
      ofNullable(filter.getState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.getOperationType())
          .map(mapToOperations(String.class))
          .ifPresent(builder::operationTypeOperations);
      ofNullable(filter.getActorType())
          .map(io.camunda.gateway.protocol.model.AuditLogActorTypeEnum::getValue)
          .map(String::toUpperCase)
          .ifPresent(builder::actorTypes);
      ofNullable(filter.getActorId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::actorIdOperations);
    }

    return builder.build();
  }

  static io.camunda.search.filter.BatchOperationItemFilter toBatchOperationItemFilter(
      final io.camunda.gateway.protocol.model.BatchOperationItemFilter filter) {
    final var builder = FilterBuilders.batchOperationItem();

    if (filter != null) {
      ofNullable(filter.getBatchOperationKey())
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationKeyOperations);
      ofNullable(filter.getState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.getItemKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::itemKeyOperations);
      ofNullable(filter.getProcessInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.getOperationType())
          .map(mapToOperations(String.class))
          .ifPresent(builder::operationTypeOperations);
    }

    return builder.build();
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
        .map(mapToOperations(String.class))
        .ifPresent(builder::jobTypeOperations);

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  static ProcessDefinitionFilter toProcessDefinitionFilter(
      final io.camunda.gateway.protocol.model.ProcessDefinitionFilter filter) {
    final var builder = FilterBuilders.processDefinition();
    Optional.ofNullable(filter)
        .ifPresent(
            f -> {
              Optional.ofNullable(f.getIsLatestVersion()).ifPresent(builder::isLatestVersion);
              Optional.ofNullable(f.getProcessDefinitionKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::processDefinitionKeys);
              Optional.ofNullable(f.getName())
                  .map(mapToOperations(String.class))
                  .ifPresent(builder::nameOperations);
              Optional.ofNullable(f.getResourceName()).ifPresent(builder::resourceNames);
              Optional.ofNullable(f.getVersion()).ifPresent(builder::versions);
              Optional.ofNullable(f.getVersionTag()).ifPresent(builder::versionTags);
              Optional.ofNullable(f.getProcessDefinitionId())
                  .map(mapToOperations(String.class))
                  .ifPresent(builder::processDefinitionIdOperations);
              Optional.ofNullable(f.getTenantId()).ifPresent(builder::tenantIds);
              Optional.ofNullable(f.getHasStartForm()).ifPresent(builder::hasStartForm);
            });
    return builder.build();
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
    return Either.right(toDecisionInstanceFilter(filter));
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
          } else {
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
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.getProcessDefinitionId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      ofNullable(filter.getProcessDefinitionName())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionNameOperations);
      ofNullable(filter.getProcessDefinitionVersion())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::processDefinitionVersionOperations);
      ofNullable(filter.getProcessDefinitionVersionTag())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionVersionTagOperations);
      ofNullable(filter.getProcessDefinitionKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processDefinitionKeyOperations);
      ofNullable(filter.getParentProcessInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::parentProcessInstanceKeyOperations);
      ofNullable(filter.getParentElementInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::parentFlowNodeInstanceKeyOperations);
      ofNullable(filter.getStartDate())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::startDateOperations);
      ofNullable(filter.getEndDate())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::endDateOperations);
      ofNullable(filter.getState())
          .map(mapToOperations(String.class, new ProcessInstanceStateConverter()))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.getHasIncident()).ifPresent(builder::hasIncident);
      ofNullable(filter.getTenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
      ofNullable(filter.getBatchOperationId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationIdOperations);
      ofNullable(filter.getErrorMessage())
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorMessageOperations);
      ofNullable(filter.getHasRetriesLeft()).ifPresent(builder::hasRetriesLeft);
      ofNullable(filter.getElementId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeIdOperations);
      ofNullable(filter.getHasElementInstanceIncident())
          .ifPresent(builder::hasFlowNodeInstanceIncident);
      ofNullable(filter.getElementInstanceState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeInstanceStateOperations);
      ofNullable(filter.getIncidentErrorHashCode())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::incidentErrorHashCodeOperations);

      if (!CollectionUtils.isEmpty(filter.getTags())) {
        final var tagErrors = TagsValidator.validate(filter.getTags());
        if (tagErrors.isEmpty()) {
          ofNullable(filter.getTags()).ifPresent(builder::tags);
        } else {
          validationErrors.addAll(tagErrors);
        }
      }

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
          .map(mapToOperations(String.class))
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

  static DecisionDefinitionFilter toDecisionDefinitionFilter(
      final io.camunda.gateway.protocol.model.DecisionDefinitionFilter filter) {
    final var builder = FilterBuilders.decisionDefinition();

    if (filter != null) {
      ofNullable(filter.getDecisionDefinitionKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::decisionDefinitionKeys);
      ofNullable(filter.getDecisionDefinitionId()).ifPresent(builder::decisionDefinitionIds);
      ofNullable(filter.getName()).ifPresent(builder::names);
      ofNullable(filter.getIsLatestVersion()).ifPresent(builder::isLatestVersion);
      ofNullable(filter.getVersion()).ifPresent(builder::versions);
      ofNullable(filter.getDecisionRequirementsId()).ifPresent(builder::decisionRequirementsIds);
      ofNullable(filter.getDecisionRequirementsKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::decisionRequirementsKeys);
      ofNullable(filter.getDecisionRequirementsName())
          .ifPresent(builder::decisionRequirementsNames);
      ofNullable(filter.getDecisionRequirementsVersion())
          .ifPresent(builder::decisionRequirementsVersions);
      ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
    }

    return builder.build();
  }

  static DecisionRequirementsFilter toDecisionRequirementsFilter(
      final io.camunda.gateway.protocol.model.DecisionRequirementsFilter filter) {
    final var builder = FilterBuilders.decisionRequirements();

    Optional.ofNullable(filter)
        .ifPresent(
            f -> {
              Optional.ofNullable(f.getDecisionRequirementsKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::decisionRequirementsKeys);
              Optional.ofNullable(f.getDecisionRequirementsName()).ifPresent(builder::names);
              Optional.ofNullable(f.getVersion()).ifPresent(builder::versions);
              Optional.ofNullable(f.getDecisionRequirementsId())
                  .ifPresent(builder::decisionRequirementsIds);
              Optional.ofNullable(f.getTenantId()).ifPresent(builder::tenantIds);
              Optional.ofNullable(f.getResourceName()).ifPresent(builder::resourceNames);
            });

    return builder.build();
  }

  static FlowNodeInstanceFilter toElementInstanceFilter(
      final io.camunda.gateway.protocol.model.ElementInstanceFilter filter) {
    final var builder = FilterBuilders.flowNodeInstance();
    Optional.ofNullable(filter)
        .ifPresent(
            f -> {
              Optional.ofNullable(f.getElementInstanceKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::flowNodeInstanceKeys);
              Optional.ofNullable(f.getProcessInstanceKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::processInstanceKeys);
              Optional.ofNullable(f.getProcessDefinitionKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::processDefinitionKeys);
              Optional.ofNullable(f.getProcessDefinitionId())
                  .ifPresent(builder::processDefinitionIds);
              Optional.ofNullable(f.getState())
                  .map(mapToOperations(String.class))
                  .ifPresent(builder::stateOperations);
              Optional.ofNullable(f.getType())
                  .ifPresent(
                      t -> builder.types(FlowNodeType.fromZeebeBpmnElementType(t.getValue())));
              Optional.ofNullable(f.getElementId()).ifPresent(builder::flowNodeIds);
              Optional.ofNullable(f.getElementName()).ifPresent(builder::flowNodeNames);
              Optional.ofNullable(f.getHasIncident()).ifPresent(builder::hasIncident);
              Optional.ofNullable(f.getIncidentKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::incidentKeys);
              Optional.ofNullable(f.getTenantId()).ifPresent(builder::tenantIds);
              Optional.ofNullable(f.getStartDate())
                  .map(mapToOperations(OffsetDateTime.class))
                  .ifPresent(builder::startDateOperations);
              Optional.ofNullable(f.getEndDate())
                  .map(mapToOperations(OffsetDateTime.class))
                  .ifPresent(builder::endDateOperations);
              Optional.ofNullable(f.getElementInstanceScopeKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::elementInstanceScopeKeys);
            });
    return builder.build();
  }

  static Either<List<String>, UserTaskFilter> toUserTaskFilter(
      final io.camunda.gateway.protocol.model.UserTaskFilter filter) {
    final var builder = FilterBuilders.userTask();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      Optional.ofNullable(filter.getUserTaskKey())
          .map(KeyUtil::keyToLong)
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
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::processDefinitionKeys);
      Optional.ofNullable(filter.getProcessInstanceKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::processInstanceKeys);
      Optional.ofNullable(filter.getTenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
      Optional.ofNullable(filter.getElementInstanceKey())
          .map(KeyUtil::keyToLong)
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
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::creationDateOperations);
      Optional.ofNullable(filter.getCompletionDate())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::completionDateOperations);
      Optional.ofNullable(filter.getDueDate())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::dueDateOperations);
      Optional.ofNullable(filter.getFollowUpDate())
          .map(mapToOperations(OffsetDateTime.class))
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

  static IncidentFilter toIncidentFilter(
      final io.camunda.gateway.protocol.model.IncidentFilter filter) {
    final var builder = FilterBuilders.incident();

    if (filter != null) {
      ofNullable(filter.getIncidentKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::incidentKeyOperations);
      ofNullable(filter.getProcessDefinitionKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processDefinitionKeyOperations);
      ofNullable(filter.getProcessDefinitionId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      ofNullable(filter.getProcessInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.getErrorType())
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorTypeOperations);
      ofNullable(filter.getErrorMessage())
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorMessageOperations);
      ofNullable(filter.getElementId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeIdOperations);
      ofNullable(filter.getElementInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::flowNodeInstanceKeyOperations);
      ofNullable(filter.getCreationTime())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::creationTimeOperations);
      ofNullable(filter.getState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.getJobKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::jobKeyOperations);
      ofNullable(filter.getTenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
    }
    return builder.build();
  }

  static MessageSubscriptionFilter toMessageSubscriptionFilter(
      final io.camunda.gateway.protocol.model.MessageSubscriptionFilter filter) {
    final var builder = FilterBuilders.messageSubscription();

    if (filter != null) {
      ofNullable(filter.getProcessDefinitionKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processDefinitionKeyOperations);
      ofNullable(filter.getMessageSubscriptionKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::messageSubscriptionKeyOperations);
      ofNullable(filter.getProcessDefinitionId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      ofNullable(filter.getProcessInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.getElementId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeIdOperations);
      ofNullable(filter.getElementInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::flowNodeInstanceKeyOperations);
      ofNullable(filter.getMessageSubscriptionState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::messageSubscriptionStateOperations);
      ofNullable(filter.getLastUpdatedDate())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::dateTimeOperations);
      ofNullable(filter.getMessageName())
          .map(mapToOperations(String.class))
          .ifPresent(builder::messageNameOperations);
      ofNullable(filter.getCorrelationKey())
          .map(mapToOperations(String.class))
          .ifPresent(builder::correlationKeyOperations);
      ofNullable(filter.getTenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
    }
    return builder.build();
  }

  static CorrelatedMessageSubscriptionFilter toCorrelatedMessageSubscriptionFilter(
      final io.camunda.gateway.protocol.model.CorrelatedMessageSubscriptionFilter filter) {
    final var builder = FilterBuilders.correlatedMessageSubscription();

    if (filter != null) {
      ofNullable(filter.getCorrelationKey())
          .map(mapToOperations(String.class))
          .ifPresent(builder::correlationKeyOperations);
      ofNullable(filter.getCorrelationTime())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::correlationTimeOperations);
      ofNullable(filter.getElementId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeIdOperations);
      ofNullable(filter.getElementInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::flowNodeInstanceKeyOperations);
      ofNullable(filter.getMessageKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::messageKeyOperations);
      ofNullable(filter.getMessageName())
          .map(mapToOperations(String.class))
          .ifPresent(builder::messageNameOperations);
      ofNullable(filter.getPartitionId())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::partitionIdOperations);
      ofNullable(filter.getProcessDefinitionId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      ofNullable(filter.getProcessDefinitionKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processDefinitionKeyOperations);
      ofNullable(filter.getProcessInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.getSubscriptionKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::subscriptionKeyOperations);
      ofNullable(filter.getTenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
    }
    return builder.build();
  }

  static io.camunda.search.filter.AuditLogFilter toAuditLogFilter(
      final io.camunda.gateway.protocol.model.AuditLogFilter filter) {
    if (filter == null) {
      return FilterBuilders.auditLog().build();
    }

    final var builder = FilterBuilders.auditLog();
    ofNullable(filter.getAuditLogKey())
        .map(mapToOperations(String.class))
        .ifPresent(builder::auditLogKeyOperations);
    ofNullable(filter.getProcessDefinitionKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::processDefinitionKeyOperations);
    ofNullable(filter.getProcessInstanceKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::processInstanceKeyOperations);
    ofNullable(filter.getElementInstanceKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::elementInstanceKeyOperations);
    ofNullable(filter.getOperationType())
        .map(mapToOperations(String.class, new AuditLogOperationTypeConverter()))
        .ifPresent(builder::operationTypeOperations);
    ofNullable(filter.getResult())
        .map(mapToOperations(String.class, new AuditLogResultConverter()))
        .ifPresent(builder::resultOperations);
    ofNullable(filter.getTimestamp())
        .map(mapToOperations(OffsetDateTime.class))
        .ifPresent(builder::timestampOperations);
    ofNullable(filter.getActorId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::actorIdOperations);
    ofNullable(filter.getActorType())
        .map(mapToOperations(String.class, new AuditLogActorTypeConverter()))
        .ifPresent(builder::actorTypeOperations);
    ofNullable(filter.getAgentElementId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::agentElementIdOperations);
    ofNullable(filter.getEntityType())
        .map(mapToOperations(String.class, new AuditLogEntityTypeConverter()))
        .ifPresent(builder::entityTypeOperations);
    ofNullable(filter.getEntityKey())
        .map(mapToOperations(String.class))
        .ifPresent(builder::entityKeyOperations);
    ofNullable(filter.getTenantId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::tenantIdOperations);
    ofNullable(filter.getCategory())
        .map(mapToOperations(String.class, new AuditLogCategoryConverter()))
        .ifPresent(builder::categoryOperations);
    ofNullable(filter.getDeploymentKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::deploymentKeyOperations);
    ofNullable(filter.getFormKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::formKeyOperations);
    ofNullable(filter.getResourceKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::resourceKeyOperations);
    ofNullable(filter.getBatchOperationType())
        .map(mapToOperations(String.class, new BatchOperationTypeConverter()))
        .ifPresent(builder::batchOperationTypeOperations);
    ofNullable(filter.getProcessDefinitionId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionIdOperations);
    ofNullable(filter.getJobKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::jobKeyOperations);
    ofNullable(filter.getUserTaskKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::userTaskKeyOperations);
    ofNullable(filter.getDecisionRequirementsId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::decisionRequirementsIdOperations);
    ofNullable(filter.getDecisionRequirementsKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::decisionRequirementsKeyOperations);
    ofNullable(filter.getDecisionDefinitionId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::decisionDefinitionIdOperations);
    ofNullable(filter.getDecisionDefinitionKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::decisionDefinitionKeyOperations);
    ofNullable(filter.getDecisionEvaluationKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::decisionEvaluationKeyOperations);
    ofNullable(filter.getRelatedEntityKey())
        .map(mapToOperations(String.class))
        .ifPresent(builder::relatedEntityKeyOperations);
    ofNullable(filter.getRelatedEntityType())
        .map(mapToOperations(String.class, new AuditLogEntityTypeConverter()))
        .ifPresent(builder::relatedEntityTypeOperations);
    ofNullable(filter.getEntityDescription())
        .map(mapToOperations(String.class))
        .ifPresent(builder::entityDescriptionOperations);
    return builder.build();
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

  static AuditLogFilter toUserTaskAuditLogFilter(final UserTaskAuditLogFilter filter) {
    if (filter == null) {
      return FilterBuilders.auditLog().build();
    }

    final var builder = FilterBuilders.auditLog();
    ofNullable(filter.getOperationType())
        .map(mapToOperations(String.class, new AuditLogOperationTypeConverter()))
        .ifPresent(builder::operationTypeOperations);
    ofNullable(filter.getResult())
        .map(mapToOperations(String.class))
        .ifPresent(builder::resultOperations);
    ofNullable(filter.getTimestamp())
        .map(mapToOperations(OffsetDateTime.class))
        .ifPresent(builder::timestampOperations);
    ofNullable(filter.getActorId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::actorIdOperations);
    ofNullable(filter.getActorType())
        .map(mapToOperations(String.class, new AuditLogActorTypeConverter()))
        .ifPresent(builder::actorTypeOperations);
    return builder.build();
  }

  static VariableFilter toUserTaskVariableFilter(final UserTaskVariableFilter filter) {
    if (filter == null) {
      return FilterBuilders.variable().build();
    }

    final var builder = FilterBuilders.variable();
    ofNullable(filter.getName())
        .map(mapToOperations(String.class))
        .ifPresent(builder::nameOperations);

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
}
