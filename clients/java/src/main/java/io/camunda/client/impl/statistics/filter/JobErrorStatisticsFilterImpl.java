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

import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.statistics.filter.JobErrorStatisticsFilter;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import java.util.function.Consumer;

public class JobErrorStatisticsFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.JobErrorStatisticsFilter>
    implements JobErrorStatisticsFilter {

  private final io.camunda.client.protocol.rest.JobErrorStatisticsFilter filter;

  public JobErrorStatisticsFilterImpl() {
    filter = new io.camunda.client.protocol.rest.JobErrorStatisticsFilter();
  }

  @Override
  public JobErrorStatisticsFilter errorCode(final String errorCode) {
    return errorCode(b -> b.eq(errorCode));
  }

  @Override
  public JobErrorStatisticsFilter errorCode(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setErrorCode(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public JobErrorStatisticsFilter errorMessage(final String errorMessage) {
    return errorMessage(b -> b.eq(errorMessage));
  }

  @Override
  public JobErrorStatisticsFilter errorMessage(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setErrorMessage(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.JobErrorStatisticsFilter getSearchRequestProperty() {
    return filter;
  }
}
