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
package io.camunda.client.api.search.filter;

import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.enums.JobState;
import io.camunda.client.api.search.enums.ListenerEventType;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.JobKindProperty;
import io.camunda.client.api.search.filter.builder.JobStateProperty;
import io.camunda.client.api.search.filter.builder.ListenerEventTypeProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

public interface JobFilter extends SearchRequestFilter {

  /**
   * Filters jobs by the specified deadline
   *
   * @param deadline the deadline of the job
   * @return the updated filter
   */
  JobFilter deadline(final OffsetDateTime deadline);

  /**
   * Filters jobs by the specified deadline using {@link DateTimeProperty} consumer.
   *
   * @param fn the deadline {@link DateTimeProperty} consumer of the job
   * @return the updated filter
   */
  JobFilter deadline(final Consumer<DateTimeProperty> fn);

  /**
   * Filters jobs by the specified denied reason
   *
   * @param deniedReason the reason why the job was denied
   * @return the updated filter
   */
  JobFilter deniedReason(final String deniedReason);

  /**
   * Filters jobs by the specified denied reason using {@link StringProperty} consumer.
   *
   * @param fn the denied reason {@link StringProperty} consumer of the job
   * @return the updated filter
   */
  JobFilter deniedReason(final Consumer<StringProperty> fn);

  /**
   * Filters jobs by the specified end time
   *
   * @param endTime the end time of the job
   * @return the updated filter
   */
  JobFilter endTime(final OffsetDateTime endTime);

  /**
   * Filters jobs by the specified end time using {@link DateTimeProperty} consumer.
   *
   * @param fn the end time {@link DateTimeProperty} consumer of the job
   * @return the updated filter
   */
  JobFilter endTime(final Consumer<DateTimeProperty> fn);

  /**
   * Filters jobs by the specified error code
   *
   * @param errorCode the error code of the job
   * @return the updated filter
   */
  JobFilter errorCode(final String errorCode);

  /**
   * Filters jobs by the specified error code using {@link StringProperty} consumer.
   *
   * @param fn the error code {@link StringProperty} consumer of the job
   * @return the updated filter
   */
  JobFilter errorCode(final Consumer<StringProperty> fn);

  /**
   * Filters jobs by the specified error message
   *
   * @param errorMessage the error message of the job
   * @return the updated filter
   */
  JobFilter errorMessage(final String errorMessage);

  /**
   * Filters jobs by the specified error message using {@link StringProperty} consumer.
   *
   * @param fn the error message {@link StringProperty} consumer of the job
   * @return the updated filter
   */
  JobFilter errorMessage(final Consumer<StringProperty> fn);

  /**
   * Filters jobs by the has failed with retries left flag.
   *
   * @param hasFailedWithRetriesLeft the flag indicating if the job has failed with retries left
   * @return the updated filter
   */
  JobFilter hasFailedWithRetriesLeft(final Boolean hasFailedWithRetriesLeft);

  /**
   * Filters jobs by the is denied flag.
   *
   * @param isDenied the flag indicating if the job is denied
   * @return the updated filter
   */
  JobFilter isDenied(final Boolean isDenied);

  /**
   * Filters jobs by the specified retries left.
   *
   * @param retries the number of retries left for the job
   * @return the updated filter
   */
  JobFilter retries(final Integer retries);

  /**
   * Filters jobs by the specified retries left using {@link IntegerProperty} consumer.
   *
   * @param fn the retries left {@link IntegerProperty} consumer of the job
   * @return the updated filter
   */
  JobFilter retries(final Consumer<IntegerProperty> fn);

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
