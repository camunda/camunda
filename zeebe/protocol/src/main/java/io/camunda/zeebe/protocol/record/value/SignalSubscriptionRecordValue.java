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
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import org.immutables.value.Value;

/**
 * Represents signal event subscription commands and events
 *
 * <p>See {@link SignalSubscriptionIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableSignalSubscriptionRecordValue.Builder.class)
public interface SignalSubscriptionRecordValue extends RecordValue, TenantOwned, WaitStateRelated {

  /**
   * @return the process key tied to the subscription
   */
  long getProcessDefinitionKey();

  /**
   * @return the BPMN process id tied to the subscription
   */
  String getBpmnProcessId();

  /**
   * @return the id of the catch event tied to the subscription
   */
  String getCatchEventId();

  /**
   * @return the key of the catch event instance key tied to the subscription
   */
  long getCatchEventInstanceKey();

  /**
   * @return the name of the signal
   */
  String getSignalName();

  /**
   * @return the key of the process instance, or {@code -1L} if not set (e.g. start-event
   *     subscriptions that are not tied to a running instance)
   * @since 8.10
   */
  @Override
  long getProcessInstanceKey();

  /**
   * @return the key of the root process instance, or {@code -1L} if not set
   * @since 8.10
   */
  @Override
  long getRootProcessInstanceKey();

  /**
   * Bridges {@link WaitStateRelated#getElementId()} to {@link #getCatchEventId()}.
   *
   * <p>Implementations should annotate this override with {@code @JsonIgnore} to avoid duplicating
   * {@code catchEventId} in serialised output.
   *
   * @since 8.10
   */
  @Override
  default String getElementId() {
    return getCatchEventId();
  }

  /**
   * Bridges {@link WaitStateRelated#getElementInstanceKey()} to {@link
   * #getCatchEventInstanceKey()}.
   *
   * <p>Implementations should annotate this override with {@code @JsonIgnore} to avoid duplicating
   * {@code catchEventInstanceKey} in serialised output.
   *
   * @since 8.10
   */
  @Override
  default long getElementInstanceKey() {
    return getCatchEventInstanceKey();
  }
}
