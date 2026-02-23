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
package io.camunda.client.impl.statistics.filter;

import io.camunda.client.api.statistics.filter.ProcessDefinitionInstanceVersionStatisticsFilter;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import java.util.function.Consumer;

public class ProcessDefinitionInstanceVersionStatisticsFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.ProcessDefinitionInstanceVersionStatisticsFilter>
    implements ProcessDefinitionInstanceVersionStatisticsFilter {

  private final io.camunda.client.protocol.rest.ProcessDefinitionInstanceVersionStatisticsFilter
      filter;

  public ProcessDefinitionInstanceVersionStatisticsFilterImpl() {
    filter = new io.camunda.client.protocol.rest.ProcessDefinitionInstanceVersionStatisticsFilter();
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsFilter processDefinitionId(final String value) {
    filter.setProcessDefinitionId(value);
    return this;
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsFilter processDefinitionId(
      final Consumer<String> fn) {
    fn.accept(filter.getProcessDefinitionId());
    return this;
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsFilter tenantId(final String value) {
    filter.setTenantId(value);
    return this;
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsFilter tenantId(final Consumer<String> fn) {
    fn.accept(filter.getTenantId());
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.ProcessDefinitionInstanceVersionStatisticsFilter
      getSearchRequestProperty() {
    return filter;
  }
}
