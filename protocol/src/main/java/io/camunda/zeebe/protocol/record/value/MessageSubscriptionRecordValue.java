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
    extends RecordValueWithVariables, ProcessInstanceRelated {

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
}
