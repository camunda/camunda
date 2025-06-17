package io.camunda.client.api.search.filter;

import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.enums.JobState;
import io.camunda.client.api.search.enums.ListenerEventType;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.JobKindProperty;
import io.camunda.client.api.search.filter.builder.JobStateProperty;
import io.camunda.client.api.search.filter.builder.ListenerEventTypeProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedSearchRequest.SearchRequestFilter;
import java.util.function.Consumer;

public interface JobFilter extends SearchRequestFilter {
  /**
   * Filters jobs by the specified job key.
   *
   * @param value the key of the job
   * @return the updated filter
   */
  JobFilter jobKey(final Long value);

  /**
   * Filters jobs by the specified job keys using {@link BasicLongProperty} consumer.
   *
   * @param fn the job keys {@link BasicLongProperty} consumer of the job
   * @return the updated filter
   */
  JobFilter jobKey(final Consumer<BasicLongProperty> fn);

  /**
   * Filters jobs by the specified type.
   *
   * @param type the type of the job
   * @return the updated filter
   */
  JobFilter type(final String type);

  /**
   * Filters jobs by the specified type using {@link StringProperty} consumer.
   *
   * @param fn the type {@link StringProperty} consumer of the job
   * @return the updated filter
   */
  JobFilter type(final Consumer<StringProperty> fn);

  /**
   * Filters jobs by the specified worker.
   *
   * @param worker the worker of the job
   * @return the updated filter
   */
  JobFilter worker(final String worker);

  /**
   * Filters jobs by the specified worker using {@link StringProperty} consumer.
   *
   * @param fn the worker {@link StringProperty} consumer of the job
   * @return the updated filter
   */
  JobFilter worker(final Consumer<StringProperty> fn);

  /**
   * Filters jobs by the specified state.
   *
   * @param state the state of the job
   * @return the updated filter
   */
  JobFilter state(final JobState state);

  /**
   * Filters jobs by the specified state using {@link JobStateProperty} consumer.
   *
   * @param fn the state {@link JobStateProperty} consumer of the job
   * @return the updated filter
   */
  JobFilter state(final Consumer<JobStateProperty> fn);

  /**
   * Filters jobs by the specified kind.
   *
   * @param kind the kind of the job
   * @return the updated filter
   */
  JobFilter kind(final JobKind kind);

  /**
   * Filters jobs by the specified kind using {@link JobKindProperty} consumer.
   *
   * @param fn the kind {@link JobKindProperty} consumer of the job
   * @return the updated filter
   */
  JobFilter kind(final Consumer<JobKindProperty> fn);

  /**
   * Filters jobs by the specified listener event type.
   *
   * @param listenerEventType the event type of the job listener
   * @return the updated filter
   */
  JobFilter listenerEventType(final ListenerEventType listenerEventType);

  /**
   * Filters jobs by the specified listener event type using {@link ListenerEventTypeProperty}
   * consumer.
   *
   * @param fn the listener event type {@link ListenerEventTypeProperty} consumer of the job
   * @return the updated filter
   */
  JobFilter listenerEventType(final Consumer<ListenerEventTypeProperty> fn);

  /**
   * Filters jobs by the specified process definition id.
   *
   * @param processDefinitionId the id of the process definition
   * @return the updated filter
   */
  JobFilter processDefinitionId(final String processDefinitionId);

  /**
   * Filters jobs by the specified process definition id using {@link StringProperty} consumer.
   *
   * @param fn the process definition id {@link StringProperty} consumer of the job
   * @return the updated filter
   */
  JobFilter processDefinitionId(final Consumer<StringProperty> fn);

  /**
   * Filters jobs by the specified process definition key.
   *
   * @param processDefinitionKey the key of the process definition
   * @return the updated filter
   */
  JobFilter processDefinitionKey(final Long processDefinitionKey);

  /**
   * Filters jobs by the specified process definition key using {@link BasicLongProperty} consumer.
   *
   * @param fn the process definition key {@link BasicLongProperty} consumer of the job
   * @return the updated filter
   */
  JobFilter processDefinitionKey(final Consumer<BasicLongProperty> fn);

  /**
   * Filters jobs by the specified process instance key
   *
   * @param processInstanceKey the key of the process instance
   * @return the updated filter
   */
  JobFilter processInstanceKey(final Long processInstanceKey);

  /**
   * Filters jobs by the specified process instance key using {@link BasicLongProperty} consumer.
   *
   * @param fn the process instance key {@link BasicLongProperty} consumer of the job
   * @return the updated filter
   */
  JobFilter processInstanceKey(final Consumer<BasicLongProperty> fn);

  /**
   * Filters jobs by the specified element id.
   *
   * @param elementId the id of the element
   * @return the updated filter
   */
  JobFilter elementId(final String elementId);

  /**
   * Filters jobs by the specified element id using {@link StringProperty} consumer.
   *
   * @param fn the element id {@link StringProperty} consumer of the job
   * @return the updated filter
   */
  JobFilter elementId(final Consumer<StringProperty> fn);

  /**
   * Filters jobs by the element instance key.
   *
   * @param elementInstanceKey the key of the element instance
   * @return the updated filter
   */
  JobFilter elementInstanceKey(final Long elementInstanceKey);

  /**
   * Filters jobs by the element instance key using {@link BasicLongProperty} consumer.
   *
   * @param fn the element instance key {@link BasicLongProperty} consumer of the job
   * @return the updated filter
   */
  JobFilter elementInstanceKey(final Consumer<BasicLongProperty> fn);

  /**
   * Filters jobs by the specified tenant id.
   *
   * @param tenantId the id of the tenant
   * @return the updated filter
   */
  JobFilter tenantId(final String tenantId);

  /**
   * Filters jobs by the specified tenant id using {@link StringProperty} consumer.
   *
   * @param fn the tenant id {@link StringProperty} consumer of the job
   * @return the updated filter
   */
  JobFilter tenantId(final Consumer<StringProperty> fn);
}
