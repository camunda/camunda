/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_SORT_FIELD_MUST_NOT_BE_NULL;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_UNKNOWN_SORT_BY;

import io.camunda.gateway.protocol.model.*;
import io.camunda.search.sort.AuthorizationSort;
import io.camunda.search.sort.BatchOperationItemSort;
import io.camunda.search.sort.BatchOperationSort;
import io.camunda.search.sort.ClusterVariableSort;
import io.camunda.search.sort.DecisionDefinitionSort;
import io.camunda.search.sort.DecisionInstanceSort;
import io.camunda.search.sort.DecisionRequirementsSort;
import io.camunda.search.sort.FlowNodeInstanceSort;
import io.camunda.search.sort.GlobalListenerSort;
import io.camunda.search.sort.GroupMemberSort;
import io.camunda.search.sort.GroupSort;
import io.camunda.search.sort.IncidentProcessInstanceStatisticsByDefinitionSort;
import io.camunda.search.sort.IncidentProcessInstanceStatisticsByErrorSort;
import io.camunda.search.sort.IncidentSort;
import io.camunda.search.sort.JobSort;
import io.camunda.search.sort.MappingRuleSort;
import io.camunda.search.sort.MessageSubscriptionSort;
import io.camunda.search.sort.ProcessDefinitionInstanceStatisticsSort;
import io.camunda.search.sort.ProcessDefinitionInstanceVersionStatisticsSort;
import io.camunda.search.sort.ProcessDefinitionSort;
import io.camunda.search.sort.ProcessInstanceSort;
import io.camunda.search.sort.RoleMemberSort;
import io.camunda.search.sort.RoleSort;
import io.camunda.search.sort.SortOption;
import io.camunda.search.sort.TenantMemberSort;
import io.camunda.search.sort.TenantSort;
import io.camunda.search.sort.UserSort;
import io.camunda.search.sort.UserTaskSort;
import io.camunda.search.sort.VariableSort;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.util.Either;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class SearchQuerySortRequestMapper {

  static List<SearchQuerySortRequest> fromProcessDefinitionSearchQuerySortRequest(
      final List<ProcessDefinitionSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromProcessInstanceSearchQuerySortRequest(
      final List<ProcessInstanceSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromJobSearchQuerySortRequest(
      final List<JobSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromRoleSearchQuerySortRequest(
      final List<RoleSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromRoleUserSearchQuerySortRequest(
      final List<RoleUserSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromRoleGroupSearchQuerySortRequest(
      final @Valid List<RoleGroupSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromRoleClientSearchQuerySortRequest(
      final List<RoleClientSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromGroupSearchQuerySortRequest(
      final List<GroupSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromGroupUserSearchQuerySortRequest(
      final List<GroupUserSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromGroupClientSearchQuerySortRequest(
      final List<GroupClientSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromTenantSearchQuerySortRequest(
      final List<TenantSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromTenantUserSearchQuerySortRequest(
      final List<TenantUserSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromTenantGroupSearchQuerySortRequest(
      final List<TenantGroupSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromTenantClientSearchQuerySortRequest(
      final List<TenantClientSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromMappingRuleSearchQuerySortRequest(
      final List<MappingRuleSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromDecisionDefinitionSearchQuerySortRequest(
      final List<DecisionDefinitionSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromDecisionRequirementsSearchQuerySortRequest(
      final List<DecisionRequirementsSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromElementInstanceSearchQuerySortRequest(
      final List<ElementInstanceSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromDecisionInstanceSearchQuerySortRequest(
      final List<DecisionInstanceSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromUserTaskSearchQuerySortRequest(
      final List<UserTaskSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromUserTaskVariableSearchQuerySortRequest(
      final List<UserTaskVariableSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromVariableSearchQuerySortRequest(
      final List<VariableSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromClusterVariableSearchQuerySortRequest(
      final List<ClusterVariableSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromUserSearchQuerySortRequest(
      final List<UserSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromIncidentSearchQuerySortRequest(
      final List<IncidentSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromAuthorizationSearchQuerySortRequest(
      final List<AuthorizationSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromAuditLogSearchQuerySortRequest(
      final List<AuditLogSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromUserTaskAuditLogSearchRequest(
      final List<AuditLogSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromBatchOperationSearchQuerySortRequest(
      final List<BatchOperationSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromBatchOperationItemSearchQuerySortRequest(
      final List<BatchOperationItemSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromMessageSubscriptionSearchQuerySortRequest(
      final List<MessageSubscriptionSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  static List<SearchQuerySortRequest> fromCorrelatedMessageSubscriptionSearchQuerySortRequest(
      final List<CorrelatedMessageSubscriptionSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  public static List<SearchQuerySortRequest>
      fromProcessDefinitionInstanceStatisticsQuerySortRequest(
          final List<ProcessDefinitionInstanceStatisticsQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  public static List<SearchQuerySortRequest>
      fromProcessDefinitionInstanceVersionStatisticsQuerySortRequest(
          final List<ProcessDefinitionInstanceVersionStatisticsQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  public static List<SearchQuerySortRequest>
      fromIncidentProcessInstanceStatisticsByErrorQuerySortRequest(
          final List<IncidentProcessInstanceStatisticsByErrorQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  public static List<SearchQuerySortRequest>
      fromIncidentProcessInstanceStatisticsByDefinitionQuerySortRequest(
          final List<IncidentProcessInstanceStatisticsByDefinitionQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r ->
                createFrom(
                    r.getField() != null ? r.getField().getValue() : null,
                    r.getOrder() != null ? r.getOrder().getValue() : null))
        .toList();
  }

  public static List<SearchQuerySortRequest> fromGlobalTaskListenerSearchQuerySortRequest(
      final List<GlobalTaskListenerSearchQuerySortRequest> requests) {
    // Add default sorting after provided ones, ensuring a meaningful ordering:
    // - place "after non global" listeners at the end
    // - sort by priority (highest priority is returned first)
    // - sort by id to ensure a deterministic order for listeners with the same priority
    final var converted =
        requests.stream()
            .map(
                r ->
                    createFrom(
                        r.getField() != null ? r.getField().getValue() : null,
                        r.getOrder() != null ? r.getOrder().getValue() : null))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    converted.addAll(
        List.of(
            new SearchQuerySortRequest("afterNonGlobal", null),
            new SearchQuerySortRequest("priority", "desc"),
            new SearchQuerySortRequest("id", null)));
    return converted;
  }

  private static SearchQuerySortRequest createFrom(final String field, final String order) {
    return new SearchQuerySortRequest(field, order);
  }

  static List<String> applyDecisionInstanceSortField(
      final String field, final DecisionInstanceSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "decisionEvaluationKey" -> builder.decisionInstanceKey();
        case "decisionEvaluationInstanceKey" -> builder.decisionInstanceId();
        case "state" -> builder.state();
        case "evaluationDate" -> builder.evaluationDate();
        case "evaluationFailure" -> builder.evaluationFailure();
        case "processDefinitionKey" -> builder.processDefinitionKey();
        case "processInstanceKey" -> builder.processInstanceKey();
        case "elementInstanceKey" -> builder.flowNodeInstanceKey();
        case "decisionDefinitionKey" -> builder.decisionDefinitionKey();
        case "decisionDefinitionId" -> builder.decisionDefinitionId();
        case "decisionDefinitionName" -> builder.decisionDefinitionName();
        case "decisionDefinitionVersion" -> builder.decisionDefinitionVersion();
        case "decisionDefinitionType" -> builder.decisionDefinitionType();
        case "rootDecisionDefinitionKey" -> builder.rootDecisionDefinitionKey();
        case "tenantId" -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyBatchOperationSortField(
      final String field, final BatchOperationSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "batchOperationKey" -> builder.batchOperationKey();
        case "state" -> builder.state();
        case "operationType" -> builder.operationType();
        case "startDate" -> builder.startDate();
        case "endDate" -> builder.endDate();
        case "actorType" -> builder.actorType();
        case "actorId" -> builder.actorId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyBatchOperationItemSortField(
      final String field, final BatchOperationItemSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "state" -> builder.state();
        case "batchOperationKey" -> builder.batchOperationKey();
        case "itemKey" -> builder.itemKey();
        case "processInstanceKey" -> builder.processInstanceKey();
        case "processedDate" -> builder.processedDate();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyProcessInstanceSortField(
      final String field, final ProcessInstanceSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "processInstanceKey" -> builder.processInstanceKey();
        case "processDefinitionId" -> builder.processDefinitionId();
        case "processDefinitionName" -> builder.processDefinitionName();
        case "processDefinitionVersion" -> builder.processDefinitionVersion();
        case "processDefinitionVersionTag" -> builder.processDefinitionVersionTag();
        case "processDefinitionKey" -> builder.processDefinitionKey();
        case "parentProcessInstanceKey" -> builder.parentProcessInstanceKey();
        case "parentElementInstanceKey" -> builder.parentFlowNodeInstanceKey();
        case "startDate" -> builder.startDate();
        case "endDate" -> builder.endDate();
        case "state" -> builder.state();
        case "hasIncident" -> builder.hasIncident();
        case "tenantId" -> builder.tenantId();
        case "businessId" -> builder.businessId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyJobSortField(final String field, final JobSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "processDefinitionKey" -> builder.processDefinitionKey();
        case "processInstanceKey" -> builder.processInstanceKey();
        case "elementInstanceKey" -> builder.elementInstanceKey();
        case "elementId" -> builder.elementId();
        case "jobKey" -> builder.jobKey();
        case "type" -> builder.type();
        case "worker" -> builder.worker();
        case "state" -> builder.state();
        case "kind" -> builder.jobKind();
        case "listenerEventType" -> builder.listenerEventType();
        case "endTime" -> builder.endTime();
        case "tenantId" -> builder.tenantId();
        case "retries" -> builder.retries();
        case "isDenied" -> builder.isDenied();
        case "deniedReason" -> builder.deniedReason();
        case "hasFailedWithRetriesLeft" -> builder.hasFailedWithRetriesLeft();
        case "errorCode" -> builder.errorCode();
        case "errorMessage" -> builder.errorMessage();
        case "deadline" -> builder.deadline();
        case "processDefinitionId" -> builder.processDefinitionId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyProcessDefinitionSortField(
      final String field, final ProcessDefinitionSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "processDefinitionKey" -> builder.processDefinitionKey();
        case "name" -> builder.name();
        case "resourceName" -> builder.resourceName();
        case "version" -> builder.version();
        case "versionTag" -> builder.versionTag();
        case "processDefinitionId" -> builder.processDefinitionId();
        case "tenantId" -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyRoleSortField(final String field, final RoleSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "name" -> builder.name();
        case "roleId" -> builder.roleId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyRoleGroupSortField(
      final String field, final RoleMemberSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case "groupId" -> {
        builder.id();
        yield List.of();
      }
      default -> List.of(ERROR_UNKNOWN_SORT_BY.formatted(field));
    };
  }

  static List<String> applyRoleUserSortField(
      final String field, final RoleMemberSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case "username" -> {
        builder.id();
        yield List.of();
      }
      default -> List.of(ERROR_UNKNOWN_SORT_BY.formatted(field));
    };
  }

  static List<String> applyRoleClientSortField(
      final String field, final RoleMemberSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case "clientId" -> {
        builder.id();
        yield List.of();
      }
      default -> List.of(ERROR_UNKNOWN_SORT_BY.formatted(field));
    };
  }

  static List<String> applyGroupSortField(final String field, final GroupSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "groupId" -> builder.groupId();
        case "name" -> builder.name();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyGroupUserSortField(
      final String field, final GroupMemberSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case "username" -> {
        builder.id();
        yield List.of();
      }
      default -> List.of(ERROR_UNKNOWN_SORT_BY.formatted(field));
    };
  }

  static List<String> applyGroupClientSortField(
      final String field, final GroupMemberSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case "clientId" -> {
        builder.id();
        yield List.of();
      }
      default -> List.of(ERROR_UNKNOWN_SORT_BY.formatted(field));
    };
  }

  static List<String> applyTenantSortField(final String field, final TenantSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "name" -> builder.name();
        case "tenantId" -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyTenantUserSortField(
      final String field, final TenantMemberSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case "username" -> {
        builder.id();
        yield List.of();
      }
      default -> List.of(ERROR_UNKNOWN_SORT_BY.formatted(field));
    };
  }

  static List<String> applyTenantGroupSortField(
      final String field, final TenantMemberSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case "groupId" -> {
        builder.id();
        yield List.of();
      }
      default -> List.of(ERROR_UNKNOWN_SORT_BY.formatted(field));
    };
  }

  static List<String> applyTenantClientSortField(
      final String field, final TenantMemberSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case "clientId" -> {
        builder.id();
        yield List.of();
      }
      default -> List.of(ERROR_UNKNOWN_SORT_BY.formatted(field));
    };
  }

  static List<String> applyMappingRuleSortField(
      final String field, final MappingRuleSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "mappingRuleId" -> builder.mappingRuleId();
        case "claimName" -> builder.claimName();
        case "claimValue" -> builder.claimValue();
        case "name" -> builder.name();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyDecisionDefinitionSortField(
      final String field, final DecisionDefinitionSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "decisionDefinitionKey" -> builder.decisionDefinitionKey();
        case "decisionDefinitionId" -> builder.decisionDefinitionId();
        case "name" -> builder.name();
        case "version" -> builder.version();
        case "decisionRequirementsId" -> builder.decisionRequirementsId();
        case "decisionRequirementsKey" -> builder.decisionRequirementsKey();
        case "decisionRequirementsName" -> builder.decisionRequirementsName();
        case "decisionRequirementsVersion" -> builder.decisionRequirementsVersion();
        case "tenantId" -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyDecisionRequirementsSortField(
      final String field, final DecisionRequirementsSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "decisionRequirementsKey" -> builder.decisionRequirementsKey();
        case "decisionRequirementsName" -> builder.name();
        case "version" -> builder.version();
        case "decisionRequirementsId" -> builder.decisionRequirementsId();
        case "tenantId" -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyElementInstanceSortField(
      final String field, final FlowNodeInstanceSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "elementInstanceKey" -> builder.flowNodeInstanceKey();
        case "processInstanceKey" -> builder.processInstanceKey();
        case "processDefinitionKey" -> builder.processDefinitionKey();
        case "processDefinitionId" -> builder.processDefinitionId();
        case "startDate" -> builder.startDate();
        case "endDate" -> builder.endDate();
        case "elementId" -> builder.flowNodeId();
        case "elementName" -> builder.flowNodeName();
        case "type" -> builder.type();
        case "state" -> builder.state();
        case "incidentKey" -> builder.incidentKey();
        case "tenantId" -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyIncidentSortField(
      final String field, final IncidentSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "incidentKey" -> builder.incidentKey();
        case "processDefinitionKey" -> builder.processDefinitionKey();
        case "processDefinitionId" -> builder.processDefinitionId();
        case "processInstanceKey" -> builder.processInstanceKey();
        case "errorType" -> builder.errorType();
        case "errorMessage" -> builder.errorMessage();
        case "elementId" -> builder.flowNodeId();
        case "elementInstanceKey" -> builder.flowNodeInstanceKey();
        case "creationTime" -> builder.creationTime();
        case "state" -> builder.state();
        case "jobKey" -> builder.jobKey();
        case "tenantId" -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyUserTaskSortField(
      final String field, final UserTaskSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "creationDate" -> builder.creationDate();
        case "completionDate" -> builder.completionDate();
        case "followUpDate" -> builder.followUpDate();
        case "dueDate" -> builder.dueDate();
        case "priority" -> builder.priority();
        case "name" -> builder.name();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyVariableSortField(
      final String field, final VariableSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "value" -> builder.value();
        case "name" -> builder.name();
        case "tenantId" -> builder.tenantId();
        case "variableKey" -> builder.variableKey();
        case "scopeKey" -> builder.scopeKey();
        case "processInstanceKey" -> builder.processInstanceKey();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyClusterVariableSortField(
      final String field, final ClusterVariableSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "value" -> builder.value();
        case "name" -> builder.name();
        case "tenantId" -> builder.tenantId();
        case "scope" -> builder.scope();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyUserTaskVariableSortField(
      final String field, final VariableSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "value" -> builder.value();
        case "name" -> builder.name();
        case "tenantId" -> builder.tenantId();
        case "variableKey" -> builder.variableKey();
        case "scopeKey" -> builder.scopeKey();
        case "processInstanceKey" -> builder.processInstanceKey();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyUserSortField(final String field, final UserSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "username" -> builder.username();
        case "name" -> builder.name();
        case "email" -> builder.email();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyMessageSubscriptionSortField(
      final String field, final MessageSubscriptionSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "messageSubscriptionKey" -> builder.messageSubscriptionKey();
        case "processDefinitionId" -> builder.processDefinitionId();
        case "processInstanceKey" -> builder.processInstanceKey();
        case "elementId" -> builder.flowNodeId();
        case "elementInstanceKey" -> builder.flowNodeInstanceKey();
        case "messageSubscriptionState" -> builder.messageSubscriptionState();
        case "lastUpdatedDate" -> builder.dateTime();
        case "messageName" -> builder.messageName();
        case "correlationKey" -> builder.correlationKey();
        case "tenantId" -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyAuthorizationSortField(
      final String field, final AuthorizationSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "ownerId" -> builder.ownerId();
        case "ownerType" -> builder.ownerType();
        case "resourceId" -> builder.resourceId();
        case "resourcePropertyName" -> builder.resourcePropertyName();
        case "resourceType" -> builder.resourceType();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyAuditLogSortField(
      final String field, final io.camunda.search.sort.AuditLogSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "actorId" -> builder.actorId();
        case "actorType" -> builder.actorType();
        case "annotation" -> builder.annotation();
        case "auditLogKey" -> builder.auditLogKey();
        case "batchOperationKey" -> builder.batchOperationKey();
        case "batchOperationType" -> builder.batchOperationType();
        case "category" -> builder.category();
        case "decisionDefinitionId" -> builder.decisionDefinitionId();
        case "decisionDefinitionKey" -> builder.decisionDefinitionKey();
        case "decisionEvaluationKey" -> builder.decisionEvaluationKey();
        case "decisionRequirementsId" -> builder.decisionRequirementsId();
        case "decisionRequirementsKey" -> builder.decisionRequirementsKey();
        case "elementInstanceKey" -> builder.elementInstanceKey();
        case "entityKey" -> builder.entityKey();
        case "entityType" -> builder.entityType();
        case "jobKey" -> builder.jobKey();
        case "operationType" -> builder.operationType();
        case "processDefinitionId" -> builder.processDefinitionId();
        case "processDefinitionKey" -> builder.processDefinitionKey();
        case "processInstanceKey" -> builder.processInstanceKey();
        case "result" -> builder.result();
        case "tenantId" -> builder.tenantId();
        case "timestamp" -> builder.timestamp();
        case "userTaskKey" -> builder.userTaskKey();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyCorrelatedMessageSubscriptionSortField(
      final String field,
      final io.camunda.search.sort.CorrelatedMessageSubscriptionSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "correlationKey" -> builder.correlationKey();
        case "correlationTime" -> builder.correlationTime();
        case "elementId" -> builder.flowNodeId();
        case "elementInstanceKey" -> builder.flowNodeInstanceKey();
        case "messageKey" -> builder.messageKey();
        case "messageName" -> builder.messageName();
        case "partitionId" -> builder.partitionId();
        case "processDefinitionId" -> builder.processDefinitionId();
        case "processDefinitionKey" -> builder.processDefinitionKey();
        case "processInstanceKey" -> builder.processInstanceKey();
        case "subscriptionKey" -> builder.subscriptionKey();
        case "tenantId" -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  public static List<String> applyProcessDefinitionInstanceStatisticsSortField(
      final String field, final ProcessDefinitionInstanceStatisticsSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "processDefinitionId" -> builder.processDefinitionId();
        case "activeInstancesWithIncidentCount" -> builder.activeInstancesWithIncidentCount();
        case "activeInstancesWithoutIncidentCount" -> builder.activeInstancesWithoutIncidentCount();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  public static List<String> applyProcessDefinitionInstanceVersionStatisticsSortField(
      final String field, final ProcessDefinitionInstanceVersionStatisticsSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "processDefinitionId" -> builder.processDefinitionId();
        case "processDefinitionKey" -> builder.processDefinitionKey();
        case "processDefinitionName" -> builder.processDefinitionName();
        case "processDefinitionVersion" -> builder.processDefinitionVersion();
        case "activeInstancesWithIncidentCount" -> builder.activeInstancesWithIncidentCount();
        case "activeInstancesWithoutIncidentCount" -> builder.activeInstancesWithoutIncidentCount();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  public static List<String> applyIncidentProcessInstanceStatisticsByErrorSortField(
      final String field, final IncidentProcessInstanceStatisticsByErrorSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "errorMessage" -> builder.errorMessage();
        case "activeInstancesWithErrorCount" -> builder.activeInstancesWithErrorCount();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  public static List<String> applyIncidentProcessInstanceStatisticsByDefinitionSortField(
      final String field, final IncidentProcessInstanceStatisticsByDefinitionSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "processDefinitionKey" -> builder.processDefinitionKey();
        case "tenantId" -> builder.tenantId();
        case "activeInstancesWithErrorCount" -> builder.activeInstancesWithErrorCount();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyGlobalTaskListenerSortField(
      final String field, final GlobalListenerSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "id" -> builder.listenerId();
        case "type" -> builder.type();
        case "afterNonGlobal" -> builder.afterNonGlobal();
        case "priority" -> builder.priority();
        case "source" -> builder.source();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static <T, B extends SortOption.AbstractBuilder<B> & ObjectBuilder<T>>
      Either<List<String>, T> toSearchQuerySort(
          final List<SearchQuerySortRequest> sorting,
          final Supplier<B> builderSupplier,
          final BiFunction<String, B, List<String>> sortFieldMapper) {
    if (sorting != null && !sorting.isEmpty()) {
      final List<String> validationErrors = new ArrayList<>();
      final var builder = builderSupplier.get();
      for (final SearchQuerySortRequest sort : sorting) {
        validationErrors.addAll(sortFieldMapper.apply(sort.field(), builder));
        applySortOrder(sort.order(), builder);
      }

      return validationErrors.isEmpty()
          ? Either.right(builder.build())
          : Either.left(validationErrors);
    }

    return Either.right(null);
  }

  private static void applySortOrder(
      final String order, final SortOption.AbstractBuilder<?> builder) {
    if ("desc".equalsIgnoreCase(order)) {
      builder.desc();
    } else {
      builder.asc();
    }
  }
}
