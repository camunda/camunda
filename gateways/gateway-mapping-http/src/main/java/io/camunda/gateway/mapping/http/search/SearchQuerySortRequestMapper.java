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

  static List<SearchQuerySortRequest<ProcessDefinitionSearchQuerySortRequest.FieldEnum>>
      fromProcessDefinitionSearchQuerySortRequest(
          final List<ProcessDefinitionSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<ProcessInstanceSearchQuerySortRequest.FieldEnum>>
      fromProcessInstanceSearchQuerySortRequest(
          final List<ProcessInstanceSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<JobSearchQuerySortRequest.FieldEnum>>
      fromJobSearchQuerySortRequest(final List<JobSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<RoleSearchQuerySortRequest.FieldEnum>>
      fromRoleSearchQuerySortRequest(final List<RoleSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<RoleUserSearchQuerySortRequest.FieldEnum>>
      fromRoleUserSearchQuerySortRequest(final List<RoleUserSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<RoleGroupSearchQuerySortRequest.FieldEnum>>
      fromRoleGroupSearchQuerySortRequest(
          final @Valid List<RoleGroupSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<RoleClientSearchQuerySortRequest.FieldEnum>>
      fromRoleClientSearchQuerySortRequest(final List<RoleClientSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<GroupSearchQuerySortRequest.FieldEnum>>
      fromGroupSearchQuerySortRequest(final List<GroupSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<GroupUserSearchQuerySortRequest.FieldEnum>>
      fromGroupUserSearchQuerySortRequest(final List<GroupUserSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<GroupClientSearchQuerySortRequest.FieldEnum>>
      fromGroupClientSearchQuerySortRequest(
          final List<GroupClientSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<TenantSearchQuerySortRequest.FieldEnum>>
      fromTenantSearchQuerySortRequest(final List<TenantSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<TenantUserSearchQuerySortRequest.FieldEnum>>
      fromTenantUserSearchQuerySortRequest(final List<TenantUserSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<TenantGroupSearchQuerySortRequest.FieldEnum>>
      fromTenantGroupSearchQuerySortRequest(
          final List<TenantGroupSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<TenantClientSearchQuerySortRequest.FieldEnum>>
      fromTenantClientSearchQuerySortRequest(
          final List<TenantClientSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<MappingRuleSearchQuerySortRequest.FieldEnum>>
      fromMappingRuleSearchQuerySortRequest(
          final List<MappingRuleSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<DecisionDefinitionSearchQuerySortRequest.FieldEnum>>
      fromDecisionDefinitionSearchQuerySortRequest(
          final List<DecisionDefinitionSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<DecisionRequirementsSearchQuerySortRequest.FieldEnum>>
      fromDecisionRequirementsSearchQuerySortRequest(
          final List<DecisionRequirementsSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<ElementInstanceSearchQuerySortRequest.FieldEnum>>
      fromElementInstanceSearchQuerySortRequest(
          final List<ElementInstanceSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<DecisionInstanceSearchQuerySortRequest.FieldEnum>>
      fromDecisionInstanceSearchQuerySortRequest(
          final List<DecisionInstanceSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<UserTaskSearchQuerySortRequest.FieldEnum>>
      fromUserTaskSearchQuerySortRequest(final List<UserTaskSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<UserTaskVariableSearchQuerySortRequest.FieldEnum>>
      fromUserTaskVariableSearchQuerySortRequest(
          final List<UserTaskVariableSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<VariableSearchQuerySortRequest.FieldEnum>>
      fromVariableSearchQuerySortRequest(final List<VariableSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<ClusterVariableSearchQuerySortRequest.FieldEnum>>
      fromClusterVariableSearchQuerySortRequest(
          final List<ClusterVariableSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<UserSearchQuerySortRequest.FieldEnum>>
      fromUserSearchQuerySortRequest(final List<UserSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<IncidentSearchQuerySortRequest.FieldEnum>>
      fromIncidentSearchQuerySortRequest(final List<IncidentSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<AuthorizationSearchQuerySortRequest.FieldEnum>>
      fromAuthorizationSearchQuerySortRequest(
          final List<AuthorizationSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<AuditLogSearchQuerySortRequest.FieldEnum>>
      fromAuditLogSearchQuerySortRequest(final List<AuditLogSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<AuditLogSearchQuerySortRequest.FieldEnum>>
      fromUserTaskAuditLogSearchRequest(final List<AuditLogSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<BatchOperationSearchQuerySortRequest.FieldEnum>>
      fromBatchOperationSearchQuerySortRequest(
          final List<BatchOperationSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<BatchOperationItemSearchQuerySortRequest.FieldEnum>>
      fromBatchOperationItemSearchQuerySortRequest(
          final List<BatchOperationItemSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<MessageSubscriptionSearchQuerySortRequest.FieldEnum>>
      fromMessageSubscriptionSearchQuerySortRequest(
          final List<MessageSubscriptionSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  static List<SearchQuerySortRequest<CorrelatedMessageSubscriptionSearchQuerySortRequest.FieldEnum>>
      fromCorrelatedMessageSubscriptionSearchQuerySortRequest(
          final List<CorrelatedMessageSubscriptionSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<
          SearchQuerySortRequest<ProcessDefinitionInstanceStatisticsQuerySortRequest.FieldEnum>>
      fromProcessDefinitionInstanceStatisticsQuerySortRequest(
          final List<ProcessDefinitionInstanceStatisticsQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<
          SearchQuerySortRequest<
              ProcessDefinitionInstanceVersionStatisticsQuerySortRequest.FieldEnum>>
      fromProcessDefinitionInstanceVersionStatisticsQuerySortRequest(
          final List<ProcessDefinitionInstanceVersionStatisticsQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<
          SearchQuerySortRequest<
              IncidentProcessInstanceStatisticsByErrorQuerySortRequest.FieldEnum>>
      fromIncidentProcessInstanceStatisticsByErrorQuerySortRequest(
          final List<IncidentProcessInstanceStatisticsByErrorQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<
          SearchQuerySortRequest<
              IncidentProcessInstanceStatisticsByDefinitionQuerySortRequest.FieldEnum>>
      fromIncidentProcessInstanceStatisticsByDefinitionQuerySortRequest(
          final List<IncidentProcessInstanceStatisticsByDefinitionQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<GlobalTaskListenerSearchQuerySortRequest.FieldEnum>>
      fromGlobalTaskListenerSearchQuerySortRequest(
          final List<GlobalTaskListenerSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  private static <T> SearchQuerySortRequest<T> createFrom(
      final T field, final SortOrderEnum order) {
    return new SearchQuerySortRequest<T>(field, order);
  }

  static List<String> applyDecisionInstanceSortField(
      final DecisionInstanceSearchQuerySortRequest.FieldEnum field,
      final DecisionInstanceSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case DECISION_EVALUATION_KEY -> builder.decisionInstanceKey();
        case DECISION_EVALUATION_INSTANCE_KEY -> builder.decisionInstanceId();
        case STATE -> builder.state();
        case EVALUATION_DATE -> builder.evaluationDate();
        case EVALUATION_FAILURE -> builder.evaluationFailure();
        case PROCESS_DEFINITION_KEY -> builder.processDefinitionKey();
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        case ELEMENT_INSTANCE_KEY -> builder.flowNodeInstanceKey();
        case DECISION_DEFINITION_KEY -> builder.decisionDefinitionKey();
        case DECISION_DEFINITION_ID -> builder.decisionDefinitionId();
        case DECISION_DEFINITION_NAME -> builder.decisionDefinitionName();
        case DECISION_DEFINITION_VERSION -> builder.decisionDefinitionVersion();
        case DECISION_DEFINITION_TYPE -> builder.decisionDefinitionType();
        case ROOT_DECISION_DEFINITION_KEY -> builder.rootDecisionDefinitionKey();
        case TENANT_ID -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyBatchOperationSortField(
      final BatchOperationSearchQuerySortRequest.FieldEnum field,
      final BatchOperationSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case BATCH_OPERATION_KEY -> builder.batchOperationKey();
        case STATE -> builder.state();
        case OPERATION_TYPE -> builder.operationType();
        case START_DATE -> builder.startDate();
        case END_DATE -> builder.endDate();
        case ACTOR_TYPE -> builder.actorType();
        case ACTOR_ID -> builder.actorId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyBatchOperationItemSortField(
      final BatchOperationItemSearchQuerySortRequest.FieldEnum field,
      final BatchOperationItemSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case STATE -> builder.state();
        case BATCH_OPERATION_KEY -> builder.batchOperationKey();
        case ITEM_KEY -> builder.itemKey();
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        case PROCESSED_DATE -> builder.processedDate();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyProcessInstanceSortField(
      final ProcessInstanceSearchQuerySortRequest.FieldEnum field,
      final ProcessInstanceSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        case PROCESS_DEFINITION_ID -> builder.processDefinitionId();
        case PROCESS_DEFINITION_NAME -> builder.processDefinitionName();
        case PROCESS_DEFINITION_VERSION -> builder.processDefinitionVersion();
        case PROCESS_DEFINITION_VERSION_TAG -> builder.processDefinitionVersionTag();
        case PROCESS_DEFINITION_KEY -> builder.processDefinitionKey();
        case PARENT_PROCESS_INSTANCE_KEY -> builder.parentProcessInstanceKey();
        case PARENT_ELEMENT_INSTANCE_KEY -> builder.parentFlowNodeInstanceKey();
        case START_DATE -> builder.startDate();
        case END_DATE -> builder.endDate();
        case STATE -> builder.state();
        case HAS_INCIDENT -> builder.hasIncident();
        case TENANT_ID -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyJobSortField(
      final JobSearchQuerySortRequest.FieldEnum field, final JobSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case PROCESS_DEFINITION_KEY -> builder.processDefinitionKey();
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        case ELEMENT_INSTANCE_KEY -> builder.elementInstanceKey();
        case ELEMENT_ID -> builder.elementId();
        case JOB_KEY -> builder.jobKey();
        case TYPE -> builder.type();
        case WORKER -> builder.worker();
        case STATE -> builder.state();
        case KIND -> builder.jobKind();
        case LISTENER_EVENT_TYPE -> builder.listenerEventType();
        case END_TIME -> builder.endTime();
        case TENANT_ID -> builder.tenantId();
        case RETRIES -> builder.retries();
        case IS_DENIED -> builder.isDenied();
        case DENIED_REASON -> builder.deniedReason();
        case HAS_FAILED_WITH_RETRIES_LEFT -> builder.hasFailedWithRetriesLeft();
        case ERROR_CODE -> builder.errorCode();
        case ERROR_MESSAGE -> builder.errorMessage();
        case DEADLINE -> builder.deadline();
        case PROCESS_DEFINITION_ID -> builder.processDefinitionId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyProcessDefinitionSortField(
      final ProcessDefinitionSearchQuerySortRequest.FieldEnum field,
      final ProcessDefinitionSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case PROCESS_DEFINITION_KEY -> builder.processDefinitionKey();
        case NAME -> builder.name();
        case RESOURCE_NAME -> builder.resourceName();
        case VERSION -> builder.version();
        case VERSION_TAG -> builder.versionTag();
        case PROCESS_DEFINITION_ID -> builder.processDefinitionId();
        case TENANT_ID -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyRoleSortField(
      final RoleSearchQuerySortRequest.FieldEnum field, final RoleSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case NAME -> builder.name();
        case ROLE_ID -> builder.roleId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyRoleGroupSortField(
      final RoleGroupSearchQuerySortRequest.FieldEnum field, final RoleMemberSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case GROUP_ID -> {
        builder.id();
        yield List.of();
      }
    };
  }

  static List<String> applyRoleUserSortField(
      final RoleUserSearchQuerySortRequest.FieldEnum field, final RoleMemberSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case USERNAME -> {
        builder.id();
        yield List.of();
      }
    };
  }

  static List<String> applyRoleClientSortField(
      final RoleClientSearchQuerySortRequest.FieldEnum field,
      final RoleMemberSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case CLIENT_ID -> {
        builder.id();
        yield List.of();
      }
    };
  }

  static List<String> applyGroupSortField(
      final GroupSearchQuerySortRequest.FieldEnum field, final GroupSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case GROUP_ID -> builder.groupId();
        case NAME -> builder.name();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyGroupUserSortField(
      final GroupUserSearchQuerySortRequest.FieldEnum field,
      final GroupMemberSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case USERNAME -> {
        builder.id();
        yield List.of();
      }
    };
  }

  static List<String> applyGroupClientSortField(
      final GroupClientSearchQuerySortRequest.FieldEnum field,
      final GroupMemberSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case CLIENT_ID -> {
        builder.id();
        yield List.of();
      }
    };
  }

  static List<String> applyTenantSortField(
      final TenantSearchQuerySortRequest.FieldEnum field, final TenantSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case NAME -> builder.name();
        case TENANT_ID -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyTenantUserSortField(
      final TenantUserSearchQuerySortRequest.FieldEnum field,
      final TenantMemberSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case USERNAME -> {
        builder.id();
        yield List.of();
      }
    };
  }

  static List<String> applyTenantGroupSortField(
      final TenantGroupSearchQuerySortRequest.FieldEnum field,
      final TenantMemberSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case GROUP_ID -> {
        builder.id();
        yield List.of();
      }
    };
  }

  static List<String> applyTenantClientSortField(
      final TenantClientSearchQuerySortRequest.FieldEnum field,
      final TenantMemberSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case CLIENT_ID -> {
        builder.id();
        yield List.of();
      }
    };
  }

  static List<String> applyMappingRuleSortField(
      final MappingRuleSearchQuerySortRequest.FieldEnum field,
      final MappingRuleSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case MAPPING_RULE_ID -> builder.mappingRuleId();
        case CLAIM_NAME -> builder.claimName();
        case CLAIM_VALUE -> builder.claimValue();
        case NAME -> builder.name();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyDecisionDefinitionSortField(
      final DecisionDefinitionSearchQuerySortRequest.FieldEnum field,
      final DecisionDefinitionSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case DECISION_DEFINITION_KEY -> builder.decisionDefinitionKey();
        case DECISION_DEFINITION_ID -> builder.decisionDefinitionId();
        case NAME -> builder.name();
        case VERSION -> builder.version();
        case DECISION_REQUIREMENTS_ID -> builder.decisionRequirementsId();
        case DECISION_REQUIREMENTS_KEY -> builder.decisionRequirementsKey();
        case DECISION_REQUIREMENTS_NAME -> builder.decisionRequirementsName();
        case DECISION_REQUIREMENTS_VERSION -> builder.decisionRequirementsVersion();
        case TENANT_ID -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyDecisionRequirementsSortField(
      final DecisionRequirementsSearchQuerySortRequest.FieldEnum field,
      final DecisionRequirementsSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case DECISION_REQUIREMENTS_KEY -> builder.decisionRequirementsKey();
        case DECISION_REQUIREMENTS_NAME -> builder.name();
        case VERSION -> builder.version();
        case DECISION_REQUIREMENTS_ID -> builder.decisionRequirementsId();
        case TENANT_ID -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyElementInstanceSortField(
      final ElementInstanceSearchQuerySortRequest.FieldEnum field,
      final FlowNodeInstanceSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case ELEMENT_INSTANCE_KEY -> builder.flowNodeInstanceKey();
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        case PROCESS_DEFINITION_KEY -> builder.processDefinitionKey();
        case PROCESS_DEFINITION_ID -> builder.processDefinitionId();
        case START_DATE -> builder.startDate();
        case END_DATE -> builder.endDate();
        case ELEMENT_ID -> builder.flowNodeId();
        case ELEMENT_NAME -> builder.flowNodeName();
        case TYPE -> builder.type();
        case STATE -> builder.state();
        case INCIDENT_KEY -> builder.incidentKey();
        case TENANT_ID -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyIncidentSortField(
      final IncidentSearchQuerySortRequest.FieldEnum field, final IncidentSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case INCIDENT_KEY -> builder.incidentKey();
        case PROCESS_DEFINITION_KEY -> builder.processDefinitionKey();
        case PROCESS_DEFINITION_ID -> builder.processDefinitionId();
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        case ERROR_TYPE -> builder.errorType();
        case ERROR_MESSAGE -> builder.errorMessage();
        case ELEMENT_ID -> builder.flowNodeId();
        case ELEMENT_INSTANCE_KEY -> builder.flowNodeInstanceKey();
        case CREATION_TIME -> builder.creationTime();
        case STATE -> builder.state();
        case JOB_KEY -> builder.jobKey();
        case TENANT_ID -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyUserTaskSortField(
      final UserTaskSearchQuerySortRequest.FieldEnum field, final UserTaskSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case CREATION_DATE -> builder.creationDate();
        case COMPLETION_DATE -> builder.completionDate();
        case FOLLOW_UP_DATE -> builder.followUpDate();
        case DUE_DATE -> builder.dueDate();
        case PRIORITY -> builder.priority();
        case NAME -> builder.name();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyVariableSortField(
      final VariableSearchQuerySortRequest.FieldEnum field, final VariableSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case VALUE -> builder.value();
        case NAME -> builder.name();
        case TENANT_ID -> builder.tenantId();
        case VARIABLE_KEY -> builder.variableKey();
        case SCOPE_KEY -> builder.scopeKey();
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyClusterVariableSortField(
      final ClusterVariableSearchQuerySortRequest.FieldEnum field,
      final ClusterVariableSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case VALUE -> builder.value();
        case NAME -> builder.name();
        case TENANT_ID -> builder.tenantId();
        case SCOPE -> builder.scope();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyUserTaskVariableSortField(
      final UserTaskVariableSearchQuerySortRequest.FieldEnum field,
      final VariableSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case VALUE -> builder.value();
        case NAME -> builder.name();
        case TENANT_ID -> builder.tenantId();
        case VARIABLE_KEY -> builder.variableKey();
        case SCOPE_KEY -> builder.scopeKey();
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyUserSortField(
      final UserSearchQuerySortRequest.FieldEnum field, final UserSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case USERNAME -> builder.username();
        case NAME -> builder.name();
        case EMAIL -> builder.email();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyMessageSubscriptionSortField(
      final MessageSubscriptionSearchQuerySortRequest.FieldEnum field,
      final MessageSubscriptionSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case MESSAGE_SUBSCRIPTION_KEY -> builder.messageSubscriptionKey();
        case PROCESS_DEFINITION_ID -> builder.processDefinitionId();
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        case ELEMENT_ID -> builder.flowNodeId();
        case ELEMENT_INSTANCE_KEY -> builder.flowNodeInstanceKey();
        case MESSAGE_SUBSCRIPTION_STATE -> builder.messageSubscriptionState();
        case LAST_UPDATED_DATE -> builder.dateTime();
        case MESSAGE_NAME -> builder.messageName();
        case CORRELATION_KEY -> builder.correlationKey();
        case TENANT_ID -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyAuthorizationSortField(
      final AuthorizationSearchQuerySortRequest.FieldEnum field,
      final AuthorizationSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case OWNER_ID -> builder.ownerId();
        case OWNER_TYPE -> builder.ownerType();
        case RESOURCE_ID -> builder.resourceId();
        case RESOURCE_PROPERTY_NAME -> builder.resourcePropertyName();
        case RESOURCE_TYPE -> builder.resourceType();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyAuditLogSortField(
      final AuditLogSearchQuerySortRequest.FieldEnum field,
      final io.camunda.search.sort.AuditLogSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case ACTOR_ID -> builder.actorId();
        case ACTOR_TYPE -> builder.actorType();
        case ANNOTATION -> builder.annotation();
        case AUDIT_LOG_KEY -> builder.auditLogKey();
        case BATCH_OPERATION_KEY -> builder.batchOperationKey();
        case BATCH_OPERATION_TYPE -> builder.batchOperationType();
        case CATEGORY -> builder.category();
        case DECISION_DEFINITION_ID -> builder.decisionDefinitionId();
        case DECISION_DEFINITION_KEY -> builder.decisionDefinitionKey();
        case DECISION_EVALUATION_KEY -> builder.decisionEvaluationKey();
        case DECISION_REQUIREMENTS_ID -> builder.decisionRequirementsId();
        case DECISION_REQUIREMENTS_KEY -> builder.decisionRequirementsKey();
        case ELEMENT_INSTANCE_KEY -> builder.elementInstanceKey();
        case ENTITY_KEY -> builder.entityKey();
        case ENTITY_TYPE -> builder.entityType();
        case JOB_KEY -> builder.jobKey();
        case OPERATION_TYPE -> builder.operationType();
        case PROCESS_DEFINITION_ID -> builder.processDefinitionId();
        case PROCESS_DEFINITION_KEY -> builder.processDefinitionKey();
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        case RESULT -> builder.result();
        case TENANT_ID -> builder.tenantId();
        case TIMESTAMP -> builder.timestamp();
        case USER_TASK_KEY -> builder.userTaskKey();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyCorrelatedMessageSubscriptionSortField(
      final CorrelatedMessageSubscriptionSearchQuerySortRequest.FieldEnum field,
      final io.camunda.search.sort.CorrelatedMessageSubscriptionSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case CORRELATION_KEY -> builder.correlationKey();
        case CORRELATION_TIME -> builder.correlationTime();
        case ELEMENT_ID -> builder.flowNodeId();
        case ELEMENT_INSTANCE_KEY -> builder.flowNodeInstanceKey();
        case MESSAGE_KEY -> builder.messageKey();
        case MESSAGE_NAME -> builder.messageName();
        case PARTITION_ID -> builder.partitionId();
        case PROCESS_DEFINITION_ID -> builder.processDefinitionId();
        case PROCESS_DEFINITION_KEY -> builder.processDefinitionKey();
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        case SUBSCRIPTION_KEY -> builder.subscriptionKey();
        case TENANT_ID -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  public static List<String> applyProcessDefinitionInstanceStatisticsSortField(
      final ProcessDefinitionInstanceStatisticsQuerySortRequest.FieldEnum field,
      final ProcessDefinitionInstanceStatisticsSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case PROCESS_DEFINITION_ID -> builder.processDefinitionId();
        case ACTIVE_INSTANCES_WITH_INCIDENT_COUNT -> builder.activeInstancesWithIncidentCount();
        case ACTIVE_INSTANCES_WITHOUT_INCIDENT_COUNT ->
            builder.activeInstancesWithoutIncidentCount();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  public static List<String> applyProcessDefinitionInstanceVersionStatisticsSortField(
      final ProcessDefinitionInstanceVersionStatisticsQuerySortRequest.FieldEnum field,
      final ProcessDefinitionInstanceVersionStatisticsSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case PROCESS_DEFINITION_ID -> builder.processDefinitionId();
        case PROCESS_DEFINITION_KEY -> builder.processDefinitionKey();
        case PROCESS_DEFINITION_NAME -> builder.processDefinitionName();
        case PROCESS_DEFINITION_VERSION -> builder.processDefinitionVersion();
        case ACTIVE_INSTANCES_WITH_INCIDENT_COUNT -> builder.activeInstancesWithIncidentCount();
        case ACTIVE_INSTANCES_WITHOUT_INCIDENT_COUNT ->
            builder.activeInstancesWithoutIncidentCount();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  public static List<String> applyIncidentProcessInstanceStatisticsByErrorSortField(
      final IncidentProcessInstanceStatisticsByErrorQuerySortRequest.FieldEnum field,
      final IncidentProcessInstanceStatisticsByErrorSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case ERROR_MESSAGE -> builder.errorMessage();
        case ACTIVE_INSTANCES_WITH_ERROR_COUNT -> builder.activeInstancesWithErrorCount();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  public static List<String> applyIncidentProcessInstanceStatisticsByDefinitionSortField(
      final IncidentProcessInstanceStatisticsByDefinitionQuerySortRequest.FieldEnum field,
      final IncidentProcessInstanceStatisticsByDefinitionSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case PROCESS_DEFINITION_KEY -> builder.processDefinitionKey();
        case TENANT_ID -> builder.tenantId();
        case ACTIVE_INSTANCES_WITH_ERROR_COUNT -> builder.activeInstancesWithErrorCount();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static List<String> applyGlobalTaskListenerSortField(
      final GlobalTaskListenerSearchQuerySortRequest.FieldEnum field,
      final GlobalListenerSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case ID -> builder.listenerId();
        case TYPE -> builder.type();
        case AFTER_NON_GLOBAL -> builder.afterNonGlobal();
        case PRIORITY -> builder.priority();
        case SOURCE -> builder.source();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  static <T, B extends SortOption.AbstractBuilder<B> & ObjectBuilder<T>, F>
      Either<List<String>, T> toSearchQuerySort(
          final List<SearchQuerySortRequest<F>> sorting,
          final Supplier<B> builderSupplier,
          final BiFunction<F, B, List<String>> sortFieldMapper) {
    if (sorting != null && !sorting.isEmpty()) {
      final List<String> validationErrors = new ArrayList<>();
      final var builder = builderSupplier.get();
      for (final SearchQuerySortRequest<F> sort : sorting) {
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
      final SortOrderEnum order, final SortOption.AbstractBuilder<?> builder) {
    if (order == SortOrderEnum.DESC) {
      builder.desc();
    } else {
      builder.asc();
    }
  }
}
