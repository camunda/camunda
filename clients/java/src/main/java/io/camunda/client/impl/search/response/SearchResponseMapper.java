/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.impl.search.response;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.Authorization;
import io.camunda.client.api.search.response.BatchOperation;
import io.camunda.client.api.search.response.BatchOperationItems;
import io.camunda.client.api.search.response.BatchOperationItems.BatchOperationItem;
import io.camunda.client.api.search.response.Client;
import io.camunda.client.api.search.response.DecisionDefinition;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.DecisionRequirements;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.Group;
import io.camunda.client.api.search.response.GroupUser;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.Job;
import io.camunda.client.api.search.response.MappingRule;
import io.camunda.client.api.search.response.MessageSubscription;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.ProcessInstanceCallHierarchyEntryResponse;
import io.camunda.client.api.search.response.ProcessInstanceSequenceFlow;
import io.camunda.client.api.search.response.Role;
import io.camunda.client.api.search.response.RoleGroup;
import io.camunda.client.api.search.response.RoleUser;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.SearchResponsePage;
import io.camunda.client.api.search.response.Tenant;
import io.camunda.client.api.search.response.TenantGroup;
import io.camunda.client.api.search.response.TenantUser;
import io.camunda.client.api.search.response.User;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import io.camunda.client.impl.search.response.BatchOperationItemsImpl.BatchOperationItemImpl;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SearchResponseMapper {

  private SearchResponseMapper() {}

  public static SearchResponse<ProcessDefinition> toProcessDefinitionSearchResponse(
      final ProcessDefinitionSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<ProcessDefinition> instances =
        toSearchResponseInstances(response.getItems(), ProcessDefinitionImpl::new);

    return new SearchResponseImpl<>(instances, page);
  }

  public static ProcessDefinition toProcessDefinitionGetResponse(
      final ProcessDefinitionResult response) {
    return new ProcessDefinitionImpl(response);
  }

  public static ProcessInstance toProcessInstanceGetResponse(final ProcessInstanceResult response) {
    return new ProcessInstanceImpl(response);
  }

  public static SearchResponse<ProcessInstance> toProcessInstanceSearchResponse(
      final ProcessInstanceSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<ProcessInstance> instances =
        toSearchResponseInstances(response.getItems(), ProcessInstanceImpl::new);

    return new SearchResponseImpl<>(instances, page);
  }

  public static List<ProcessInstanceSequenceFlow> toProcessInstanceSequenceFlowSearchResponse(
      final ProcessInstanceSequenceFlowsQueryResult response) {
    if (response.getItems() != null) {
      return response.getItems().stream()
          .map(ProcessInstanceSequenceFlowImpl::new)
          .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  public static SearchResponse<UserTask> toUserTaskSearchResponse(
      final UserTaskSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<UserTask> instances =
        toSearchResponseInstances(response.getItems(), UserTaskImpl::new);
    return new SearchResponseImpl<>(instances, page);
  }

  public static SearchResponse<Variable> toVariableSearchResponse(
      final VariableSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<Variable> instances =
        toSearchResponseInstances(response.getItems(), VariableImpl::new);
    return new SearchResponseImpl<>(instances, page);
  }

  public static SearchResponse<DecisionDefinition> toDecisionDefinitionSearchResponse(
      final DecisionDefinitionSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<DecisionDefinition> instances =
        toSearchResponseInstances(response.getItems(), DecisionDefinitionImpl::new);
    return new SearchResponseImpl<>(instances, page);
  }

  public static SearchResponse<ElementInstance> toElementInstanceSearchResponse(
      final ElementInstanceSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<ElementInstance> instances =
        toSearchResponseInstances(response.getItems(), ElementInstanceImpl::new);
    return new SearchResponseImpl<>(instances, page);
  }

  public static ElementInstance toElementInstanceGetResponse(final ElementInstanceResult response) {
    return new ElementInstanceImpl(response);
  }

  public static SearchResponse<Incident> toIncidentSearchResponse(
      final IncidentSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<Incident> incidents =
        toSearchResponseInstances(response.getItems(), IncidentImpl::new);
    return new SearchResponseImpl<>(incidents, page);
  }

  public static Incident toIncidentGetResponse(final IncidentResult response) {
    return new IncidentImpl(response);
  }

  private static SearchResponsePage toSearchResponsePage(
      final SearchQueryPageResponse pageResponse) {
    return new SearchResponsePageImpl(
        pageResponse.getTotalItems(),
        pageResponse.getHasMoreTotalItems(),
        pageResponse.getStartCursor(),
        pageResponse.getEndCursor());
  }

  public static SearchResponse<DecisionRequirements> toDecisionRequirementsSearchResponse(
      final DecisionRequirementsSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<DecisionRequirements> instances =
        toSearchResponseInstances(response.getItems(), DecisionRequirementsImpl::new);
    return new SearchResponseImpl<>(instances, page);
  }

  public static SearchResponse<DecisionInstance> toDecisionInstanceSearchResponse(
      final DecisionInstanceSearchQueryResult response, final JsonMapper jsonMapper) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<DecisionInstance> instances =
        toSearchResponseInstances(
            response.getItems(), item -> new DecisionInstanceImpl(item, jsonMapper));
    return new SearchResponseImpl<>(instances, page);
  }

  public static BatchOperation toBatchOperationGetResponse(final BatchOperationResponse response) {
    return new BatchOperationImpl(response);
  }

  public static BatchOperationItems toBatchOperationItemsGetResponse(
      final BatchOperationItemSearchQueryResult response) {
    return new BatchOperationItemsImpl(response);
  }

  public static Client toClientResponse(final RoleClientResult response) {
    return new ClientImpl(response.getClientId());
  }

  public static SearchResponse<Client> toClientsResponse(final RoleClientSearchResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<Client> instances =
        toSearchResponseInstances(response.getItems(), SearchResponseMapper::toClientResponse);
    return new SearchResponseImpl<>(instances, page);
  }

  public static SearchResponse<Client> toGroupClientsResponse(
      final GroupClientSearchResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<Client> instances =
        toSearchResponseInstances(response.getItems(), SearchResponseMapper::toGroupClientResponse);
    return new SearchResponseImpl<>(instances, page);
  }

  public static Client toGroupClientResponse(final GroupClientResult response) {
    return new ClientImpl(response.getClientId());
  }

  public static SearchResponse<Client> toTenantClientsResponse(
      final TenantClientSearchResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<Client> instances =
        toSearchResponseInstances(
            response.getItems(), SearchResponseMapper::toTenantClientResponse);
    return new SearchResponseImpl<>(instances, page);
  }

  public static Client toTenantClientResponse(final TenantClientResult response) {
    return new ClientImpl(response.getClientId());
  }

  public static Role toRoleResponse(final RoleResult response) {
    return new RoleImpl(response.getRoleId(), response.getName(), response.getDescription());
  }

  public static SearchResponse<Role> toRolesResponse(final RoleSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<Role> instances =
        toSearchResponseInstances(response.getItems(), SearchResponseMapper::toRoleResponse);
    return new SearchResponseImpl<>(instances, page);
  }

  public static SearchResponse<Tenant> toTenantsResponse(final TenantSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<Tenant> instances =
        toSearchResponseInstances(response.getItems(), SearchResponseMapper::toTenantResponse);
    return new SearchResponseImpl<>(instances, page);
  }

  public static Tenant toTenantResponse(final TenantResult response) {
    return new TenantImpl(response.getTenantId(), response.getName(), response.getDescription());
  }

  public static SearchResponse<RoleUser> toRoleUsersResponse(final RoleUserSearchResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<RoleUser> instances =
        toSearchResponseInstances(response.getItems(), SearchResponseMapper::toRoleUser);
    return new SearchResponseImpl<>(instances, page);
  }

  private static RoleUser toRoleUser(final RoleUserResult response) {
    return new RoleUserImpl(response.getUsername());
  }

  public static SearchResponse<Authorization> toAuthorizationsResponse(
      final AuthorizationSearchResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<Authorization> instances =
        toSearchResponseInstances(
            response.getItems(), SearchResponseMapper::toAuthorizationResponse);
    return new SearchResponseImpl<>(instances, page);
  }

  public static Authorization toAuthorizationResponse(final AuthorizationResult response) {
    final List<PermissionTypeEnum> permissionTypes = response.getPermissionTypes();
    final List<PermissionType> convertedPermissionTypes = new ArrayList<>();
    for (final PermissionTypeEnum permissionType : permissionTypes) {
      final PermissionType converted = EnumUtil.convert(permissionType, PermissionType.class);
      convertedPermissionTypes.add(converted);
    }

    return new AuthorizationImpl(
        response.getAuthorizationKey(),
        response.getOwnerId(),
        response.getResourceId(),
        EnumUtil.convert(response.getOwnerType(), OwnerType.class),
        EnumUtil.convert(response.getResourceType(), ResourceType.class),
        convertedPermissionTypes);
  }

  public static Group toGroupResponse(final GroupResult response) {
    return new GroupImpl(response.getGroupId(), response.getName(), response.getDescription());
  }

  public static User toUserResponse(final UserResult response) {
    return new UserImpl(response.getUsername(), response.getName(), response.getEmail());
  }

  public static SearchResponse<GroupUser> toGroupUsersResponse(
      final GroupUserSearchResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<GroupUser> instances =
        toSearchResponseInstances(response.getItems(), SearchResponseMapper::toGroupUser);
    return new SearchResponseImpl<>(instances, page);
  }

  public static GroupUser toGroupUser(final GroupUserResult response) {
    return new GroupUserImpl(response.getUsername());
  }

  public static SearchResponse<TenantUser> toTenantUsersResponse(
      final TenantUserSearchResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<TenantUser> instances =
        toSearchResponseInstances(response.getItems(), SearchResponseMapper::toTenantUser);
    return new SearchResponseImpl<>(instances, page);
  }

  private static TenantUser toTenantUser(final TenantUserResult response) {
    return new TenantUserImpl(response.getUsername());
  }

  public static SearchResponse<TenantGroup> toTenantGroupsResponse(
      final TenantGroupSearchResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<TenantGroup> instances =
        toSearchResponseInstances(response.getItems(), SearchResponseMapper::toTenantGroup);
    return new SearchResponseImpl<>(instances, page);
  }

  private static TenantGroup toTenantGroup(final TenantGroupResult response) {
    return new TenantGroupImpl(response.getGroupId());
  }

  public static MappingRule toMappingResponse(final MappingRuleResult response) {
    return new MappingRuleImpl(
        response.getMappingRuleId(),
        response.getClaimName(),
        response.getClaimValue(),
        response.getName());
  }

  public static SearchResponse<Group> toGroupsResponse(final GroupSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<Group> instances =
        toSearchResponseInstances(response.getItems(), SearchResponseMapper::toGroupResponse);
    return new SearchResponseImpl<>(instances, page);
  }

  public static SearchResponse<RoleGroup> toRoleGroupsResponse(
      final RoleGroupSearchResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<RoleGroup> instances =
        toSearchResponseInstances(response.getItems(), SearchResponseMapper::toRoleGroupResponse);
    return new SearchResponseImpl<>(instances, page);
  }

  public static RoleGroup toRoleGroupResponse(final RoleGroupResult response) {
    return new RoleGroupImpl(response.getGroupId());
  }

  public static SearchResponse<User> toUsersResponse(final UserSearchResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<User> instances =
        toSearchResponseInstances(response.getItems(), SearchResponseMapper::toUserResponse);
    return new SearchResponseImpl<>(instances, page);
  }

  public static SearchResponse<MappingRule> toMappingRulesResponse(
      final MappingRuleSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<MappingRule> instances =
        toSearchResponseInstances(response.getItems(), SearchResponseMapper::toMappingResponse);
    return new SearchResponseImpl<>(instances, page);
  }

  public static SearchResponse<BatchOperation> toBatchOperationsResponse(
      final BatchOperationSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<BatchOperation> instances =
        toSearchResponseInstances(response.getItems(), BatchOperationImpl::new);
    return new SearchResponseImpl<>(instances, page);
  }

  public static SearchResponse<BatchOperationItem> toBatchOperationItemsResponse(
      final BatchOperationItemSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<BatchOperationItem> instances =
        toSearchResponseInstances(response.getItems(), BatchOperationItemImpl::new);
    return new SearchResponseImpl<>(instances, page);
  }

  public static List<ProcessInstanceCallHierarchyEntryResponse>
      toProcessInstanceCallHierarchyEntryResponse(
          final ProcessInstanceCallHierarchyEntry[] entries) {
    return toSearchResponseInstances(
        Arrays.asList(entries), ProcessInstanceCallHierarchyEntryResponseImpl::new);
  }

  private static <T, R> List<R> toSearchResponseInstances(
      final List<T> items, final Function<T, R> mapper) {
    return Optional.ofNullable(items)
        .map(i -> i.stream().map(mapper).collect(Collectors.toList()))
        .orElse(Collections.emptyList());
  }

  public static SearchResponse<Job> toJobSearchResponse(final JobSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<Job> instances = toSearchResponseInstances(response.getItems(), JobImpl::new);
    return new SearchResponseImpl<>(instances, page);
  }

  public static SearchResponse<MessageSubscription> toMessageSubscriptionSearchResponse(
      final MessageSubscriptionSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<MessageSubscription> instances =
        toSearchResponseInstances(response.getItems(), MessageSubscriptionImpl::new);
    return new SearchResponseImpl<>(instances, page);
  }
}
