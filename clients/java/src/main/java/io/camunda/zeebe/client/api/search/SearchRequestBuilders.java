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
package io.camunda.zeebe.client.api.search;

import io.camunda.zeebe.client.api.search.filter.DecisionDefinitionFilter;
import io.camunda.zeebe.client.api.search.filter.DecisionRequirementsFilter;
import io.camunda.zeebe.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.zeebe.client.api.search.filter.UserTaskFilter;
import io.camunda.zeebe.client.api.search.filter.VariableValueFilter;
import io.camunda.zeebe.client.api.search.sort.DecisionDefinitionSort;
import io.camunda.zeebe.client.api.search.sort.DecisionRequirementsSort;
import io.camunda.zeebe.client.api.search.sort.ProcessInstanceSort;
import io.camunda.zeebe.client.api.search.sort.UserTaskSort;
import io.camunda.zeebe.client.impl.search.SearchRequestPageImpl;
import io.camunda.zeebe.client.impl.search.filter.DecisionDefinitionFilterImpl;
import io.camunda.zeebe.client.impl.search.filter.DecisionRequirementsFilterImpl;
import io.camunda.zeebe.client.impl.search.filter.ProcessInstanceFilterImpl;
import io.camunda.zeebe.client.impl.search.filter.UserTaskFilterImpl;
import io.camunda.zeebe.client.impl.search.filter.VariableValueFilterImpl;
import io.camunda.zeebe.client.impl.search.sort.DecisionDefinitionSortImpl;
import io.camunda.zeebe.client.impl.search.sort.DecisionRequirementsSortImpl;
import io.camunda.zeebe.client.impl.search.sort.ProcessInstanceSortImpl;
import io.camunda.zeebe.client.impl.search.sort.UserTaskSortImpl;
import java.util.function.Consumer;

public final class SearchRequestBuilders {

  private SearchRequestBuilders() {}

  /** Create a process instance filter. */
  public static ProcessInstanceFilter processInstanceFilter() {
    return new ProcessInstanceFilterImpl();
  }

  /** Create a process instance filter by using a fluent builder. */
  public static ProcessInstanceFilter processInstanceFilter(
      final Consumer<ProcessInstanceFilter> fn) {
    final ProcessInstanceFilter filter = processInstanceFilter();
    fn.accept(filter);
    return filter;
  }

  /** Create a variable value filter. */
  public static VariableValueFilter variableValueFilter() {
    return new VariableValueFilterImpl();
  }

  /** Create a variable value filter by using a fluent builder. */
  public static VariableValueFilter variableValueFilter(final Consumer<VariableValueFilter> fn) {
    final VariableValueFilter filter = variableValueFilter();
    fn.accept(filter);
    return filter;
  }

  /** Create a decision definition filter. */
  public static DecisionDefinitionFilter decisionDefinitionFilter() {
    return new DecisionDefinitionFilterImpl() {};
  }

  /** Create a decision definition filter by using a fluent builder. */
  public static DecisionDefinitionFilter decisionDefinitionFilter(
      final Consumer<DecisionDefinitionFilter> fn) {
    final DecisionDefinitionFilter filter = decisionDefinitionFilter();
    fn.accept(filter);
    return filter;
  }

  /** Create a process instance sort option. */
  public static ProcessInstanceSort processInstanceSort() {
    return new ProcessInstanceSortImpl();
  }

  /** Create a process instance sort option by using a fluent builder. */
  public static ProcessInstanceSort processInstanceSort(final Consumer<ProcessInstanceSort> fn) {
    final ProcessInstanceSort sort = processInstanceSort();
    fn.accept(sort);
    return sort;
  }

  /** Create a decision definition sort option. */
  public static DecisionDefinitionSort decisionDefinitionSort() {
    return new DecisionDefinitionSortImpl() {};
  }

  /** Create a decision definition sort option by using a fluent builder. */
  public static DecisionDefinitionSort decisionDefinitionSort(
      final Consumer<DecisionDefinitionSort> fn) {
    final DecisionDefinitionSort sort = decisionDefinitionSort();
    fn.accept(sort);
    return sort;
  }

  /** Create a search page. */
  public static SearchRequestPage searchRequestPage() {
    return new SearchRequestPageImpl();
  }

  /** Create a search page by using a fluent builder. */
  public static SearchRequestPage searchRequestPage(final Consumer<SearchRequestPage> fn) {
    final SearchRequestPage filter = searchRequestPage();
    fn.accept(filter);
    return filter;
  }

  public static UserTaskFilter userTaskFilter() {
    return new UserTaskFilterImpl();
  }

  public static UserTaskFilter userTaskFilter(final Consumer<UserTaskFilter> fn) {
    final UserTaskFilter filter = userTaskFilter();
    fn.accept(filter);
    return filter;
  }

  public static UserTaskSort userTaskSort() {
    return new UserTaskSortImpl();
  }

  public static UserTaskSort userTaskSort(final Consumer<UserTaskSort> fn) {
    final UserTaskSort sort = userTaskSort();
    fn.accept(sort);
    return sort;
  }

  public static DecisionRequirementsFilter decisionRequirementsFilter() {
    return new DecisionRequirementsFilterImpl();
  }

  public static DecisionRequirementsFilter decisionRequirementsFilter(
      final Consumer<DecisionRequirementsFilter> fn) {
    final DecisionRequirementsFilter filter = decisionRequirementsFilter();
    fn.accept(filter);
    return filter;
  }

  public static DecisionRequirementsSort decisionRequirementsSort() {
    return new DecisionRequirementsSortImpl();
  }

  public static DecisionRequirementsSort decisionRequirementsSort(
      final Consumer<DecisionRequirementsSort> fn) {
    final DecisionRequirementsSort sort = decisionRequirementsSort();
    fn.accept(sort);
    return sort;
  }
}
