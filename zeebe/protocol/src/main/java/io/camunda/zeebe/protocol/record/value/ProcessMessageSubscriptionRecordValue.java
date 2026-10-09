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
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import org.immutables.value.Value;

/**
 * Represents a process message subscription command or event.
 *
 * <p>See {@link ProcessMessageSubscriptionIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableProcessMessageSubscriptionRecordValue.Builder.class)
public interface ProcessMessageSubscriptionRecordValue
    extends RecordValueWithVariables, ProcessInstanceRelated, TenantOwned {
  /**
   * @return the process instance key
   */
  @Override
  long getProcessInstanceKey();

  /**
   * @return the element instance key
   */
  long getElementInstanceKey();

  /**
   * @return the process definition key
   */
  long getProcessDefinitionKey();

  /**
   * @return the BPMN process id
   */
  String getBpmnProcessId();

  /**
   * @return the key of the correlated message
   */
  long getMessageKey();

  /**
   * @return the message name
   */
  String getMessageName();

  /**
   * @return the correlation key
   */
  String getCorrelationKey();

  /**
   * @return the id of the element tied to the subscription.
   */
  String getElementId();

  /**
   * @return {@code true} if the event tied to the subscription is interrupting. Otherwise, it
   *     returns {@code false} if the event is non-interrupting.
   */
  boolean isInterrupting();

  /**
   * Returns the key of the root process instance in the hierarchy. For top-level process instances,
   * this is equal to {@link #getProcessInstanceKey()}. For child process instances (created via
   * call activities), this is the key of the topmost parent process instance.
   *
   * <p>Important: This value is only set for process instance records created after version 8.9.0
   * and part of hierarchies created after that version. For older process instances, the method
   * will return -1.
   *
   * @return the key of the root process instance, or {@code -1} if not set
   */
  long getRootProcessInstanceKey();

  /**
   * The business id captured from the subscribing process instance at the time the subscription was
   * opened. It is shipped to the message partition together with the OPEN command so the message
   * partition can apply business-id-based filtering locally at correlation time.
   *
   * @return the business id, or an empty string if not set
   * @since 8.10
   */
  String getBusinessId();

  /**
   * @return the BPMN element type of the catch element that opened this subscription, or {@link
   *     BpmnElementType#UNSPECIFIED} for subscriptions created before this field was introduced.
   * @since 8.10
   */
  BpmnElementType getElementType();

  /**
   * Returns the generation key of the message-side subscription, assigned when the subscription is
   * created on the message partition and echoed back to the process instance partition in the open
   * acknowledgement. Used to guard delete/correlate commands against stale races.
   *
   * <p>This is a per-generation key, not a stable identity across suspend/resume: {@code
   * reopenFromManifest} resets it to {@code -1} when a suspended subscription is reopened, until
   * the replacement's open acknowledgement assigns the new key. {@code -1} therefore means "unset"
   * — a subscription created before this field was introduced, or one currently being reopened —
   * and must not be cached or interpreted as legacy-only across generations.
   *
   * @return the subscription key, or {@code -1} if unset
   * @since 8.10
   */
  long getSubscriptionKey();

  /**
   * @return {@code true} if this subscription was closed by the suspend flow, meaning the delete
   *     acknowledgement must restore this row as a resume manifest instead of deleting it; {@code
   *     false} for a normal close (unsubscribe, cancellation, migration) or a legacy record
   * @since 8.11
   */
  boolean isClosedForSuspend();
}
