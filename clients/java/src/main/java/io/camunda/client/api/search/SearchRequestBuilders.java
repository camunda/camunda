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
package io.camunda.client.api.search;

import io.camunda.client.impl.search.ProcessInstanceFilterImpl;
import io.camunda.client.impl.search.ProcessInstanceSortImpl;
import io.camunda.client.impl.search.SearchRequestPageImpl;
import io.camunda.client.impl.search.UserTaskFilterImpl;
import io.camunda.client.impl.search.UserTaskSortImpl;
import io.camunda.client.impl.search.VariableValueFilterImpl;
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
}
