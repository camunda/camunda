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
package io.zeebe.protocol.record.value;

import io.zeebe.protocol.record.RecordValueWithVariables;

/**
 * Represents a signal that an event was triggered in a process instance, within a given scope, and
 * targeting a particular element identified by its ID.
 *
 * <p>The scope here can refer to a process definition (for start events), or to a specific element
 * instance, e.g. an activity. Note that the scope may be (and usually is) a different element
 * instance than the one identified by the {@link #getTargetElementId()}.
 *
 * <p>The target element ID refer to the element which should receive the payload of the event, e.g.
 * the boundary event, or the start event. For example, if the scope is a sub process, then the
 * target element ID could refer to one of its boundary events.
 *
 * <p>NOTE: this record is optional, and events can be triggered without this record being emitted.
 * It's meant to be used mostly when the scope and/or target element of the event is far removed
 * from the emitter (e.g. error throw end event, escalation), and there is no special purpose entity
 * associated with the emitter (e.g. timer, message).
 */
public interface ProcessEventRecordValue extends RecordValueWithVariables, ProcessInstanceRelated {

  /** @return the key identifying the event's scope */
  long getScopeKey();

  /** @return the ID of the element which should react to the event */
  String getTargetElementId();

  /** @return the key of the deployed process this instance belongs to. */
  long getProcessDefinitionKey();
}
