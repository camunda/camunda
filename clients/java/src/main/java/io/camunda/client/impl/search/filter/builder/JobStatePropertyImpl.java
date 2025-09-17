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
package io.camunda.client.impl.search.filter.builder;

import io.camunda.client.api.search.enums.JobState;
import io.camunda.client.api.search.filter.builder.JobStateProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.JobStateEnum;
import io.camunda.client.protocol.rest.JobStateFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class JobStatePropertyImpl extends TypedSearchRequestPropertyProvider<JobStateFilterProperty>
    implements JobStateProperty {

  private final JobStateFilterProperty filterProperty = new JobStateFilterProperty();

  @Override
  public JobStateProperty eq(final JobState value) {
    filterProperty.set$Eq(EnumUtil.convert(value, JobStateEnum.class));
    return this;
  }

  @Override
  public JobStateProperty neq(final JobState value) {
    filterProperty.set$Neq(EnumUtil.convert(value, JobStateEnum.class));
    return this;
  }

  @Override
  public JobStateProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public JobStateProperty in(final List<JobState> values) {
    filterProperty.set$In(
        values.stream()
            .map(source -> (EnumUtil.convert(source, JobStateEnum.class)))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public JobStateProperty in(final JobState... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected JobStateFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }

  @Override
  public JobStateProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }
}
