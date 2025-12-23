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
import io.camunda.client.api.search.enums.AuditLogCategoryEnum;
import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.client.api.search.enums.AuditLogResultEnum;
import io.camunda.client.api.search.filter.AuditLogFilter;
import io.camunda.client.api.search.filter.builder.AuditLogActorTypeFilterProperty;
import io.camunda.client.api.search.filter.builder.AuditLogCategoryFilterProperty;
import io.camunda.client.api.search.filter.builder.AuditLogEntityKeyFilterProperty;
import io.camunda.client.api.search.filter.builder.AuditLogEntityTypeFilterProperty;
import io.camunda.client.api.search.filter.builder.AuditLogKeyFilterProperty;
import io.camunda.client.api.search.filter.builder.AuditLogOperationTypeFilterProperty;
import io.camunda.client.api.search.filter.builder.AuditLogResultFilterProperty;
import io.camunda.client.api.search.filter.builder.BasicStringProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.impl.search.filter.builder.AuditLogActorTypeFilterPropertyImpl;
import io.camunda.client.impl.search.filter.builder.AuditLogCategoryFilterPropertyImpl;
import io.camunda.client.impl.search.filter.builder.AuditLogEntityKeyFilterPropertyImpl;
import io.camunda.client.impl.search.filter.builder.AuditLogEntityTypeFilterPropertyImpl;
import io.camunda.client.impl.search.filter.builder.AuditLogKeyFilterPropertyImpl;
import io.camunda.client.impl.search.filter.builder.AuditLogOperationTypeFilterPropertyImpl;
import io.camunda.client.impl.search.filter.builder.AuditLogResultFilterPropertyImpl;
import io.camunda.client.impl.search.filter.builder.BasicStringPropertyImpl;
import io.camunda.client.impl.search.filter.builder.DateTimePropertyImpl;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

public class AuditLogFilterImpl
    extends TypedSearchRequestPropertyProvider<io.camunda.client.protocol.rest.AuditLogFilter>
    implements AuditLogFilter {

  private final io.camunda.client.protocol.rest.AuditLogFilter filter;

  public AuditLogFilterImpl() {
    filter = new io.camunda.client.protocol.rest.AuditLogFilter();
  }

  @Override
  public AuditLogFilter auditLogKey(final String value) {
    return auditLogKey(b -> b.eq(value));
  }

  @Override
  public AuditLogFilter auditLogKey(final Consumer<AuditLogKeyFilterProperty> fn) {
    final AuditLogKeyFilterProperty property = new AuditLogKeyFilterPropertyImpl();
    fn.accept(property);
    filter.setAuditLogKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AuditLogFilter processDefinitionKey(final String value) {
    return processDefinitionKey(b -> b.eq(value));
  }

  @Override
  public AuditLogFilter processDefinitionKey(final Consumer<BasicStringProperty> fn) {
    final BasicStringProperty property = new BasicStringPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AuditLogFilter processInstanceKey(final String value) {
    return processInstanceKey(b -> b.eq(value));
  }

  @Override
  public AuditLogFilter processInstanceKey(final Consumer<BasicStringProperty> fn) {
    final BasicStringProperty property = new BasicStringPropertyImpl();
    fn.accept(property);
    filter.setProcessInstanceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AuditLogFilter elementInstanceKey(final String value) {
    return elementInstanceKey(b -> b.eq(value));
  }

  @Override
  public AuditLogFilter elementInstanceKey(final Consumer<BasicStringProperty> fn) {
    final BasicStringProperty property = new BasicStringPropertyImpl();
    fn.accept(property);
    filter.setElementInstanceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AuditLogFilter operationType(final AuditLogOperationTypeEnum value) {
    return operationType(b -> b.eq(value));
  }

  @Override
  public AuditLogFilter operationType(final Consumer<AuditLogOperationTypeFilterProperty> fn) {
    final AuditLogOperationTypeFilterProperty property =
        new AuditLogOperationTypeFilterPropertyImpl();
    fn.accept(property);
    filter.operationType(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AuditLogFilter timestamp(final OffsetDateTime value) {
    return timestamp(b -> b.eq(value));
  }

  @Override
  public AuditLogFilter timestamp(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setTimestamp(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AuditLogFilter actorId(final String value) {
    return actorId(b -> b.eq(value));
  }

  @Override
  public AuditLogFilter actorId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setActorId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AuditLogFilter actorType(final AuditLogActorTypeEnum fn) {
    return actorType(b -> b.eq(fn));
  }

  @Override
  public AuditLogFilter actorType(final Consumer<AuditLogActorTypeFilterProperty> fn) {
    final AuditLogActorTypeFilterProperty property = new AuditLogActorTypeFilterPropertyImpl();
    fn.accept(property);
    filter.setActorType(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AuditLogFilter entityType(final AuditLogEntityTypeEnum entityType) {
    return entityType(b -> b.eq(entityType));
  }

  @Override
  public AuditLogFilter entityType(final Consumer<AuditLogEntityTypeFilterProperty> fn) {
    final AuditLogEntityTypeFilterProperty property = new AuditLogEntityTypeFilterPropertyImpl();
    fn.accept(property);
    filter.setEntityType(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AuditLogFilter entityKey(final String entityKey) {
    return entityKey(b -> b.eq(entityKey));
  }

  @Override
  public AuditLogFilter entityKey(final Consumer<AuditLogEntityKeyFilterProperty> fn) {
    final AuditLogEntityKeyFilterProperty property = new AuditLogEntityKeyFilterPropertyImpl();
    fn.accept(property);
    filter.setEntityKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AuditLogFilter tenantId(final String value) {
    return tenantId(b -> b.eq(value));
  }

  @Override
  public AuditLogFilter tenantId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setTenantId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AuditLogFilter result(final AuditLogResultEnum result) {
    return result(b -> b.eq(result));
  }

  @Override
  public AuditLogFilter result(final Consumer<AuditLogResultFilterProperty> fn) {
    final AuditLogResultFilterProperty property = new AuditLogResultFilterPropertyImpl();
    fn.accept(property);
    filter.setResult(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AuditLogFilter category(final AuditLogCategoryEnum category) {
    return category(b -> b.eq(category));
  }

  @Override
  public AuditLogFilter category(final Consumer<AuditLogCategoryFilterProperty> fn) {
    final AuditLogCategoryFilterProperty property = new AuditLogCategoryFilterPropertyImpl();
    fn.accept(property);
    filter.setCategory(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AuditLogFilter deploymentKey(final String value) {
    deploymentKey(b -> b.eq(value));
    return this;
  }

  @Override
  public AuditLogFilter deploymentKey(final Consumer<BasicStringProperty> fn) {
    final BasicStringProperty property = new BasicStringPropertyImpl();
    fn.accept(property);
    filter.setDeploymentKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AuditLogFilter formKey(final String value) {
    formKey(b -> b.eq(value));
    return this;
  }

  @Override
  public AuditLogFilter formKey(final Consumer<BasicStringProperty> fn) {
    final BasicStringProperty property = new BasicStringPropertyImpl();
    fn.accept(property);
    filter.setFormKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AuditLogFilter resourceKey(final String value) {
    resourceKey(b -> b.eq(value));
    return this;
  }

  @Override
  public AuditLogFilter resourceKey(final Consumer<BasicStringProperty> fn) {
    final BasicStringProperty property = new BasicStringPropertyImpl();
    fn.accept(property);
    filter.setResourceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.AuditLogFilter getSearchRequestProperty() {
    return filter;
  }
}
