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

import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.IncidentErrorTypeProperty;
import io.camunda.client.api.search.filter.builder.IncidentStateProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

public interface ElementInstanceIncidentFilter extends SearchRequestFilter {

  /**
   * Filters incidents by the specified key.
   *
   * @param value the key of the incidents
   * @return the updated filter
   */
  ElementInstanceIncidentFilter incidentKey(final Long value);

  /**
   * Filters incidents by the specified keys using {@link BasicLongProperty} consumer.
   *
   * @param fn the incident keys {@link BasicLongProperty} consumer of the incidents
   * @return the updated filter
   */
  ElementInstanceIncidentFilter incidentKey(final Consumer<BasicLongProperty> fn);

  /**
   * Filters incidents by the process definition key.
   *
   * @param value the key of the process definition
   * @return the updated filter
   */
  ElementInstanceIncidentFilter processDefinitionKey(final Long value);

  /**
   * Filters incidents by the process definition keys using {@link BasicLongProperty} consumer.
   *
   * @param fn the process definition keys {@link BasicLongProperty} consumer
   * @return the updated filter
   */
  ElementInstanceIncidentFilter processDefinitionKey(final Consumer<BasicLongProperty> fn);

  /**
   * Filters incidents by the process definition id.
   *
   * @param value the bpmn process id of the process definition
   * @return the updated filter
   */
  ElementInstanceIncidentFilter processDefinitionId(final String value);

  /**
   * Filters incidents by the process definition ids using {@link StringProperty} consumer.
   *
   * @param fn the process definition ids {@link StringProperty} consumer of the incidents
   * @return the updated filter
   */
  ElementInstanceIncidentFilter processDefinitionId(final Consumer<StringProperty> fn);

  /**
   * Filters incidents by error type.
   *
   * @param errorType the error type of incident
   * @return the updated filter
   */
  ElementInstanceIncidentFilter errorType(final IncidentErrorType errorType);

  /**
   * Filters incidents by error type using {@link IncidentErrorTypeProperty} consumer.
   *
   * @param fn the error types {@link IncidentErrorTypeProperty} consumer of the incidents
   * @return the updated filter
   */
  ElementInstanceIncidentFilter errorType(final Consumer<IncidentErrorTypeProperty> fn);

  /**
   * Filters incidents by error message.
   *
   * @param errorMessage the message of incident
   * @return the updated filter
   */
  ElementInstanceIncidentFilter errorMessage(final String errorMessage);

  /**
   * Filters incidents by error message using {@link StringProperty} consumer.
   *
   * @param fn the error messages {@link StringProperty} consumer of the incidents
   * @return the updated filter
   */
  ElementInstanceIncidentFilter errorMessage(final Consumer<StringProperty> fn);

  /**
   * Filters incidents by the element id.
   *
   * @param value the id of element id.
   * @return the updated filter
   */
  ElementInstanceIncidentFilter elementId(final String value);

  /**
   * Filters incidents by the element ids using {@link StringProperty} consumer.
   *
   * @param fn the element ids {@link StringProperty} consumer of the incidents
   * @return the updated filter
   */
  ElementInstanceIncidentFilter elementId(final Consumer<StringProperty> fn);

  /**
   * Filters incidents by the process instance key.
   *
   * @param value the key of process instance.
   * @return the updated filter
   */
  ElementInstanceIncidentFilter processInstanceKey(final Long value);

  /**
   * Filters incidents by the process instance keys using {@link BasicLongProperty} consumer.
   *
   * @param fn the process instance keys {@link BasicLongProperty} consumer of the incidents
   * @return the updated filter
   */
  ElementInstanceIncidentFilter processInstanceKey(final Consumer<BasicLongProperty> fn);

  /**
   * Filters incidents by creation time of incident.
   *
   * @param creationTime the creation time of incident
   * @return the updated filter
   */
  ElementInstanceIncidentFilter creationTime(final OffsetDateTime creationTime);

  /**
   * Filters incidents by creation times using {@link DateTimeProperty} consumer.
   *
   * @param fn the creation times {@link DateTimeProperty} consumer of the incidents
   * @return the updated filter
   */
  ElementInstanceIncidentFilter creationTime(final Consumer<DateTimeProperty> fn);

  /**
   * Filters incidents by the state of incident.
   *
   * @param value the state of incident
   * @return the updated filter
   */
  ElementInstanceIncidentFilter state(final IncidentState value);

  /**
   * Filters incidents by state using {@link IncidentStateProperty} consumer.
   *
   * @param fn the states {@link IncidentStateProperty} consumer of the incidents
   * @return the updated filter
   */
  ElementInstanceIncidentFilter state(final Consumer<IncidentStateProperty> fn);

  /**
   * Filters incidents by job key.
   *
   * @param value the key of the job.
   * @return the updated filter
   */
  ElementInstanceIncidentFilter jobKey(final Long value);

  /**
   * Filters incidents by job keys using {@link BasicLongProperty} consumer.
   *
   * @param fn the job keys {@link BasicLongProperty} consumer of the incidents
   * @return the updated filter
   */
  ElementInstanceIncidentFilter jobKey(final Consumer<BasicLongProperty> fn);

  /**
   * Filters incidents by tenant id.
   *
   * @param value the id of tenant
   * @return the updated filter
   */
  ElementInstanceIncidentFilter tenantId(final String value);

  /**
   * Filters incidents by tenant ids using {@link StringProperty} consumer.
   *
   * @param fn the tenant ids {@link StringProperty} consumer of the incidents
   * @return the updated filter
   */
  ElementInstanceIncidentFilter tenantId(final Consumer<StringProperty> fn);
}
