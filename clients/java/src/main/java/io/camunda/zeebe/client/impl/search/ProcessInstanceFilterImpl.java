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
package io.camunda.zeebe.client.impl.search;

import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.variableValueFilter;

import io.camunda.zeebe.client.api.search.ProcessInstanceFilter;
import io.camunda.zeebe.client.api.search.VariableValueFilter;
import io.camunda.zeebe.client.impl.util.CollectionUtil;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceFilterRequest;
import io.camunda.zeebe.client.protocol.rest.VariableValueFilterRequest;
import java.util.List;
import java.util.function.Consumer;

public class ProcessInstanceFilterImpl
    extends TypedSearchRequestPropertyProvider<ProcessInstanceFilterRequest>
    implements ProcessInstanceFilter {

  private final ProcessInstanceFilterRequest filter;

  public ProcessInstanceFilterImpl() {
    filter = new ProcessInstanceFilterRequest();
  }

  @Override
  public ProcessInstanceFilter processInstanceKeys(final Long... values) {
    return processInstanceKeys(CollectionUtil.toList(values));
  }

  @Override
  public ProcessInstanceFilter processInstanceKeys(final List<Long> values) {
    filter.setKey(CollectionUtil.addValuesToList(filter.getKey(), values));
    return this;
  }

  @Override
  public ProcessInstanceFilter variable(final VariableValueFilter value) {
    final VariableValueFilterRequest variableFilter = provideSearchRequestProperty(value);
    filter.addVariablesItem(variableFilter);
    return this;
  }

  @Override
  public ProcessInstanceFilter variable(final Consumer<VariableValueFilter> fn) {
    return variable(variableValueFilter(fn));
  }

  @Override
  protected ProcessInstanceFilterRequest getSearchRequestProperty() {
    return filter;
  }
}
