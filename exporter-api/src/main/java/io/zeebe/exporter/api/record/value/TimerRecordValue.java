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
package io.zeebe.exporter.api.record.value;

import io.zeebe.exporter.api.record.RecordValue;
import io.zeebe.protocol.intent.TimerIntent;

/**
 * Represents a timer event or command.
 *
 * <p>See {@link TimerIntent} for intents.
 */
public interface TimerRecordValue extends RecordValue {

  /** @return the key of the workflow in which this timer was created */
  long getWorkflowKey();

  /** @return the key of the related element instance. */
  long getElementInstanceKey();

  /** @return the key of the related workflow instance */
  long getWorkflowInstanceKey();

  /** @return the due date of the timer as Unix timestamp in millis. */
  long getDueDate();

  /**
   * The handlerFlowNodeID property represent the ID, from the BPMN XML of the workflow, of the flow
   * node which will handle the timer trigger's event. In normal flow, this is usually the same as
   * the related activity's ID, but when the timer was created due to a boundary event, it will be
   * that event's ID.
   *
   * @return the ID of the flow node which will handle the trigger element
   */
  String getHandlerFlowNodeId();

  /** @return the number of times this timer should trigger */
  int getRepetitions();
}
