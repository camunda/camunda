/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuditLogSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuthorizationSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedBatchOperationItemSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedBatchOperationSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedClusterVariableSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedCorrelatedMessageSubscriptionSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionDefinitionSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionInstanceSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionRequirementsSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedElementInstanceSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGlobalTaskListenerSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupClientSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupUserSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMappingRuleSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMessageSubscriptionSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleClientSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleGroupSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleUserSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantClientSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantGroupSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantUserSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskAuditLogSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskVariableSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedVariableSearchQueryRequestStrictContract;
import io.camunda.gateway.protocol.model.AuditLogSearchQueryRequest;
import io.camunda.gateway.protocol.model.AuthorizationSearchQuery;
import io.camunda.gateway.protocol.model.BatchOperationItemSearchQuery;
import io.camunda.gateway.protocol.model.BatchOperationSearchQuery;
import io.camunda.gateway.protocol.model.ClusterVariableSearchQueryRequest;
import io.camunda.gateway.protocol.model.CorrelatedMessageSubscriptionSearchQuery;
import io.camunda.gateway.protocol.model.DecisionDefinitionSearchQuery;
import io.camunda.gateway.protocol.model.DecisionInstanceSearchQuery;
import io.camunda.gateway.protocol.model.DecisionRequirementsSearchQuery;
import io.camunda.gateway.protocol.model.ElementInstanceSearchQuery;
import io.camunda.gateway.protocol.model.GlobalTaskListenerSearchQueryRequest;
import io.camunda.gateway.protocol.model.GroupClientSearchQueryRequest;
import io.camunda.gateway.protocol.model.GroupSearchQueryRequest;
import io.camunda.gateway.protocol.model.GroupUserSearchQueryRequest;
import io.camunda.gateway.protocol.model.IncidentSearchQuery;
import io.camunda.gateway.protocol.model.JobSearchQuery;
import io.camunda.gateway.protocol.model.MappingRuleSearchQueryRequest;
import io.camunda.gateway.protocol.model.MessageSubscriptionSearchQuery;
import io.camunda.gateway.protocol.model.ProcessDefinitionSearchQuery;
import io.camunda.gateway.protocol.model.ProcessInstanceSearchQuery;
import io.camunda.gateway.protocol.model.RoleClientSearchQueryRequest;
import io.camunda.gateway.protocol.model.RoleGroupSearchQueryRequest;
import io.camunda.gateway.protocol.model.RoleSearchQueryRequest;
import io.camunda.gateway.protocol.model.RoleUserSearchQueryRequest;
import io.camunda.gateway.protocol.model.TenantClientSearchQueryRequest;
import io.camunda.gateway.protocol.model.TenantGroupSearchQueryRequest;
import io.camunda.gateway.protocol.model.TenantSearchQueryRequest;
import io.camunda.gateway.protocol.model.TenantUserSearchQueryRequest;
import io.camunda.gateway.protocol.model.UserSearchQueryRequest;
import io.camunda.gateway.protocol.model.UserTaskAuditLogSearchQueryRequest;
import io.camunda.gateway.protocol.model.UserTaskSearchQuery;
import io.camunda.gateway.protocol.model.UserTaskVariableSearchQueryRequest;
import io.camunda.gateway.protocol.model.VariableSearchQuery;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.BatchOperationItemQuery;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.ClusterVariableQuery;
import io.camunda.search.query.CorrelatedMessageSubscriptionQuery;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.GlobalListenerQuery;
import io.camunda.search.query.GroupMemberQuery;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.JobQuery;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.RoleMemberQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.TenantMemberQuery;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.zeebe.util.Either;
import jakarta.annotation.Generated;
import org.springframework.http.ProblemDetail;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedSearchQueryRequestMapper {

  private GeneratedSearchQueryRequestMapper() {}

  public static Either<ProblemDetail, ProcessInstanceQuery> toProcessInstanceQuery(
      final ProcessInstanceSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.processInstanceSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromProcessInstanceSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::processInstance,
            SearchQuerySortRequestMapper::applyProcessInstanceSortField);
    final var filter = SearchQueryFilterMapper.toProcessInstanceFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::processInstanceSearchQuery);
  }

  public static Either<ProblemDetail, JobQuery> toJobQuery(final JobSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.jobSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromJobSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::job,
            SearchQuerySortRequestMapper::applyJobSortField);
    final var filter = SearchQueryFilterMapper.toJobFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::jobSearchQuery);
  }

  public static Either<ProblemDetail, RoleQuery> toRoleQuery(final RoleSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromRoleSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::role,
            SearchQuerySortRequestMapper::applyRoleSortField);
    final var filter = SearchQueryFilterMapper.toRoleFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::roleSearchQuery);
  }

  public static Either<ProblemDetail, RoleMemberQuery> toRoleMemberQuery(
      final RoleUserSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleMemberSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromRoleUserSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::roleMember,
            SearchQuerySortRequestMapper::applyRoleUserSortField);
    final var filter = FilterBuilders.roleMember().build();
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::roleMemberSearchQuery);
  }

  public static Either<ProblemDetail, RoleMemberQuery> toRoleMemberQuery(
      final RoleGroupSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleMemberSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromRoleGroupSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::roleMember,
            SearchQuerySortRequestMapper::applyRoleGroupSortField);
    final var filter = FilterBuilders.roleMember().build();
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::roleMemberSearchQuery);
  }

  public static Either<ProblemDetail, RoleMemberQuery> toRoleMemberQuery(
      final RoleClientSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleMemberSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromRoleClientSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::roleMember,
            SearchQuerySortRequestMapper::applyRoleClientSortField);
    final var filter = FilterBuilders.roleMember().build();
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::roleMemberSearchQuery);
  }

  public static Either<ProblemDetail, GroupQuery> toGroupQuery(
      final GroupSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.groupSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromGroupSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::group,
            SearchQuerySortRequestMapper::applyGroupSortField);
    final var filter = SearchQueryFilterMapper.toGroupFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::groupSearchQuery);
  }

  public static Either<ProblemDetail, GroupMemberQuery> toGroupMemberQuery(
      final GroupUserSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.groupMemberSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromGroupUserSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::groupMember,
            SearchQuerySortRequestMapper::applyGroupUserSortField);
    final var filter = FilterBuilders.groupMember().build();
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::groupMemberSearchQuery);
  }

  public static Either<ProblemDetail, GroupMemberQuery> toGroupMemberQuery(
      final GroupClientSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.groupMemberSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromGroupClientSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::groupMember,
            SearchQuerySortRequestMapper::applyGroupClientSortField);
    final var filter = FilterBuilders.groupMember().build();
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::groupMemberSearchQuery);
  }

  public static Either<ProblemDetail, TenantQuery> toTenantQuery(
      final TenantSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromTenantSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::tenant,
            SearchQuerySortRequestMapper::applyTenantSortField);
    final var filter = SearchQueryFilterMapper.toTenantFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::tenantSearchQuery);
  }

  public static Either<ProblemDetail, TenantMemberQuery> toTenantMemberQuery(
      final TenantGroupSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantMemberSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromTenantGroupSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::tenantMember,
            SearchQuerySortRequestMapper::applyTenantGroupSortField);
    final var filter = FilterBuilders.tenantMember().build();
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::tenantMemberSearchQuery);
  }

  public static Either<ProblemDetail, TenantMemberQuery> toTenantMemberQuery(
      final TenantUserSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantMemberSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromTenantUserSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::tenantMember,
            SearchQuerySortRequestMapper::applyTenantUserSortField);
    final var filter = FilterBuilders.tenantMember().build();
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::tenantMemberSearchQuery);
  }

  public static Either<ProblemDetail, TenantMemberQuery> toTenantMemberQuery(
      final TenantClientSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantMemberSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromTenantClientSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::tenantMember,
            SearchQuerySortRequestMapper::applyTenantClientSortField);
    final var filter = FilterBuilders.tenantMember().build();
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::tenantMemberSearchQuery);
  }

  public static Either<ProblemDetail, MappingRuleQuery> toMappingRuleQuery(
      final MappingRuleSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.mappingRuleSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromMappingRuleSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::mappingRule,
            SearchQuerySortRequestMapper::applyMappingRuleSortField);
    final var filter = SearchQueryFilterMapper.toMappingRuleFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::mappingRuleSearchQuery);
  }

  public static Either<ProblemDetail, ProcessDefinitionQuery> toProcessDefinitionQuery(
      final ProcessDefinitionSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.processDefinitionSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromProcessDefinitionSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::processDefinition,
            SearchQuerySortRequestMapper::applyProcessDefinitionSortField);
    final var filter = SearchQueryFilterMapper.toProcessDefinitionFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::processDefinitionSearchQuery);
  }

  public static Either<ProblemDetail, DecisionDefinitionQuery> toDecisionDefinitionQuery(
      final DecisionDefinitionSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionDefinitionSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromDecisionDefinitionSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::decisionDefinition,
            SearchQuerySortRequestMapper::applyDecisionDefinitionSortField);
    final var filter = SearchQueryFilterMapper.toDecisionDefinitionFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::decisionDefinitionSearchQuery);
  }

  public static Either<ProblemDetail, DecisionRequirementsQuery> toDecisionRequirementsQuery(
      final DecisionRequirementsSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionRequirementsSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromDecisionRequirementsSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::decisionRequirements,
            SearchQuerySortRequestMapper::applyDecisionRequirementsSortField);
    final var filter = SearchQueryFilterMapper.toDecisionRequirementsFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::decisionRequirementsSearchQuery);
  }

  public static Either<ProblemDetail, FlowNodeInstanceQuery> toElementInstanceQuery(
      final ElementInstanceSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.flownodeInstanceSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromElementInstanceSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::flowNodeInstance,
            SearchQuerySortRequestMapper::applyElementInstanceSortField);
    final var filter = SearchQueryFilterMapper.toElementInstanceFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::flownodeInstanceSearchQuery);
  }

  public static Either<ProblemDetail, DecisionInstanceQuery> toDecisionInstanceQuery(
      final DecisionInstanceSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionInstanceSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromDecisionInstanceSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::decisionInstance,
            SearchQuerySortRequestMapper::applyDecisionInstanceSortField);
    final var filter = SearchQueryFilterMapper.toDecisionInstanceFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::decisionInstanceSearchQuery);
  }

  public static Either<ProblemDetail, UserTaskQuery> toUserTaskQuery(
      final UserTaskSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.userTaskSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromUserTaskSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::userTask,
            SearchQuerySortRequestMapper::applyUserTaskSortField);
    final var filter = SearchQueryFilterMapper.toUserTaskFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::userTaskSearchQuery);
  }

  public static Either<ProblemDetail, VariableQuery> toUserTaskVariableQuery(
      final UserTaskVariableSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.variableSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromUserTaskVariableSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::variable,
            SearchQuerySortRequestMapper::applyUserTaskVariableSortField);
    final var filter = SearchQueryFilterMapper.toUserTaskVariableFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::variableSearchQuery);
  }

  public static Either<ProblemDetail, VariableQuery> toVariableQuery(
      final VariableSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.variableSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromVariableSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::variable,
            SearchQuerySortRequestMapper::applyVariableSortField);
    final var filter = SearchQueryFilterMapper.toVariableFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::variableSearchQuery);
  }

  public static Either<ProblemDetail, ClusterVariableQuery> toClusterVariableQuery(
      final ClusterVariableSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.clusterVariableSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromClusterVariableSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::clusterVariable,
            SearchQuerySortRequestMapper::applyClusterVariableSortField);
    final var filter = SearchQueryFilterMapper.toClusterVariableFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::clusterVariableSearchQuery);
  }

  public static Either<ProblemDetail, UserQuery> toUserQuery(final UserSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.userSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromUserSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::user,
            SearchQuerySortRequestMapper::applyUserSortField);
    final var filter = SearchQueryFilterMapper.toUserFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::userSearchQuery);
  }

  public static Either<ProblemDetail, IncidentQuery> toIncidentQuery(
      final IncidentSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.incidentSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromIncidentSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::incident,
            SearchQuerySortRequestMapper::applyIncidentSortField);
    final var filter = SearchQueryFilterMapper.toIncidentFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::incidentSearchQuery);
  }

  public static Either<ProblemDetail, BatchOperationQuery> toBatchOperationQuery(
      final BatchOperationSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.batchOperationQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromBatchOperationSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::batchOperation,
            SearchQuerySortRequestMapper::applyBatchOperationSortField);
    final var filter = SearchQueryFilterMapper.toBatchOperationFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::batchOperationQuery);
  }

  public static Either<ProblemDetail, BatchOperationItemQuery> toBatchOperationItemQuery(
      final BatchOperationItemSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.batchOperationItemQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromBatchOperationItemSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::batchOperationItem,
            SearchQuerySortRequestMapper::applyBatchOperationItemSortField);
    final var filter = SearchQueryFilterMapper.toBatchOperationItemFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::batchOperationItemQuery);
  }

  public static Either<ProblemDetail, AuthorizationQuery> toAuthorizationQuery(
      final AuthorizationSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.authorizationSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromAuthorizationSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::authorization,
            SearchQuerySortRequestMapper::applyAuthorizationSortField);
    final var filter = SearchQueryFilterMapper.toAuthorizationFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::authorizationSearchQuery);
  }

  public static Either<ProblemDetail, AuditLogQuery> toAuditLogQuery(
      final AuditLogSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.auditLogSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromAuditLogSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::auditLog,
            SearchQuerySortRequestMapper::applyAuditLogSortField);
    final var filter = SearchQueryFilterMapper.toAuditLogFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::auditLogSearchQuery);
  }

  public static Either<ProblemDetail, AuditLogQuery> toUserTaskAuditLogQuery(
      final UserTaskAuditLogSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.auditLogSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromUserTaskAuditLogSearchRequest(request.getSort()),
            SortOptionBuilders::auditLog,
            SearchQuerySortRequestMapper::applyAuditLogSortField);
    final var filter = SearchQueryFilterMapper.toUserTaskAuditLogFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::auditLogSearchQuery);
  }

  public static Either<ProblemDetail, MessageSubscriptionQuery> toMessageSubscriptionQuery(
      final MessageSubscriptionSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.messageSubscriptionSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromMessageSubscriptionSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::messageSubscription,
            SearchQuerySortRequestMapper::applyMessageSubscriptionSortField);
    final var filter = SearchQueryFilterMapper.toMessageSubscriptionFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::messageSubscriptionSearchQuery);
  }

  public static Either<ProblemDetail, CorrelatedMessageSubscriptionQuery>
      toCorrelatedMessageSubscriptionQuery(final CorrelatedMessageSubscriptionSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.correlatedMessageSubscriptionSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromCorrelatedMessageSubscriptionSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::correlatedMessageSubscription,
            SearchQuerySortRequestMapper::applyCorrelatedMessageSubscriptionSortField);
    final var filter =
        SearchQueryFilterMapper.toCorrelatedMessageSubscriptionFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::correlatedMessageSubscriptionSearchQuery);
  }

  public static Either<ProblemDetail, GlobalListenerQuery> toGlobalTaskListenerQuery(
      final GlobalTaskListenerSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.globalListenerSearchQuery().build());
    }
    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromGlobalTaskListenerSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::globalListener,
            SearchQuerySortRequestMapper::applyGlobalTaskListenerSortField);
    final var filter = SearchQueryFilterMapper.toGlobalTaskListenerFilter(request.getFilter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::globalListenerSearchQuery);
  }

  // -- Strict contract overloads (no protocol model dependency) --

  public static Either<ProblemDetail, ProcessInstanceQuery> toProcessInstanceQueryStrict(
      final GeneratedProcessInstanceSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.processInstanceSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::processInstance,
            SearchQuerySortRequestMapper::applyProcessInstanceSortField);
    final var filter = SearchQueryFilterMapper.toProcessInstanceFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::processInstanceSearchQuery);
  }

  public static Either<ProblemDetail, JobQuery> toJobQueryStrict(
      final GeneratedJobSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.jobSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests, SortOptionBuilders::job, SearchQuerySortRequestMapper::applyJobSortField);
    final var filter = SearchQueryFilterMapper.toJobFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::jobSearchQuery);
  }

  public static Either<ProblemDetail, RoleQuery> toRoleQueryStrict(
      final GeneratedRoleSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::role,
            SearchQuerySortRequestMapper::applyRoleSortField);
    final var filter = SearchQueryFilterMapper.toRoleFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::roleSearchQuery);
  }

  public static Either<ProblemDetail, RoleMemberQuery> toRoleUserQueryStrict(
      final GeneratedRoleUserSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleMemberSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::roleMember,
            SearchQuerySortRequestMapper::applyRoleUserSortField);
    final var filter = FilterBuilders.roleMember().build();
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::roleMemberSearchQuery);
  }

  public static Either<ProblemDetail, RoleMemberQuery> toRoleGroupQueryStrict(
      final GeneratedRoleGroupSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleMemberSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::roleMember,
            SearchQuerySortRequestMapper::applyRoleGroupSortField);
    final var filter = FilterBuilders.roleMember().build();
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::roleMemberSearchQuery);
  }

  public static Either<ProblemDetail, RoleMemberQuery> toRoleClientQueryStrict(
      final GeneratedRoleClientSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleMemberSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::roleMember,
            SearchQuerySortRequestMapper::applyRoleClientSortField);
    final var filter = FilterBuilders.roleMember().build();
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::roleMemberSearchQuery);
  }

  public static Either<ProblemDetail, GroupQuery> toGroupQueryStrict(
      final GeneratedGroupSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.groupSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::group,
            SearchQuerySortRequestMapper::applyGroupSortField);
    final var filter = SearchQueryFilterMapper.toGroupFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::groupSearchQuery);
  }

  public static Either<ProblemDetail, GroupMemberQuery> toGroupUserQueryStrict(
      final GeneratedGroupUserSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.groupMemberSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::groupMember,
            SearchQuerySortRequestMapper::applyGroupUserSortField);
    final var filter = FilterBuilders.groupMember().build();
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::groupMemberSearchQuery);
  }

  public static Either<ProblemDetail, GroupMemberQuery> toGroupClientQueryStrict(
      final GeneratedGroupClientSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.groupMemberSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::groupMember,
            SearchQuerySortRequestMapper::applyGroupClientSortField);
    final var filter = FilterBuilders.groupMember().build();
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::groupMemberSearchQuery);
  }

  public static Either<ProblemDetail, TenantQuery> toTenantQueryStrict(
      final GeneratedTenantSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::tenant,
            SearchQuerySortRequestMapper::applyTenantSortField);
    final var filter = SearchQueryFilterMapper.toTenantFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::tenantSearchQuery);
  }

  public static Either<ProblemDetail, TenantMemberQuery> toTenantGroupQueryStrict(
      final GeneratedTenantGroupSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantMemberSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::tenantMember,
            SearchQuerySortRequestMapper::applyTenantGroupSortField);
    final var filter = FilterBuilders.tenantMember().build();
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::tenantMemberSearchQuery);
  }

  public static Either<ProblemDetail, TenantMemberQuery> toTenantUserQueryStrict(
      final GeneratedTenantUserSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantMemberSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::tenantMember,
            SearchQuerySortRequestMapper::applyTenantUserSortField);
    final var filter = FilterBuilders.tenantMember().build();
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::tenantMemberSearchQuery);
  }

  public static Either<ProblemDetail, TenantMemberQuery> toTenantClientQueryStrict(
      final GeneratedTenantClientSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantMemberSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::tenantMember,
            SearchQuerySortRequestMapper::applyTenantClientSortField);
    final var filter = FilterBuilders.tenantMember().build();
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::tenantMemberSearchQuery);
  }

  public static Either<ProblemDetail, MappingRuleQuery> toMappingRuleQueryStrict(
      final GeneratedMappingRuleSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.mappingRuleSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::mappingRule,
            SearchQuerySortRequestMapper::applyMappingRuleSortField);
    final var filter = SearchQueryFilterMapper.toMappingRuleFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::mappingRuleSearchQuery);
  }

  public static Either<ProblemDetail, ProcessDefinitionQuery> toProcessDefinitionQueryStrict(
      final GeneratedProcessDefinitionSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.processDefinitionSearchQuery().build());
    }

    // Validate isLatestVersion constraints before processing
    final var isLatestVersionValidation = validateIsLatestVersionConstraintsStrict(request);
    if (isLatestVersionValidation.isLeft()) {
      return Either.left(isLatestVersionValidation.getLeft());
    }

    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::processDefinition,
            SearchQuerySortRequestMapper::applyProcessDefinitionSortField);
    final var filter = SearchQueryFilterMapper.toProcessDefinitionFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::processDefinitionSearchQuery);
  }

  public static Either<ProblemDetail, DecisionDefinitionQuery> toDecisionDefinitionQueryStrict(
      final GeneratedDecisionDefinitionSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionDefinitionSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::decisionDefinition,
            SearchQuerySortRequestMapper::applyDecisionDefinitionSortField);
    final var filter = SearchQueryFilterMapper.toDecisionDefinitionFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::decisionDefinitionSearchQuery);
  }

  public static Either<ProblemDetail, DecisionRequirementsQuery> toDecisionRequirementsQueryStrict(
      final GeneratedDecisionRequirementsSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionRequirementsSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::decisionRequirements,
            SearchQuerySortRequestMapper::applyDecisionRequirementsSortField);
    final var filter = SearchQueryFilterMapper.toDecisionRequirementsFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::decisionRequirementsSearchQuery);
  }

  public static Either<ProblemDetail, FlowNodeInstanceQuery> toElementInstanceQueryStrict(
      final GeneratedElementInstanceSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.flownodeInstanceSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::flowNodeInstance,
            SearchQuerySortRequestMapper::applyElementInstanceSortField);
    final var filter = SearchQueryFilterMapper.toElementInstanceFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::flownodeInstanceSearchQuery);
  }

  public static Either<ProblemDetail, DecisionInstanceQuery> toDecisionInstanceQueryStrict(
      final GeneratedDecisionInstanceSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionInstanceSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::decisionInstance,
            SearchQuerySortRequestMapper::applyDecisionInstanceSortField);
    final var filter = SearchQueryFilterMapper.toDecisionInstanceFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::decisionInstanceSearchQuery);
  }

  public static Either<ProblemDetail, UserTaskQuery> toUserTaskQueryStrict(
      final GeneratedUserTaskSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.userTaskSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::userTask,
            SearchQuerySortRequestMapper::applyUserTaskSortField);
    final var filter = SearchQueryFilterMapper.toUserTaskFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::userTaskSearchQuery);
  }

  public static Either<ProblemDetail, VariableQuery> toUserTaskVariableQueryStrict(
      final GeneratedUserTaskVariableSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.variableSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::variable,
            SearchQuerySortRequestMapper::applyUserTaskVariableSortField);
    final var filter = SearchQueryFilterMapper.toUserTaskVariableFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::variableSearchQuery);
  }

  public static Either<ProblemDetail, VariableQuery> toVariableQueryStrict(
      final GeneratedVariableSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.variableSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::variable,
            SearchQuerySortRequestMapper::applyVariableSortField);
    final var filter = SearchQueryFilterMapper.toVariableFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::variableSearchQuery);
  }

  public static Either<ProblemDetail, ClusterVariableQuery> toClusterVariableQueryStrict(
      final GeneratedClusterVariableSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.clusterVariableSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::clusterVariable,
            SearchQuerySortRequestMapper::applyClusterVariableSortField);
    final var filter = SearchQueryFilterMapper.toClusterVariableFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::clusterVariableSearchQuery);
  }

  public static Either<ProblemDetail, UserQuery> toUserQueryStrict(
      final GeneratedUserSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.userSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::user,
            SearchQuerySortRequestMapper::applyUserSortField);
    final var filter = SearchQueryFilterMapper.toUserFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::userSearchQuery);
  }

  public static Either<ProblemDetail, IncidentQuery> toIncidentQueryStrict(
      final GeneratedIncidentSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.incidentSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::incident,
            SearchQuerySortRequestMapper::applyIncidentSortField);
    final var filter = SearchQueryFilterMapper.toIncidentFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::incidentSearchQuery);
  }

  public static Either<ProblemDetail, BatchOperationQuery> toBatchOperationQueryStrict(
      final GeneratedBatchOperationSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.batchOperationQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::batchOperation,
            SearchQuerySortRequestMapper::applyBatchOperationSortField);
    final var filter = SearchQueryFilterMapper.toBatchOperationFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::batchOperationQuery);
  }

  public static Either<ProblemDetail, BatchOperationItemQuery> toBatchOperationItemQueryStrict(
      final GeneratedBatchOperationItemSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.batchOperationItemQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::batchOperationItem,
            SearchQuerySortRequestMapper::applyBatchOperationItemSortField);
    final var filter = SearchQueryFilterMapper.toBatchOperationItemFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::batchOperationItemQuery);
  }

  public static Either<ProblemDetail, AuthorizationQuery> toAuthorizationQueryStrict(
      final GeneratedAuthorizationSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.authorizationSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::authorization,
            SearchQuerySortRequestMapper::applyAuthorizationSortField);
    final var filter = SearchQueryFilterMapper.toAuthorizationFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::authorizationSearchQuery);
  }

  public static Either<ProblemDetail, AuditLogQuery> toAuditLogQueryStrict(
      final GeneratedAuditLogSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.auditLogSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::auditLog,
            SearchQuerySortRequestMapper::applyAuditLogSortField);
    final var filter = SearchQueryFilterMapper.toAuditLogFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::auditLogSearchQuery);
  }

  public static Either<ProblemDetail, AuditLogQuery> toUserTaskAuditLogQueryStrict(
      final GeneratedUserTaskAuditLogSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.auditLogSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests = java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::auditLog,
            SearchQuerySortRequestMapper::applyAuditLogSortField);
    final var filter = SearchQueryFilterMapper.toUserTaskAuditLogFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::auditLogSearchQuery);
  }

  public static Either<ProblemDetail, MessageSubscriptionQuery> toMessageSubscriptionQueryStrict(
      final GeneratedMessageSubscriptionSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.messageSubscriptionSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::messageSubscription,
            SearchQuerySortRequestMapper::applyMessageSubscriptionSortField);
    final var filter = SearchQueryFilterMapper.toMessageSubscriptionFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::messageSubscriptionSearchQuery);
  }

  public static Either<ProblemDetail, CorrelatedMessageSubscriptionQuery>
      toCorrelatedMessageSubscriptionQueryStrict(
          final GeneratedCorrelatedMessageSubscriptionSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.correlatedMessageSubscriptionSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::correlatedMessageSubscription,
            SearchQuerySortRequestMapper::applyCorrelatedMessageSubscriptionSortField);
    final var filter =
        SearchQueryFilterMapper.toCorrelatedMessageSubscriptionFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::correlatedMessageSubscriptionSearchQuery);
  }

  public static Either<ProblemDetail, GlobalListenerQuery> toGlobalTaskListenerQueryStrict(
      final GeneratedGlobalTaskListenerSearchQueryRequestStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.globalListenerSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::globalListener,
            SearchQuerySortRequestMapper::applyGlobalTaskListenerSortField);
    final var filter = SearchQueryFilterMapper.toGlobalTaskListenerFilter(request.filter());
    return SearchQueryRequestMapper.buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::globalListenerSearchQuery);
  }

  private static Either<ProblemDetail, Void> validateIsLatestVersionConstraintsStrict(
      final GeneratedProcessDefinitionSearchQueryRequestStrictContract request) {
    final var filter = request.filter();
    if (filter == null || filter.isLatestVersion() == null || !filter.isLatestVersion()) {
      return Either.right(null);
    }

    final java.util.List<String> violations = new java.util.ArrayList<>();

    final var page = request.page();
    if (page != null) {
      if (page.before() != null) {
        violations.add(
            io.camunda.gateway.mapping.http.validator.ErrorMessages
                .ERROR_MESSAGE_UNSUPPORTED_PAGINATION_WITH_IS_LATEST_VERSION
                .formatted("before"));
      }
      if (page.from() != null) {
        violations.add(
            io.camunda.gateway.mapping.http.validator.ErrorMessages
                .ERROR_MESSAGE_UNSUPPORTED_PAGINATION_WITH_IS_LATEST_VERSION
                .formatted("from"));
      }
    }

    final var sort = request.sort();
    if (sort != null && !sort.isEmpty()) {
      final var allowedFields = java.util.Set.of("processDefinitionId", "tenantId");
      for (final var sortRequest : sort) {
        final var field = sortRequest.field();
        if (field != null && !allowedFields.contains(field.getValue())) {
          violations.add(
              io.camunda.gateway.mapping.http.validator.ErrorMessages
                  .ERROR_MESSAGE_UNSUPPORTED_SORT_FIELD_WITH_IS_LATEST_VERSION
                  .formatted(field.getValue()));
        }
      }
    }

    if (!violations.isEmpty()) {
      final var problem =
          io.camunda.gateway.mapping.http.validator.RequestValidator.createProblemDetail(
              violations);
      if (problem.isPresent()) {
        return Either.left(problem.get());
      }
    }

    return Either.right(null);
  }
}
