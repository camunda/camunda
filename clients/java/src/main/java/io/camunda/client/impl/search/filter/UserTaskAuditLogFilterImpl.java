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

import io.camunda.client.api.search.enums.AuditLogActorTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.client.api.search.enums.AuditLogResultEnum;
import io.camunda.client.api.search.filter.UserTaskAuditLogFilter;
import io.camunda.client.api.search.filter.builder.AuditLogActorTypeFilterProperty;
import io.camunda.client.api.search.filter.builder.AuditLogOperationTypeFilterProperty;
import io.camunda.client.api.search.filter.builder.AuditLogResultFilterProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.impl.search.filter.builder.AuditLogActorTypeFilterPropertyImpl;
import io.camunda.client.impl.search.filter.builder.AuditLogOperationTypeFilterPropertyImpl;
import io.camunda.client.impl.search.filter.builder.AuditLogResultFilterPropertyImpl;
import io.camunda.client.impl.search.filter.builder.DateTimePropertyImpl;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

public class UserTaskAuditLogFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.UserTaskAuditLogFilter>
    implements UserTaskAuditLogFilter {

  private final io.camunda.client.protocol.rest.UserTaskAuditLogFilter filter;

  public UserTaskAuditLogFilterImpl() {
    filter = new io.camunda.client.protocol.rest.UserTaskAuditLogFilter();
  }

  @Override
  public UserTaskAuditLogFilter operationType(final AuditLogOperationTypeEnum value) {
    return operationType(b -> b.eq(value));
  }

  @Override
  public UserTaskAuditLogFilter operationType(
      final Consumer<AuditLogOperationTypeFilterProperty> fn) {
    final AuditLogOperationTypeFilterProperty property =
        new AuditLogOperationTypeFilterPropertyImpl();
    fn.accept(property);
    filter.setOperationType(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public UserTaskAuditLogFilter result(final AuditLogResultEnum value) {
    return result(b -> b.eq(value));
  }

  @Override
  public UserTaskAuditLogFilter result(final Consumer<AuditLogResultFilterProperty> fn) {
    final AuditLogResultFilterProperty property = new AuditLogResultFilterPropertyImpl();
    fn.accept(property);
    filter.setResult(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public UserTaskAuditLogFilter timestamp(final OffsetDateTime value) {
    return timestamp(b -> b.eq(value));
  }

  @Override
  public UserTaskAuditLogFilter timestamp(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setTimestamp(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public UserTaskAuditLogFilter actorType(final AuditLogActorTypeEnum value) {
    return actorType(b -> b.eq(value));
  }

  @Override
  public UserTaskAuditLogFilter actorType(final Consumer<AuditLogActorTypeFilterProperty> fn) {
    final AuditLogActorTypeFilterProperty property = new AuditLogActorTypeFilterPropertyImpl();
    fn.accept(property);
    filter.setActorType(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public UserTaskAuditLogFilter actorId(final String value) {
    return actorId(b -> b.eq(value));
  }

  @Override
  public UserTaskAuditLogFilter actorId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setActorId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.UserTaskAuditLogFilter getSearchRequestProperty() {
    return filter;
  }
}
