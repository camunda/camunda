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
package io.camunda.zeebe.protocol.record.value;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import java.util.List;
import org.immutables.value.Value;

/**
 * Represents a global listener configuration in the Zeebe protocol.
 *
 * <p>A global listener is notified of specific events occurring in the cluster, regardless of the
 * process instance or scope. This record value describes the configuration and properties of such a
 * listener.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableGlobalListenerRecordValue.Builder.class)
public interface GlobalListenerRecordValue extends RecordValue {

  int DEFAULT_RETRIES = 3;
  int DEFAULT_PRIORITY = 50;
  GlobalListenerSource DEFAULT_SOURCE = GlobalListenerSource.CONFIGURATION;
  GlobalListenerType DEFAULT_LISTENER_TYPE = GlobalListenerType.USER_TASK;

  /**
   * Returns the unique key of the global listener.
   *
   * @return the global listener's unique key
   */
  Long getGlobalListenerKey();

  /**
   * Returns the identifier of the global listener. This ID, together with the listener type,
   * uniquely identifies the listener across the cluster.
   *
   * <p>This ID is used to reference and manage the listener through APIs.
   *
   * @return the global listener's unique identifier
   */
  String getId();

  /**
   * Returns the job type of the global listener.
   *
   * <p>The type is used as a reference to specify which job workers request the respective listener
   * job.
   *
   * @return the job type of the global listener
   */
  String getType();

  /**
   * Returns the maximum number of retries allowed for this listener.
   *
   * <p>If the listener fails to process an event, it may be retried up to this number.
   *
   * @return the retry count
   */
  int getRetries();

  /**
   * Returns the list of event types this listener is interested in.
   *
   * <p>The listener will be notified only for these event types.
   *
   * <p>For global user task listeners, valid event types include:
   *
   * <ul>
   *   <li>"CREATING": when a user task is created
   *   <li>"ASSIGNING": when a user task is assigned to a user or unassigned
   *   <li>"UPDATING": when a user task information is updated
   *   <li>"COMPLETING": when a user task is completed
   *   <li>"CANCELING": when a user task is canceled
   *   <li>"ALL": triggered by all of the above
   * </ul>
   *
   * @return the list of event types
   */
  List<String> getEventTypes();

  /**
   * Indicates whether this listener should be executed after all non-global listeners.
   *
   * <p>If {@code true}, the listener is invoked after non-global listeners for the same event,
   * otherwise before them.
   *
   * @return {@code true} if executed after non-global listeners, otherwise {@code false}
   */
  boolean isAfterNonGlobal();

  /**
   * Returns the priority of the listener.
   *
   * <p>Higher priority listeners are executed before lower priority ones.
   *
   * @return the priority value
   */
  int getPriority();

  /**
   * Returns the source of the global listener.
   *
   * <p>The source indicates how or where the listener was registered. The possible values are:
   *
   * <ul>
   *   <li>{@link GlobalListenerSource#API}: registered via API
   *   <li>{@link GlobalListenerSource#CONFIGURATION}: registered via configuration file</
   * </ul>
   *
   * @return the listener source
   */
  GlobalListenerSource getSource();

  /**
   * Returns the listener type of the global listener.
   *
   * <p>Supported types are {@link GlobalListenerType#USER_TASK} and {@link
   * GlobalListenerType#EXECUTION}.
   *
   * @return the listener type
   */
  GlobalListenerType getListenerType();

  /**
   * Returns the list of fine-grained BPMN element types this listener targets.
   *
   * <p>Element types are combined with {@link #getCategories()} as a union to determine which
   * elements trigger this listener. If both element types and categories are empty, the listener
   * applies to all elements (equivalent to {@code categories: [all]}).
   *
   * <p>Valid element types include: {@code process}, {@code subprocess}, {@code eventSubprocess},
   * {@code serviceTask}, {@code userTask}, {@code sendTask}, {@code receiveTask}, {@code
   * scriptTask}, {@code businessRuleTask}, {@code callActivity}, {@code multiInstanceBody}, {@code
   * exclusiveGateway}, {@code parallelGateway}, {@code inclusiveGateway}, {@code
   * eventBasedGateway}, {@code startEvent}, {@code endEvent}, {@code intermediateCatchEvent},
   * {@code intermediateThrowEvent}, {@code boundaryEvent}.
   *
   * @return the list of element types, or an empty list if not restricted by element type
   */
  List<String> getElementTypes();

  /**
   * Returns the list of broad element type categories this listener targets.
   *
   * <p>Categories are combined with {@link #getElementTypes()} as a union to determine which
   * elements trigger this listener. Supported categories:
   *
   * <ul>
   *   <li>{@code all} — every element type
   *   <li>{@code tasks} — serviceTask, userTask, sendTask, receiveTask, scriptTask,
   *       businessRuleTask
   *   <li>{@code gateways} — exclusiveGateway, parallelGateway, inclusiveGateway, eventBasedGateway
   *   <li>{@code events} — startEvent, endEvent, intermediateCatchEvent, intermediateThrowEvent,
   *       boundaryEvent
   * </ul>
   *
   * @return the list of categories, or an empty list if not restricted by category
   */
  List<String> getCategories();

  /**
   * When this value is set, it indicates that the record is part of the changes necessary to define
   * a global listeners configuration with this key.
   */
  Long getConfigKey();
}
