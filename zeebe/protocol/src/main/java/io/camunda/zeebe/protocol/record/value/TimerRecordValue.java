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
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import org.immutables.value.Value;

/**
 * Represents a timer event or command.
 *
 * <p>See {@link TimerIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableTimerRecordValue.Builder.class)
public interface TimerRecordValue
    extends RecordValue, ProcessInstanceRelated, TenantOwned, WaitStateRelated {

  /**
   * @return the BPMN element type of the timer element (e.g. INTERMEDIATE_CATCH_EVENT,
   *     BOUNDARY_EVENT, START_EVENT), or UNSPECIFIED if unknown.
   */
  BpmnElementType getElementType();

  /**
   * @return the key of the process in which this timer was created
   */
  long getProcessDefinitionKey();

  /**
   * @return the key of the related element instance.
   */
  long getElementInstanceKey();

  /**
   * @return the key of the related process instance
   */
  @Override
  long getProcessInstanceKey();

  /**
   * @return the due date of the timer as Unix timestamp in millis.
   */
  long getDueDate();

  /**
   * The handlerFlowNodeID property represent the ID, from the BPMN XML of the process, of the flow
   * node which will handle the timer trigger's event. In normal flow, this is usually the same as
   * the related activity's ID, but when the timer was created due to a boundary event, it will be
   * that event's ID.
   *
   * @return the ID of the flow node which will handle the trigger element
   */
  String getTargetElementId();

  /**
   * @return the number of times this timer should trigger
   */
  int getRepetitions();

  /**
   * @return the root process instance key, or -1L if this is a start-event subscription
   */
  @Override
  long getRootProcessInstanceKey();

  /**
   * @return the BPMN process id of the process that owns this timer
   */
  @Override
  String getBpmnProcessId();

  /**
   * Delegates to {@link #getTargetElementId()}: the target element id IS the BPMN element id of the
   * timer catch/boundary event.
   */
  @Override
  default String getElementId() {
    return getTargetElementId();
  }
}
