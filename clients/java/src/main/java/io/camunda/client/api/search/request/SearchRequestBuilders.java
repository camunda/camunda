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

import io.camunda.client.api.search.filter.AdHocSubprocessActivityFilter;
import io.camunda.client.api.search.filter.DecisionDefinitionFilter;
import io.camunda.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.client.api.search.filter.DecisionRequirementsFilter;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.filter.ProcessDefinitionFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.filter.UserTaskVariableFilter;
import io.camunda.client.api.search.filter.VariableFilter;
import io.camunda.client.api.search.filter.VariableValueFilter;
import io.camunda.client.api.search.sort.DecisionDefinitionSort;
import io.camunda.client.api.search.sort.DecisionInstanceSort;
import io.camunda.client.api.search.sort.DecisionRequirementsSort;
import io.camunda.client.api.search.sort.ElementInstanceSort;
import io.camunda.client.api.search.sort.IncidentSort;
import io.camunda.client.api.search.sort.ProcessDefinitionSort;
import io.camunda.client.api.search.sort.ProcessInstanceSort;
import io.camunda.client.api.search.sort.UserTaskSort;
import io.camunda.client.api.search.sort.VariableSort;
import io.camunda.client.api.statistics.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.client.impl.search.filter.AdHocSubprocessActivityFilterImpl;
import io.camunda.client.impl.search.filter.DecisionDefinitionFilterImpl;
import io.camunda.client.impl.search.filter.DecisionInstanceFilterImpl;
import io.camunda.client.impl.search.filter.DecisionRequirementsFilterImpl;
import io.camunda.client.impl.search.filter.ElementInstanceFilterImpl;
import io.camunda.client.impl.search.filter.IncidentFilterImpl;
import io.camunda.client.impl.search.filter.ProcessDefinitionFilterImpl;
import io.camunda.client.impl.search.filter.ProcessInstanceFilterImpl;
import io.camunda.client.impl.search.filter.UserTaskFilterImpl;
import io.camunda.client.impl.search.filter.UserTaskVariableFilterImpl;
import io.camunda.client.impl.search.filter.VariableFilterImpl;
import io.camunda.client.impl.search.filter.VariableValueFilterImpl;
import io.camunda.client.impl.search.request.SearchRequestPageImpl;
import io.camunda.client.impl.search.sort.DecisionDefinitionSortImpl;
import io.camunda.client.impl.search.sort.DecisionInstanceSortImpl;
import io.camunda.client.impl.search.sort.DecisionRequirementsSortImpl;
import io.camunda.client.impl.search.sort.ElementInstanceSortImpl;
import io.camunda.client.impl.search.sort.IncidentSortImpl;
import io.camunda.client.impl.search.sort.ProcessDefinitionSortImpl;
import io.camunda.client.impl.search.sort.ProcessInstanceSortImpl;
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

  public static AdHocSubprocessActivityFilter adHocSubprocessActivityFilter(
      final Consumer<AdHocSubprocessActivityFilter> fn) {
    final AdHocSubprocessActivityFilter filter = new AdHocSubprocessActivityFilterImpl();
    fn.accept(filter);
    return filter;
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
}
