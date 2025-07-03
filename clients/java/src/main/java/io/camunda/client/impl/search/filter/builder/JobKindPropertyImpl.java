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

import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.filter.builder.JobKindProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.JobKindEnum;
import io.camunda.client.protocol.rest.JobKindFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class JobKindPropertyImpl extends TypedSearchRequestPropertyProvider<JobKindFilterProperty>
    implements JobKindProperty {

  private final JobKindFilterProperty filterProperty = new JobKindFilterProperty();

  @Override
  public JobKindProperty eq(final JobKind value) {
    filterProperty.set$Eq(EnumUtil.convert(value, JobKindEnum.class));
    return this;
  }

  @Override
  public JobKindProperty neq(final JobKind value) {
    filterProperty.set$Neq(EnumUtil.convert(value, JobKindEnum.class));
    return this;
  }

  @Override
  public JobKindProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public JobKindProperty in(final List<JobKind> value) {
    filterProperty.set$In(
        value.stream()
            .map(source -> (EnumUtil.convert(source, JobKindEnum.class)))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public JobKindProperty in(final JobKind... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected JobKindFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }

  @Override
  public JobKindProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }
}
