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

import io.camunda.client.api.search.filter.JobTypeStatisticsFilter;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import java.util.function.Consumer;

public class JobTypeStatisticsFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.JobTypeStatisticsFilter>
    implements JobTypeStatisticsFilter {

  private final io.camunda.client.protocol.rest.JobTypeStatisticsFilter filter;

  public JobTypeStatisticsFilterImpl() {
    filter = new io.camunda.client.protocol.rest.JobTypeStatisticsFilter();
  }

  @Override
  public JobTypeStatisticsFilter jobType(final String jobType) {
    jobType(b -> b.eq(jobType));
    return this;
  }

  @Override
  public JobTypeStatisticsFilter jobType(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setJobType(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.JobTypeStatisticsFilter getSearchRequestProperty() {
    return filter;
  }
}
