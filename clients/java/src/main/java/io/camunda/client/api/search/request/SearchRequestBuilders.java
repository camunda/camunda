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
package io.camunda.client.api.search.request;

import io.camunda.client.api.search.filter.AuthorizationFilter;
import io.camunda.client.api.search.filter.BatchOperationFilter;
import io.camunda.client.api.search.filter.BatchOperationItemFilter;
import io.camunda.client.api.search.filter.DecisionDefinitionFilter;
import io.camunda.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.client.api.search.filter.DecisionRequirementsFilter;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.filter.GroupFilter;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.client.api.search.filter.MappingRuleFilter;
import io.camunda.client.api.search.filter.MessageSubscriptionFilter;
import io.camunda.client.api.search.filter.ProcessDefinitionFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.filter.RoleFilter;
import io.camunda.client.api.search.filter.TenantFilter;
import io.camunda.client.api.search.filter.UserFilter;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.filter.UserTaskVariableFilter;
import io.camunda.client.api.search.filter.VariableFilter;
import io.camunda.client.api.search.filter.VariableValueFilter;
import io.camunda.client.api.search.sort.AuthorizationSort;
import io.camunda.client.api.search.sort.BatchOperationItemSort;
import io.camunda.client.api.search.sort.BatchOperationSort;
import io.camunda.client.api.search.sort.ClientSort;
import io.camunda.client.api.search.sort.DecisionDefinitionSort;
import io.camunda.client.api.search.sort.DecisionInstanceSort;
import io.camunda.client.api.search.sort.DecisionRequirementsSort;
import io.camunda.client.api.search.sort.ElementInstanceSort;
import io.camunda.client.api.search.sort.GroupSort;
import io.camunda.client.api.search.sort.GroupUserSort;
import io.camunda.client.api.search.sort.IncidentSort;
import io.camunda.client.api.search.sort.JobSort;
import io.camunda.client.api.search.sort.MappingRuleSort;
import io.camunda.client.api.search.sort.MessageSubscriptionSort;
import io.camunda.client.api.search.sort.ProcessDefinitionSort;
import io.camunda.client.api.search.sort.ProcessInstanceSort;
import io.camunda.client.api.search.sort.RoleGroupSort;
import io.camunda.client.api.search.sort.RoleSort;
import io.camunda.client.api.search.sort.RoleUserSort;
import io.camunda.client.api.search.sort.TenantGroupSort;
import io.camunda.client.api.search.sort.TenantSort;
import io.camunda.client.api.search.sort.TenantUserSort;
import io.camunda.client.api.search.sort.UserSort;
import io.camunda.client.api.search.sort.UserTaskSort;
import io.camunda.client.api.search.sort.VariableSort;
import io.camunda.client.api.statistics.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.client.impl.search.filter.AuthorizationFilterImpl;
import io.camunda.client.impl.search.filter.BatchOperationFilterImpl;
import io.camunda.client.impl.search.filter.BatchOperationItemFilterImpl;
import io.camunda.client.impl.search.filter.DecisionDefinitionFilterImpl;
import io.camunda.client.impl.search.filter.DecisionInstanceFilterImpl;
import io.camunda.client.impl.search.filter.DecisionRequirementsFilterImpl;
import io.camunda.client.impl.search.filter.ElementInstanceFilterImpl;
import io.camunda.client.impl.search.filter.GroupFilterImpl;
import io.camunda.client.impl.search.filter.IncidentFilterImpl;
import io.camunda.client.impl.search.filter.JobFilterImpl;
import io.camunda.client.impl.search.filter.MappingRuleFilterImpl;
import io.camunda.client.impl.search.filter.MessageSubscriptionFilterImpl;
import io.camunda.client.impl.search.filter.ProcessDefinitionFilterImpl;
import io.camunda.client.impl.search.filter.ProcessInstanceFilterImpl;
import io.camunda.client.impl.search.filter.RoleFilterImpl;
import io.camunda.client.impl.search.filter.TenantFilterImpl;
import io.camunda.client.impl.search.filter.UserFilterImpl;
import io.camunda.client.impl.search.filter.UserTaskFilterImpl;
import io.camunda.client.impl.search.filter.UserTaskVariableFilterImpl;
import io.camunda.client.impl.search.filter.VariableFilterImpl;
import io.camunda.client.impl.search.filter.VariableValueFilterImpl;
import io.camunda.client.impl.search.request.SearchRequestPageImpl;
import io.camunda.client.impl.search.sort.AuthorizationSortImpl;
import io.camunda.client.impl.search.sort.BatchOperationItemSortImpl;
import io.camunda.client.impl.search.sort.BatchOperationSortImpl;
import io.camunda.client.impl.search.sort.ClientSortImpl;
import io.camunda.client.impl.search.sort.DecisionDefinitionSortImpl;
import io.camunda.client.impl.search.sort.DecisionInstanceSortImpl;
import io.camunda.client.impl.search.sort.DecisionRequirementsSortImpl;
import io.camunda.client.impl.search.sort.ElementInstanceSortImpl;
import io.camunda.client.impl.search.sort.GroupSortImpl;
import io.camunda.client.impl.search.sort.GroupUserSortImpl;
import io.camunda.client.impl.search.sort.IncidentSortImpl;
import io.camunda.client.impl.search.sort.JobSortImpl;
import io.camunda.client.impl.search.sort.MappingRuleSortImpl;
import io.camunda.client.impl.search.sort.MessageSubscriptionSortImpl;
import io.camunda.client.impl.search.sort.ProcessDefinitionSortImpl;
import io.camunda.client.impl.search.sort.ProcessInstanceSortImpl;
import io.camunda.client.impl.search.sort.RoleGroupSortImpl;
import io.camunda.client.impl.search.sort.RoleSortImpl;
import io.camunda.client.impl.search.sort.RoleUserSortImpl;
import io.camunda.client.impl.search.sort.TenantGroupSortImpl;
import io.camunda.client.impl.search.sort.TenantSortImpl;
import io.camunda.client.impl.search.sort.TenantUserSortImpl;
import io.camunda.client.impl.search.sort.UserSortImpl;
import io.camunda.client.impl.search.sort.UserTaskSortImpl;
import io.camunda.client.impl.search.sort.VariableSortImpl;
import io.camunda.client.impl.statistics.filter.ProcessDefinitionStatisticsFilterImpl;
import java.util.function.Consumer;

public final class SearchRequestBuilders {

  private SearchRequestBuilders() {}

  public static ProcessDefinitionFilter processDefinitionFilter(
      final Consumer<ProcessDefinitionFilter> fn) {
    final ProcessDefinitionFilter filter = new ProcessDefinitionFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static ProcessInstanceFilter processInstanceFilter(
      final Consumer<ProcessInstanceFilter> fn) {
    final ProcessInstanceFilter filter = new ProcessInstanceFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static DecisionDefinitionFilter decisionDefinitionFilter(
      final Consumer<DecisionDefinitionFilter> fn) {
    final DecisionDefinitionFilter filter = new DecisionDefinitionFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static IncidentFilter incidentFilter(final Consumer<IncidentFilter> fn) {
    final IncidentFilter filter = new IncidentFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static ProcessDefinitionSort processDefinitionSort(
      final Consumer<ProcessDefinitionSort> fn) {
    final ProcessDefinitionSort sort = new ProcessDefinitionSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static ProcessInstanceSort processInstanceSort(final Consumer<ProcessInstanceSort> fn) {
    final ProcessInstanceSort sort = new ProcessInstanceSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static DecisionDefinitionSort decisionDefinitionSort(
      final Consumer<DecisionDefinitionSort> fn) {
    final DecisionDefinitionSort sort = new DecisionDefinitionSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static IncidentSort incidentSort(final Consumer<IncidentSort> fn) {
    final IncidentSort sort = new IncidentSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static SearchRequestPage searchRequestPage(final Consumer<SearchRequestPage> fn) {
    final SearchRequestPage filter = new SearchRequestPageImpl();
    fn.accept(filter);
    return filter;
  }

  public static UserTaskFilter userTaskFilter(final Consumer<UserTaskFilter> fn) {
    final UserTaskFilter filter = new UserTaskFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static UserTaskSort userTaskSort(final Consumer<UserTaskSort> fn) {
    final UserTaskSort sort = new UserTaskSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static DecisionRequirementsFilter decisionRequirementsFilter(
      final Consumer<DecisionRequirementsFilter> fn) {
    final DecisionRequirementsFilter filter = new DecisionRequirementsFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static DecisionRequirementsSort decisionRequirementsSort(
      final Consumer<DecisionRequirementsSort> fn) {
    final DecisionRequirementsSort sort = new DecisionRequirementsSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static DecisionInstanceFilter decisionInstanceFilter(
      final Consumer<DecisionInstanceFilter> fn) {
    final DecisionInstanceFilter filter = new DecisionInstanceFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static DecisionInstanceSort decisionInstanceSort(final Consumer<DecisionInstanceSort> fn) {
    final DecisionInstanceSort sort = new DecisionInstanceSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static ElementInstanceFilter elementInstanceFilter(
      final Consumer<ElementInstanceFilter> fn) {
    final ElementInstanceFilter filter = new ElementInstanceFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static ElementInstanceSort elementInstanceSort(final Consumer<ElementInstanceSort> fn) {
    final ElementInstanceSort sort = new ElementInstanceSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static VariableFilter variableFilter(final Consumer<VariableFilter> fn) {
    final VariableFilter filter = new VariableFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static VariableSort variableSort(final Consumer<VariableSort> fn) {
    final VariableSort sort = new VariableSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static UserTaskVariableFilter userTaskVariableFilter(
      final Consumer<UserTaskVariableFilter> fn) {
    final UserTaskVariableFilter filter = new UserTaskVariableFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static VariableValueFilter variableValueFilter(final Consumer<VariableValueFilter> fn) {
    final VariableValueFilterImpl filter = new VariableValueFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static ProcessDefinitionStatisticsFilter processDefinitionStatisticsFilter(
      final Consumer<ProcessDefinitionStatisticsFilter> fn) {
    final ProcessDefinitionStatisticsFilter filter = new ProcessDefinitionStatisticsFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static GroupFilter groupFilter(final Consumer<GroupFilter> fn) {
    final GroupFilter filter = new GroupFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static GroupSort groupSort(final Consumer<GroupSort> fn) {
    final GroupSort sort = new GroupSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static UserFilter userFilter(final Consumer<UserFilter> fn) {
    final UserFilter filter = new UserFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static UserSort userSort(final Consumer<UserSort> fn) {
    final UserSort sort = new UserSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static GroupUserSort groupUserSort(final Consumer<GroupUserSort> fn) {
    final GroupUserSort sort = new GroupUserSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static MappingRuleFilter mappingRuleFilter(final Consumer<MappingRuleFilter> fn) {
    final MappingRuleFilter filter = new MappingRuleFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static MappingRuleSort mappingRuleSort(final Consumer<MappingRuleSort> fn) {
    final MappingRuleSort sort = new MappingRuleSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static BatchOperationFilter batchOperationFilter(final Consumer<BatchOperationFilter> fn) {
    final BatchOperationFilter filter = new BatchOperationFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static BatchOperationSort batchOperationSort(final Consumer<BatchOperationSort> fn) {
    final BatchOperationSort sort = new BatchOperationSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static BatchOperationItemFilter batchOperationItemFilter(
      final Consumer<BatchOperationItemFilter> fn) {
    final BatchOperationItemFilter filter = new BatchOperationItemFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static BatchOperationItemSort batchOperationItemSort(
      final Consumer<BatchOperationItemSort> fn) {
    final BatchOperationItemSort sort = new BatchOperationItemSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static AuthorizationFilter authorizationFilter(final Consumer<AuthorizationFilter> fn) {
    final AuthorizationFilter filter = new AuthorizationFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static AuthorizationSort authorizationSort(final Consumer<AuthorizationSort> fn) {
    final AuthorizationSort sort = new AuthorizationSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static RoleFilter roleFilter(final Consumer<RoleFilter> fn) {
    final RoleFilter filter = new RoleFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static RoleSort roleSort(final Consumer<RoleSort> fn) {
    final RoleSort sort = new RoleSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static TenantFilter tenantFilter(final Consumer<TenantFilter> fn) {
    final TenantFilter filter = new TenantFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static TenantSort tenantSort(final Consumer<TenantSort> fn) {
    final TenantSort sort = new TenantSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static RoleUserSort roleUserSort(final Consumer<RoleUserSort> fn) {
    final RoleUserSort sort = new RoleUserSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static RoleGroupSort roleGroupSort(final Consumer<RoleGroupSort> fn) {
    final RoleGroupSort sort = new RoleGroupSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static TenantUserSort tenantUserSort(final Consumer<TenantUserSort> fn) {
    final TenantUserSort sort = new TenantUserSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static TenantGroupSort tenantGroupSort(final Consumer<TenantGroupSort> fn) {
    final TenantGroupSort sort = new TenantGroupSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static ClientSort clientSort(final Consumer<ClientSort> fn) {
    final ClientSort sort = new ClientSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static JobFilter jobFilter(final Consumer<JobFilter> fn) {
    final JobFilter filter = new JobFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static JobSort jobSort(final Consumer<JobSort> fn) {
    final JobSort sort = new JobSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static MessageSubscriptionFilter messageSubscriptionFilter(
      final Consumer<MessageSubscriptionFilter> fn) {
    final MessageSubscriptionFilter filter = new MessageSubscriptionFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static MessageSubscriptionSort messageSubscriptionSort(
      final Consumer<MessageSubscriptionSort> fn) {
    final MessageSubscriptionSort sort = new MessageSubscriptionSortImpl();
    fn.accept(sort);
    return sort;
  }
}
