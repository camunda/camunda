package io.camunda.client.impl.search.filter;

import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.enums.JobState;
import io.camunda.client.api.search.enums.ListenerEventType;
import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.JobKindProperty;
import io.camunda.client.api.search.filter.builder.JobStateProperty;
import io.camunda.client.api.search.filter.builder.ListenerEventTypeProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.impl.search.filter.builder.BasicLongPropertyImpl;
import io.camunda.client.impl.search.filter.builder.JobKindPropertyImpl;
import io.camunda.client.impl.search.filter.builder.JobStatePropertyImpl;
import io.camunda.client.impl.search.filter.builder.ListenerEventTypePropertyImpl;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.protocol.rest.JobFilterRequest;
import java.util.function.Consumer;

public class JobFilterImpl extends TypedSearchRequestPropertyProvider<JobFilterRequest>
    implements JobFilter {

  private final JobFilterRequest filter;

  public JobFilterImpl() {
    filter = new JobFilterRequest();
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
  protected JobFilterRequest getSearchRequestProperty() {
    return filter;
  }
}
