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
package io.camunda.client.impl.search;

import io.camunda.client.protocol.rest.*;
import java.util.List;
import java.util.stream.Collectors;

public class SearchQuerySortRequestMapper {

  public static List<SearchQuerySortRequest> fromProcessDefinitionSearchQuerySortRequest(
      List<ProcessDefinitionSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchQuerySortRequest> fromProcessInstanceSearchQuerySortRequest(
      List<ProcessInstanceSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchQuerySortRequest> fromRoleSearchQuerySortRequest(
      List<RoleSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchQuerySortRequest> fromGroupSearchQuerySortRequest(
      List<GroupSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchQuerySortRequest> fromTenantSearchQuerySortRequest(
      List<TenantSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchQuerySortRequest> fromMappingSearchQuerySortRequest(
      List<MappingSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchQuerySortRequest> fromDecisionDefinitionSearchQuerySortRequest(
      List<DecisionDefinitionSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchQuerySortRequest> fromDecisionRequirementsSearchQuerySortRequest(
      List<DecisionRequirementsSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchQuerySortRequest> fromFlowNodeInstanceSearchQuerySortRequest(
      List<FlowNodeInstanceSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchQuerySortRequest> fromDecisionInstanceSearchQuerySortRequestt(
      List<DecisionInstanceSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchQuerySortRequest> fromUserTaskSearchQuerySortRequest(
      List<UserTaskSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchQuerySortRequest> fromUserTaskVariableSearchQuerySortRequest(
      List<UserTaskVariableSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchQuerySortRequest> fromVariableSearchQuerySortRequest(
      List<VariableSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchQuerySortRequest> fromUserSearchQuerySortRequest(
      List<UserSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchQuerySortRequest> fromIncidentSearchQuerySortRequest(
      List<IncidentSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchQuerySortRequest> fromAuthorizationSearchQuerySortRequest(
      List<AuthorizationSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<ProcessDefinitionSearchQuerySortRequest>
      toProcessDefinitionSearchQuerySortRequest(List<SearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r -> {
              final ProcessDefinitionSearchQuerySortRequest request =
                  new ProcessDefinitionSearchQuerySortRequest();
              request.setField(
                  ProcessDefinitionSearchQuerySortRequest.FieldEnum.fromValue(r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }

  public static List<ProcessInstanceSearchQuerySortRequest> toProcessInstanceSearchQuerySortRequest(
      List<SearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r -> {
              final ProcessInstanceSearchQuerySortRequest request =
                  new ProcessInstanceSearchQuerySortRequest();
              request.setField(
                  ProcessInstanceSearchQuerySortRequest.FieldEnum.fromValue(r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }

  public static List<RoleSearchQuerySortRequest> toRoleSearchQuerySortRequest(
      List<SearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r -> {
              final RoleSearchQuerySortRequest request = new RoleSearchQuerySortRequest();
              request.setField(RoleSearchQuerySortRequest.FieldEnum.fromValue(r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }

  public static List<GroupSearchQuerySortRequest> toGroupSearchQuerySortRequest(
      List<SearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r -> {
              final GroupSearchQuerySortRequest request = new GroupSearchQuerySortRequest();
              request.setField(GroupSearchQuerySortRequest.FieldEnum.fromValue(r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }

  public static List<TenantSearchQuerySortRequest> toTenantSearchQuerySortRequest(
      List<SearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r -> {
              final TenantSearchQuerySortRequest request = new TenantSearchQuerySortRequest();
              request.setField(TenantSearchQuerySortRequest.FieldEnum.fromValue(r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }

  public static List<MappingSearchQuerySortRequest> toMappingSearchQuerySortRequest(
      List<SearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r -> {
              final MappingSearchQuerySortRequest request = new MappingSearchQuerySortRequest();
              request.setField(MappingSearchQuerySortRequest.FieldEnum.fromValue(r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }

  public static List<DecisionDefinitionSearchQuerySortRequest>
      toDecisionDefinitionSearchQuerySortRequest(List<SearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r -> {
              final DecisionDefinitionSearchQuerySortRequest request =
                  new DecisionDefinitionSearchQuerySortRequest();
              request.setField(
                  DecisionDefinitionSearchQuerySortRequest.FieldEnum.fromValue(r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }

  public static List<DecisionRequirementsSearchQuerySortRequest>
      toDecisionRequirementsSearchQuerySortRequest(List<SearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r -> {
              final DecisionRequirementsSearchQuerySortRequest request =
                  new DecisionRequirementsSearchQuerySortRequest();
              request.setField(
                  DecisionRequirementsSearchQuerySortRequest.FieldEnum.fromValue(r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }

  public static List<FlowNodeInstanceSearchQuerySortRequest>
      toFlowNodeInstanceSearchQuerySortRequest(List<SearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r -> {
              final FlowNodeInstanceSearchQuerySortRequest request =
                  new FlowNodeInstanceSearchQuerySortRequest();
              request.setField(
                  FlowNodeInstanceSearchQuerySortRequest.FieldEnum.fromValue(r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }

  public static List<DecisionInstanceSearchQuerySortRequest>
      toDecisionInstanceSearchQuerySortRequest(List<SearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r -> {
              final DecisionInstanceSearchQuerySortRequest request =
                  new DecisionInstanceSearchQuerySortRequest();
              request.setField(
                  DecisionInstanceSearchQuerySortRequest.FieldEnum.fromValue(r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }

  public static List<UserTaskSearchQuerySortRequest> toUserTaskSearchQuerySortRequest(
      List<SearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r -> {
              final UserTaskSearchQuerySortRequest request = new UserTaskSearchQuerySortRequest();
              request.setField(UserTaskSearchQuerySortRequest.FieldEnum.fromValue(r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }

  public static List<UserTaskVariableSearchQuerySortRequest>
      toUserTaskVariableSearchQuerySortRequest(List<SearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r -> {
              final UserTaskVariableSearchQuerySortRequest request =
                  new UserTaskVariableSearchQuerySortRequest();
              request.setField(
                  UserTaskVariableSearchQuerySortRequest.FieldEnum.fromValue(r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }

  public static List<VariableSearchQuerySortRequest> toVariableSearchQuerySortRequest(
      List<SearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r -> {
              final VariableSearchQuerySortRequest request = new VariableSearchQuerySortRequest();
              request.setField(VariableSearchQuerySortRequest.FieldEnum.fromValue(r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }

  public static List<UserSearchQuerySortRequest> toUserSearchQuerySortRequest(
      List<SearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r -> {
              final UserSearchQuerySortRequest request = new UserSearchQuerySortRequest();
              request.setField(UserSearchQuerySortRequest.FieldEnum.fromValue(r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }

  public static List<IncidentSearchQuerySortRequest> toIncidentSearchQuerySortRequest(
      List<SearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r -> {
              final IncidentSearchQuerySortRequest request = new IncidentSearchQuerySortRequest();
              request.setField(IncidentSearchQuerySortRequest.FieldEnum.fromValue(r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }

  public static List<AuthorizationSearchQuerySortRequest> toAuthorizationSearchQuerySortRequest(
      List<SearchQuerySortRequest> requests) {
    return requests.stream()
        .map(
            r -> {
              final AuthorizationSearchQuerySortRequest request =
                  new AuthorizationSearchQuerySortRequest();
              request.setField(
                  AuthorizationSearchQuerySortRequest.FieldEnum.fromValue(r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }

  private static SearchQuerySortRequest createFrom(Object field, SortOrderEnum order) {
    final SearchQuerySortRequest request = new SearchQuerySortRequest();
    request.setField(field.toString());
    request.setOrder(order);
    return request;
  }
}
