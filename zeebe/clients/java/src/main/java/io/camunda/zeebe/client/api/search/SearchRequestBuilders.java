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

import io.camunda.zeebe.client.impl.search.ProcessInstanceFilterImpl;
import io.camunda.zeebe.client.impl.search.ProcessInstanceSortImpl;
import io.camunda.zeebe.client.impl.search.SearchRequestPageImpl;
import io.camunda.zeebe.client.impl.search.VariableValueFilterImpl;
import java.util.function.Consumer;

public final class SearchRequestBuilders {

  private SearchRequestBuilders() {}

  public static ProcessInstanceFilter processInstanceFilter() {
    return new ProcessInstanceFilterImpl();
  }

  public static ProcessInstanceFilter processInstanceFilter(
      final Consumer<ProcessInstanceFilter> fn) {
    final ProcessInstanceFilter filter = processInstanceFilter();
    fn.accept(filter);
    return filter;
  }

  public static VariableValueFilter variableValueFilter() {
    return new VariableValueFilterImpl();
  }

  public static VariableValueFilter variableValueFilter(final Consumer<VariableValueFilter> fn) {
    final VariableValueFilter filter = variableValueFilter();
    fn.accept(filter);
    return filter;
  }

  public static ProcessInstanceSort processInstanceSort() {
    return new ProcessInstanceSortImpl();
  }

  public static ProcessInstanceSort processInstanceSort(final Consumer<ProcessInstanceSort> fn) {
    final ProcessInstanceSort sort = processInstanceSort();
    fn.accept(sort);
    return sort;
  }

  public static SearchRequestPage searchRequestPage() {
    return new SearchRequestPageImpl();
  }

  public static SearchRequestPage searchRequestPage(final Consumer<SearchRequestPage> fn) {
    final SearchRequestPage filter = searchRequestPage();
    fn.accept(filter);
    return filter;
  }
}
