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

import io.camunda.client.api.search.enums.GlobalListenerSource;
import io.camunda.client.api.search.enums.GlobalTaskListenerEventType;
import io.camunda.client.api.search.filter.GlobalTaskListenerFilter;
import io.camunda.client.api.search.filter.builder.GlobalListenerSourceProperty;
import io.camunda.client.api.search.filter.builder.GlobalTaskListenerEventTypeProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.impl.search.filter.builder.GlobalListenerSourcePropertyImpl;
import io.camunda.client.impl.search.filter.builder.GlobalTaskListenerEventTypePropertyImpl;
import io.camunda.client.impl.search.filter.builder.IntegerPropertyImpl;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.protocol.rest.GlobalTaskListenerSearchQueryFilterRequest;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class GlobalTaskListenerFilterImpl
    extends TypedSearchRequestPropertyProvider<GlobalTaskListenerSearchQueryFilterRequest>
    implements GlobalTaskListenerFilter {

  private final GlobalTaskListenerSearchQueryFilterRequest filter;

  public GlobalTaskListenerFilterImpl() {
    filter = new GlobalTaskListenerSearchQueryFilterRequest();
  }

  @Override
  protected GlobalTaskListenerSearchQueryFilterRequest getSearchRequestProperty() {
    return filter;
  }

  @Override
  public GlobalTaskListenerFilter id(final String id) {
    return id(f -> f.eq(id));
  }

  @Override
  public GlobalTaskListenerFilter id(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public GlobalTaskListenerFilter type(final String type) {
    return type(f -> f.eq(type));
  }

  @Override
  public GlobalTaskListenerFilter type(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setType(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public GlobalTaskListenerFilter eventTypes(final List<GlobalTaskListenerEventType> eventTypes) {
    eventTypes.forEach(this::eventType);
    return this;
  }

  @Override
  public GlobalTaskListenerFilter eventTypes(final GlobalTaskListenerEventType... eventTypes) {
    return eventTypes(Arrays.asList(eventTypes));
  }

  @Override
  public GlobalTaskListenerFilter eventTypes(
      final Consumer<GlobalTaskListenerEventTypeProperty> fn) {
    final GlobalTaskListenerEventTypeProperty property =
        new GlobalTaskListenerEventTypePropertyImpl();
    fn.accept(property);
    filter.addEventTypesItem(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public GlobalTaskListenerFilter eventType(final GlobalTaskListenerEventType eventType) {
    return eventTypes(f -> f.eq(eventType));
  }

  @Override
  public GlobalTaskListenerFilter retries(final Integer retries) {
    return retries(f -> f.eq(retries));
  }

  @Override
  public GlobalTaskListenerFilter retries(final Consumer<IntegerProperty> fn) {
    final IntegerProperty property = new IntegerPropertyImpl();
    fn.accept(property);
    filter.setRetries(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public GlobalTaskListenerFilter afterNonGlobal(final Boolean afterNonGlobal) {
    filter.afterNonGlobal(afterNonGlobal);
    return this;
  }

  @Override
  public GlobalTaskListenerFilter beforeNonGlobal() {
    return afterNonGlobal(false);
  }

  @Override
  public GlobalTaskListenerFilter afterNonGlobal() {
    return afterNonGlobal(true);
  }

  @Override
  public GlobalTaskListenerFilter priority(final Integer priority) {
    return priority(f -> f.eq(priority));
  }

  @Override
  public GlobalTaskListenerFilter priority(final Consumer<IntegerProperty> fn) {
    final IntegerProperty property = new IntegerPropertyImpl();
    fn.accept(property);
    filter.setPriority(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public GlobalTaskListenerFilter source(final GlobalListenerSource source) {
    return source(f -> f.eq(source));
  }

  @Override
  public GlobalTaskListenerFilter source(final Consumer<GlobalListenerSourceProperty> fn) {
    final GlobalListenerSourceProperty property = new GlobalListenerSourcePropertyImpl();
    fn.accept(property);
    filter.source(provideSearchRequestProperty(property));
    return this;
  }
}
