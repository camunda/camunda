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
import io.camunda.client.api.search.response.Mapping;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.ProcessInstanceCallHierarchyEntryResponse;
import io.camunda.client.api.search.response.ProcessInstanceSequenceFlow;
import io.camunda.client.api.search.response.Role;
import io.camunda.client.api.search.response.RoleUser;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.SearchResponsePage;
import io.camunda.client.api.search.response.User;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import io.camunda.client.impl.search.response.BatchOperationItemsImpl.BatchOperationItemImpl;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.*;
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
        pageResponse.getFirstSortValues(),
        pageResponse.getLastSortValues());
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

  public static Role toRoleResponse(final RoleResult response) {
    return new RoleImpl(response.getRoleId(), response.getName(), response.getDescription());
  }

  public static SearchResponse<Role> toRolesResponse(final RoleSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<Role> instances =
        toSearchResponseInstances(response.getItems(), SearchResponseMapper::toRoleResponse);
    return new SearchResponseImpl<>(instances, page);
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

  public static Group toGroupResponse(final GroupResult response) {
    return new GroupImpl(response.getGroupId(), response.getName(), response.getDescription());
  }

  public static User toUserResponse(final UserResult response) {
    return new UserImpl(
        ParseUtil.parseLongOrNull(response.getUserKey()),
        response.getUsername(),
        response.getName(),
        response.getEmail());
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

  public static Mapping toMappingResponse(final MappingResult response) {
    return new MappingImpl(
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

  public static SearchResponse<User> toUsersResponse(final UserSearchResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<User> instances =
        toSearchResponseInstances(response.getItems(), SearchResponseMapper::toUserResponse);
    return new SearchResponseImpl<>(instances, page);
  }

  public static SearchResponse<Mapping> toMappingsResponse(
      final MappingSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<Mapping> instances =
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
}
