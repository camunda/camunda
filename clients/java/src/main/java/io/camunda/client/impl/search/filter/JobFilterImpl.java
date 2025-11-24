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

import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.enums.JobState;
import io.camunda.client.api.search.enums.ListenerEventType;
import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.JobKindProperty;
import io.camunda.client.api.search.filter.builder.JobStateProperty;
import io.camunda.client.api.search.filter.builder.ListenerEventTypeProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.impl.search.filter.builder.BasicLongPropertyImpl;
import io.camunda.client.impl.search.filter.builder.DateTimePropertyImpl;
import io.camunda.client.impl.search.filter.builder.IntegerPropertyImpl;
import io.camunda.client.impl.search.filter.builder.JobKindPropertyImpl;
import io.camunda.client.impl.search.filter.builder.JobStatePropertyImpl;
import io.camunda.client.impl.search.filter.builder.ListenerEventTypePropertyImpl;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

public class JobFilterImpl
    extends TypedSearchRequestPropertyProvider<io.camunda.client.protocol.rest.JobFilter>
    implements JobFilter {

  private final io.camunda.client.protocol.rest.JobFilter filter;

  public JobFilterImpl() {
    filter = new io.camunda.client.protocol.rest.JobFilter();
  }

  @Override
  public JobFilter deadline(final OffsetDateTime deadline) {
    deadline(b -> b.eq(deadline));
    return this;
  }

  @Override
  public JobFilter deadline(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setDeadline(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public JobFilter deniedReason(final String deniedReason) {
    deniedReason(b -> b.eq(deniedReason));
    return this;
  }

  @Override
  public JobFilter deniedReason(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setDeniedReason(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public JobFilter endTime(final OffsetDateTime endTime) {
    endTime(b -> b.eq(endTime));
    return this;
  }

  @Override
  public JobFilter endTime(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setEndTime(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public JobFilter errorCode(final String errorCode) {
    errorCode(b -> b.eq(errorCode));
    return this;
  }

  @Override
  public JobFilter errorCode(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setErrorCode(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public JobFilter errorMessage(final String errorMessage) {
    errorMessage(b -> b.eq(errorMessage));
    return this;
  }

  @Override
  public JobFilter errorMessage(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setErrorMessage(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public JobFilter hasFailedWithRetriesLeft(final Boolean hasFailedWithRetriesLeft) {
    filter.setHasFailedWithRetriesLeft(hasFailedWithRetriesLeft);
    return this;
  }

  @Override
  public JobFilter isDenied(final Boolean isDenied) {
    filter.setIsDenied(isDenied);
    return this;
  }

  @Override
  public JobFilter retries(final Integer retries) {
    retries(b -> b.eq(retries));
    return this;
  }

  @Override
  public JobFilter retries(final Consumer<IntegerProperty> fn) {
    final IntegerProperty property = new IntegerPropertyImpl();
    fn.accept(property);
    filter.setRetries(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public JobFilter jobKey(final Long value) {
    jobKey(b -> b.eq(value));
    return this;
  }

  @Override
  public JobFilter jobKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setJobKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public JobFilter type(final String type) {
    type(b -> b.eq(type));
    return this;
  }

  @Override
  public JobFilter type(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setType(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public JobFilter worker(final String worker) {
    worker(b -> b.eq(worker));
    return this;
  }

  @Override
  public JobFilter worker(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setWorker(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public JobFilter state(final JobState state) {
    state(b -> b.eq(state));
    return this;
  }

  @Override
  public JobFilter state(final Consumer<JobStateProperty> fn) {
    final JobStateProperty property = new JobStatePropertyImpl();
    fn.accept(property);
    filter.setState(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public JobFilter kind(final JobKind kind) {
    kind(b -> b.eq(kind));
    return this;
  }

  @Override
  public JobFilter kind(final Consumer<JobKindProperty> fn) {
    final JobKindProperty property = new JobKindPropertyImpl();
    fn.accept(property);
    filter.setKind(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public JobFilter listenerEventType(final ListenerEventType listenerEventType) {
    listenerEventType(b -> b.eq(listenerEventType));
    return this;
  }

  @Override
  public JobFilter listenerEventType(final Consumer<ListenerEventTypeProperty> fn) {
    final ListenerEventTypeProperty property = new ListenerEventTypePropertyImpl();
    fn.accept(property);
    filter.setListenerEventType(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public JobFilter processDefinitionId(final String processDefinitionId) {
    processDefinitionId(b -> b.eq(processDefinitionId));
    return this;
  }

  @Override
  public JobFilter processDefinitionId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public JobFilter processDefinitionKey(final Long processDefinitionKey) {
    processDefinitionKey(b -> b.eq(processDefinitionKey));
    return this;
  }

  @Override
  public JobFilter processDefinitionKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public JobFilter processInstanceKey(final Long processInstanceKey) {
    processInstanceKey(b -> b.eq(processInstanceKey));
    return this;
  }

  @Override
  public JobFilter processInstanceKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setProcessInstanceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public JobFilter elementId(final String elementId) {
    elementId(b -> b.eq(elementId));
    return this;
  }

  @Override
  public JobFilter elementId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setElementId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public JobFilter elementInstanceKey(final Long elementInstanceKey) {
    elementInstanceKey(b -> b.eq(elementInstanceKey));
    return this;
  }

  @Override
  public JobFilter elementInstanceKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setElementInstanceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public JobFilter tenantId(final String tenantId) {
    tenantId(b -> b.eq(tenantId));
    return this;
  }

  @Override
  public JobFilter tenantId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setTenantId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public JobFilter creationTime(final OffsetDateTime creationTime) {
    creationTime(b -> b.eq(creationTime));
    return this;
  }

  @Override
  public JobFilter creationTime(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setCreationTime(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public JobFilter lastUpdateTime(final OffsetDateTime lastUpdateTime) {
    lastUpdateTime(b -> b.eq(lastUpdateTime));
    return this;
  }

  @Override
  public JobFilter lastUpdateTime(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setLastUpdateTime(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.JobFilter getSearchRequestProperty() {
    return filter;
  }
}
