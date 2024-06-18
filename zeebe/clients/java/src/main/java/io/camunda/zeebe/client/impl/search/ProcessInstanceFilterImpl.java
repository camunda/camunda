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

import io.camunda.zeebe.client.api.search.ProcessInstanceFilter;
import io.camunda.zeebe.client.api.search.VariableValueFilter;
import java.util.List;
import java.util.function.Consumer;

public class ProcessInstanceFilterImpl
    extends TypedQueryProperty<io.camunda.zeebe.client.protocol.rest.ProcessInstanceFilter>
    implements ProcessInstanceFilter {

  private final io.camunda.zeebe.client.protocol.rest.ProcessInstanceFilter filter;

  public ProcessInstanceFilterImpl() {
    filter = new io.camunda.zeebe.client.protocol.rest.ProcessInstanceFilter();
  }

  @Override
  public ProcessInstanceFilter processInstanceKeys(final Long value, final Long... values) {
    return processInstanceKeys(ProcessInstanceFilter.collectValues(value, values));
  }

  @Override
  public ProcessInstanceFilter processInstanceKeys(final List<Long> values) {
    filter.setKey(ProcessInstanceFilter.addValuesToList(filter.getKey(), values));
    return this;
  }

  @Override
  public ProcessInstanceFilter variable(final VariableValueFilter value) {
    final VariableValueFilterImpl variableFilter = (VariableValueFilterImpl) value;
    filter.addVariablesItem(variableFilter.getQueryProperty());
    return this;
  }

  @Override
  public ProcessInstanceFilter variable(final Consumer<VariableValueFilter> fn) {
    final VariableValueFilterImpl f = new VariableValueFilterImpl();
    fn.accept(f);
    return variable(f);
  }

  @Override
  protected io.camunda.zeebe.client.protocol.rest.ProcessInstanceFilter getQueryProperty() {
    return filter;
  }
}
