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

import io.camunda.client.api.search.enums.GlobalExecutionListenerEventType;
import io.camunda.client.api.search.enums.GlobalListenerSource;
import io.camunda.client.api.search.filter.GlobalExecutionListenerFilter;
import io.camunda.client.api.search.filter.builder.GlobalExecutionListenerEventTypeProperty;
import io.camunda.client.api.search.filter.builder.GlobalListenerSourceProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.impl.search.filter.builder.GlobalExecutionListenerEventTypePropertyImpl;
import io.camunda.client.impl.search.filter.builder.GlobalListenerSourcePropertyImpl;
import io.camunda.client.impl.search.filter.builder.IntegerPropertyImpl;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.protocol.rest.GlobalExecutionListenerSearchQueryFilterRequest;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class GlobalExecutionListenerFilterImpl
    extends TypedSearchRequestPropertyProvider<GlobalExecutionListenerSearchQueryFilterRequest>
    implements GlobalExecutionListenerFilter {

  private final GlobalExecutionListenerSearchQueryFilterRequest filter;

  public GlobalExecutionListenerFilterImpl() {
    filter = new GlobalExecutionListenerSearchQueryFilterRequest();
  }

  @Override
  protected GlobalExecutionListenerSearchQueryFilterRequest getSearchRequestProperty() {
    return filter;
  }

  @Override
  public GlobalExecutionListenerFilter id(final String id) {
    return id(f -> f.eq(id));
  }

  @Override
  public GlobalExecutionListenerFilter id(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public GlobalExecutionListenerFilter type(final String type) {
    return type(f -> f.eq(type));
  }

  @Override
  public GlobalExecutionListenerFilter type(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setType(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public GlobalExecutionListenerFilter eventTypes(
      final List<GlobalExecutionListenerEventType> eventTypes) {
    eventTypes.forEach(this::eventType);
    return this;
  }

  @Override
  public GlobalExecutionListenerFilter eventTypes(
      final GlobalExecutionListenerEventType... eventTypes) {
    return eventTypes(Arrays.asList(eventTypes));
  }

  @Override
  public GlobalExecutionListenerFilter eventTypes(
      final Consumer<GlobalExecutionListenerEventTypeProperty> fn) {
    final GlobalExecutionListenerEventTypeProperty property =
        new GlobalExecutionListenerEventTypePropertyImpl();
    fn.accept(property);
    filter.addEventTypesItem(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public GlobalExecutionListenerFilter eventType(final GlobalExecutionListenerEventType eventType) {
    return eventTypes(f -> f.eq(eventType));
  }

  @Override
  public GlobalExecutionListenerFilter retries(final Integer retries) {
    return retries(f -> f.eq(retries));
  }

  @Override
  public GlobalExecutionListenerFilter retries(final Consumer<IntegerProperty> fn) {
    final IntegerProperty property = new IntegerPropertyImpl();
    fn.accept(property);
    filter.setRetries(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public GlobalExecutionListenerFilter afterNonGlobal(final Boolean afterNonGlobal) {
    filter.afterNonGlobal(afterNonGlobal);
    return this;
  }

  @Override
  public GlobalExecutionListenerFilter beforeNonGlobal() {
    return afterNonGlobal(false);
  }

  @Override
  public GlobalExecutionListenerFilter afterNonGlobal() {
    return afterNonGlobal(true);
  }

  @Override
  public GlobalExecutionListenerFilter priority(final Integer priority) {
    return priority(f -> f.eq(priority));
  }

  @Override
  public GlobalExecutionListenerFilter priority(final Consumer<IntegerProperty> fn) {
    final IntegerProperty property = new IntegerPropertyImpl();
    fn.accept(property);
    filter.setPriority(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public GlobalExecutionListenerFilter source(final GlobalListenerSource source) {
    return source(f -> f.eq(source));
  }

  @Override
  public GlobalExecutionListenerFilter source(final Consumer<GlobalListenerSourceProperty> fn) {
    final GlobalListenerSourceProperty property = new GlobalListenerSourcePropertyImpl();
    fn.accept(property);
    filter.source(provideSearchRequestProperty(property));
    return this;
  }
}
