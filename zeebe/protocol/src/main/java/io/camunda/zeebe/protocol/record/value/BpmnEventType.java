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

import java.util.Arrays;
import java.util.Optional;

public enum BpmnEventType {

  // Default
  UNSPECIFIED(null),

  // Event Type
  CONDITIONAL("conditional"),
  ERROR("error"),
  ESCALATION("escalation"),
  LINK("link"),
  MESSAGE("message"),
  NONE("none"),
  SIGNAL("signal"),
  TERMINATE("terminate"),
  TIMER("timer"),
  COMPENSATION("compensation");

  private final String eventTypeName;

  BpmnEventType(final String eventTypeName) {
    this.eventTypeName = eventTypeName;
  }

  public Optional<String> getEventTypeName() {
    return Optional.ofNullable(eventTypeName);
  }

  public static BpmnEventType bpmnEventTypeFor(final String eventTypeName) {
    return Arrays.stream(values())
        .filter(
            bpmnEventType ->
                bpmnEventType.eventTypeName != null
                    && bpmnEventType.eventTypeName.equals(eventTypeName))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Unsupported BPMN event of type " + eventTypeName));
  }
}
