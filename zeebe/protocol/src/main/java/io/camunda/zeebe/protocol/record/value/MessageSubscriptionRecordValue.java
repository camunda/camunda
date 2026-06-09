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
import io.camunda.zeebe.protocol.record.RecordValueWithVariables;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import org.immutables.value.Value;

/**
 * Represents a message correlation subscription event or command.
 *
 * <p>See {@link MessageSubscriptionIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableMessageSubscriptionRecordValue.Builder.class)
public interface MessageSubscriptionRecordValue
    extends RecordValueWithVariables, ProcessInstanceRelated, TenantOwned, WaitStateRelated {

  /**
   * @return the process instance key tied to the subscription
   */
  @Override
  long getProcessInstanceKey();

  /**
   * @return the element instance key tied to the subscription
   */
  long getElementInstanceKey();

  /**
   * @return the process definition key tied to the subscription
   */
  long getProcessDefinitionKey();

  /**
   * @return the BPMN process id tied to the subscription
   */
  String getBpmnProcessId();

  /**
   * @return the name of the message
   */
  String getMessageName();

  /**
   * @return the correlation key
   */
  String getCorrelationKey();

  /**
   * @return the key of the correlated message
   */
  long getMessageKey();

  /**
   * @return {@code true} if the event tied to the subscription is interrupting. Otherwise, it
   *     returns {@code false} if the event is non-interrupting.
   */
  boolean isInterrupting();

  /**
   * The business id captured from the subscribing process instance at the time the subscription was
   * opened. Used as an additional, post-routing local filter on the message partition: when a
   * published message carries a business id, only subscriptions whose stored business id matches
   * will correlate. A subscription opened from a process instance without a business id stores an
   * empty string.
   *
   * <p>This value is captured at OPEN time on the process instance partition and shipped to the
   * message partition with the subscription. It is not retroactively updated if the process
   * instance's business id is assigned later.
   *
   * @return the business id, or an empty string if not set
   * @since 8.10
   */
  String getBusinessId();

  /**
   * @return the id of the BPMN element tied to the subscription, or an empty string if not set
   */
  @Override
  String getElementId();

  /**
   * Returns the key of the root process instance in the hierarchy. For top-level process instances,
   * this is equal to {@link #getProcessInstanceKey()}. For child process instances (created via
   * call activities), this is the key of the topmost parent process instance.
   *
   * <p>Important: This value is only set for process instances (and their subscriptions) created
   * after version 8.9.0. For older process instances, the method will return -1.
   *
   * @return the key of the root process instance, or {@code -1} if not set
   */
  @Override
  long getRootProcessInstanceKey();

  /**
   * @return the BPMN element type of the element tied to the subscription
   */
  BpmnElementType getElementType();
}
