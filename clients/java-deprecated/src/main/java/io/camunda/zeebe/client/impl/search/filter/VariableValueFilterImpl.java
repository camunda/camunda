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
package io.camunda.zeebe.client.impl.search.filter;

import io.camunda.zeebe.client.api.search.filter.VariableValueFilter;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.protocol.rest.VariableValueFilterProperty;

/**
 * @deprecated since 8.8 for removal in 8.10, replaced by the new Camunda Client Java. Please see
 *     the migration guide:
 *     https://docs.camunda.io/docs/8.8/apis-tools/migration-manuals/migrate-to-camunda-java-client/
 */
@Deprecated
public class VariableValueFilterImpl
    extends TypedSearchRequestPropertyProvider<VariableValueFilterProperty>
    implements VariableValueFilter {

  private final VariableValueFilterProperty filter;

  public VariableValueFilterImpl() {
    filter = new VariableValueFilterProperty();
  }

  @Override
  public VariableValueFilter name(final String value) {
    filter.setName(value);
    return this;
  }

  @Override
  public VariableValueFilter eq(final Object value) {
    filter.setEq(value);
    return this;
  }

  @Override
  public VariableValueFilter gt(final Object value) {
    filter.setGt(value);
    return this;
  }

  @Override
  public VariableValueFilter gte(final Object value) {
    filter.setGte(value);
    return this;
  }

  @Override
  public VariableValueFilter lt(final Object value) {
    filter.setLt(value);
    return this;
  }

  @Override
  public VariableValueFilter lte(final Object value) {
    filter.setLte(value);
    return this;
  }

  @Override
  protected VariableValueFilterProperty getSearchRequestProperty() {
    return filter;
  }
}
