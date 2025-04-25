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
package io.camunda.client.impl.search.request;

import io.camunda.client.protocol.rest.*;
import java.util.List;
import java.util.stream.Collectors;

public class SearchRequestSortMapper {

  public static List<SearchRequestSort> fromProcessDefinitionSearchQuerySortRequest(
      final List<ProcessDefinitionSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchRequestSort> fromProcessInstanceSearchQuerySortRequest(
      final List<ProcessInstanceSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchRequestSort> fromRoleSearchQuerySortRequest(
      final List<RoleSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchRequestSort> fromGroupSearchQuerySortRequest(
      final List<GroupSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchRequestSort> fromTenantSearchQuerySortRequest(
      final List<TenantSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchRequestSort> fromMappingSearchQuerySortRequest(
      final List<MappingSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchRequestSort> fromDecisionDefinitionSearchQuerySortRequest(
      final List<DecisionDefinitionSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchRequestSort> fromDecisionRequirementsSearchQuerySortRequest(
      final List<DecisionRequirementsSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchRequestSort> fromElementInstanceSearchQuerySortRequest(
      final List<ElementInstanceSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchRequestSort> fromDecisionInstanceSearchQuerySortRequestt(
      final List<DecisionInstanceSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchRequestSort> fromUserTaskSearchQuerySortRequest(
      final List<UserTaskSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchRequestSort> fromUserTaskVariableSearchQuerySortRequest(
      final List<UserTaskVariableSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchRequestSort> fromVariableSearchQuerySortRequest(
      final List<VariableSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchRequestSort> fromUserSearchQuerySortRequest(
      final List<UserSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchRequestSort> fromIncidentSearchQuerySortRequest(
      final List<IncidentSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<SearchRequestSort> fromAuthorizationSearchQuerySortRequest(
      final List<AuthorizationSearchQuerySortRequest> requests) {
    return requests.stream()
        .map(r -> createFrom(r.getField(), r.getOrder()))
        .collect(Collectors.toList());
  }

  public static List<ProcessDefinitionSearchQuerySortRequest>
      toProcessDefinitionSearchQuerySortRequest(final List<SearchRequestSort> requests) {
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
      final List<SearchRequestSort> requests) {
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
      final List<SearchRequestSort> requests) {
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
      final List<SearchRequestSort> requests) {
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
      final List<SearchRequestSort> requests) {
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
      final List<SearchRequestSort> requests) {
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
      toDecisionDefinitionSearchQuerySortRequest(final List<SearchRequestSort> requests) {
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
      toDecisionRequirementsSearchQuerySortRequest(final List<SearchRequestSort> requests) {
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

  public static List<ElementInstanceSearchQuerySortRequest> toElementInstanceSearchQuerySortRequest(
      final List<SearchRequestSort> requests) {
    return requests.stream()
        .map(
            r -> {
              final ElementInstanceSearchQuerySortRequest request =
                  new ElementInstanceSearchQuerySortRequest();
              request.setField(
                  ElementInstanceSearchQuerySortRequest.FieldEnum.fromValue(r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }

  public static List<DecisionInstanceSearchQuerySortRequest>
      toDecisionInstanceSearchQuerySortRequest(final List<SearchRequestSort> requests) {
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
      final List<SearchRequestSort> requests) {
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
      toUserTaskVariableSearchQuerySortRequest(final List<SearchRequestSort> requests) {
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
      final List<SearchRequestSort> requests) {
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
      final List<SearchRequestSort> requests) {
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
      final List<SearchRequestSort> requests) {
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
      final List<SearchRequestSort> requests) {
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

  private static SearchRequestSort createFrom(final Object field, final SortOrderEnum order) {
    final SearchRequestSort request = new SearchRequestSort();
    request.setField(field.toString());
    request.setOrder(order);
    return request;
  }
}
