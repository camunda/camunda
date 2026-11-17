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
package io.camunda.client.impl.search.filter;

import io.camunda.client.api.search.enums.WaitStateElementType;
import io.camunda.client.api.search.enums.WaitStateType;
import io.camunda.client.api.search.filter.ElementInstanceWaitStateFilter;
import io.camunda.client.api.search.filter.builder.BasicStringProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.filter.builder.WaitStateElementTypeProperty;
import io.camunda.client.api.search.filter.builder.WaitStateTypeProperty;
import io.camunda.client.impl.search.filter.builder.BasicStringPropertyImpl;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.filter.builder.WaitStateElementTypePropertyImpl;
import io.camunda.client.impl.search.filter.builder.WaitStateTypePropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.ParseUtil;
import java.util.function.Consumer;

public class ElementInstanceWaitStateFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.ElementInstanceWaitStateFilter>
    implements ElementInstanceWaitStateFilter {

  private final io.camunda.client.protocol.rest.ElementInstanceWaitStateFilter filter;

  public ElementInstanceWaitStateFilterImpl() {
    filter = new io.camunda.client.protocol.rest.ElementInstanceWaitStateFilter();
  }

  @Override
  public ElementInstanceWaitStateFilter elementInstanceKey(final long value) {
    return elementInstanceKey(b -> b.eq(ParseUtil.keyToString(value)));
  }

  @Override
  public ElementInstanceWaitStateFilter elementInstanceKey(final Consumer<BasicStringProperty> fn) {
    final BasicStringPropertyImpl property = new BasicStringPropertyImpl();
    fn.accept(property);
    filter.setElementInstanceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ElementInstanceWaitStateFilter processInstanceKey(final long value) {
    return processInstanceKey(b -> b.eq(ParseUtil.keyToString(value)));
  }

  @Override
  public ElementInstanceWaitStateFilter processInstanceKey(final Consumer<BasicStringProperty> fn) {
    final BasicStringPropertyImpl property = new BasicStringPropertyImpl();
    fn.accept(property);
    filter.setProcessInstanceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ElementInstanceWaitStateFilter rootProcessInstanceKey(final long value) {
    return rootProcessInstanceKey(b -> b.eq(ParseUtil.keyToString(value)));
  }

  @Override
  public ElementInstanceWaitStateFilter rootProcessInstanceKey(
      final Consumer<BasicStringProperty> fn) {
    final BasicStringPropertyImpl property = new BasicStringPropertyImpl();
    fn.accept(property);
    filter.setRootProcessInstanceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ElementInstanceWaitStateFilter elementId(final String value) {
    return elementId(b -> b.eq(value));
  }

  @Override
  public ElementInstanceWaitStateFilter elementId(final Consumer<StringProperty> fn) {
    final StringPropertyImpl property = new StringPropertyImpl();
    fn.accept(property);
    filter.setElementId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ElementInstanceWaitStateFilter elementType(final WaitStateElementType value) {
    return elementType(b -> b.eq(value));
  }

  @Override
  public ElementInstanceWaitStateFilter elementType(
      final Consumer<WaitStateElementTypeProperty> fn) {
    final WaitStateElementTypePropertyImpl property = new WaitStateElementTypePropertyImpl();
    fn.accept(property);
    filter.setElementType(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ElementInstanceWaitStateFilter waitStateType(final WaitStateType value) {
    return waitStateType(b -> b.eq(value));
  }

  @Override
  public ElementInstanceWaitStateFilter waitStateType(final Consumer<WaitStateTypeProperty> fn) {
    final WaitStateTypePropertyImpl property = new WaitStateTypePropertyImpl();
    fn.accept(property);
    filter.setWaitStateType(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.ElementInstanceWaitStateFilter
      getSearchRequestProperty() {
    return filter;
  }
}
