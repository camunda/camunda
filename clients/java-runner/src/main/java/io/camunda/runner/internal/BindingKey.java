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
package io.camunda.runner.internal;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import java.util.Objects;

/**
 * Key into the bindings map. A binding identifies an element + the kind of dispatch (service-task
 * body, execution listener, or task listener) + an event-type discriminator for listener kinds.
 *
 * <p>{@code eventType} is {@code null} for {@link BindingKind#SERVICE_TASK}; for {@link
 * BindingKind#EXECUTION_LISTENER} it is one of {@code "start"} / {@code "end"}; for {@link
 * BindingKind#TASK_LISTENER} it is the name of a {@link ZeebeTaskListenerEventType} (post-{@code
 * resolve()}) such as {@code "assigning"}.
 */
public record BindingKey(String elementId, BindingKind kind, String eventType) {

  public BindingKey {
    Objects.requireNonNull(elementId, "elementId");
    Objects.requireNonNull(kind, "kind");
    if (kind == BindingKind.SERVICE_TASK && eventType != null) {
      throw new IllegalArgumentException(
          "SERVICE_TASK bindings must not carry an eventType (got '" + eventType + "')");
    }
    if (kind != BindingKind.SERVICE_TASK && eventType == null) {
      throw new IllegalArgumentException("listener bindings require a non-null eventType");
    }
  }

  public static BindingKey serviceTask(final String elementId) {
    return new BindingKey(elementId, BindingKind.SERVICE_TASK, null);
  }

  /** {@code startOrEnd} must be either {@code "start"} or {@code "end"}. */
  public static BindingKey executionListener(final String elementId, final String startOrEnd) {
    if (!"start".equals(startOrEnd) && !"end".equals(startOrEnd)) {
      throw new IllegalArgumentException(
          "execution-listener eventType must be 'start' or 'end' (got '" + startOrEnd + "')");
    }
    return new BindingKey(elementId, BindingKind.EXECUTION_LISTENER, startOrEnd);
  }

  public static BindingKey taskListener(
      final String elementId, final ZeebeTaskListenerEventType eventType) {
    Objects.requireNonNull(eventType, "eventType");
    return new BindingKey(elementId, BindingKind.TASK_LISTENER, eventType.resolve().name());
  }

  /**
   * Compact, human-readable handle key surfaced via {@link io.camunda.runner.Run#workersHandled()}:
   *
   * <ul>
   *   <li>{@code SERVICE_TASK} → {@code "<elementId>"}
   *   <li>{@code EXECUTION_LISTENER} → {@code "<elementId>:<start|end>"}
   *   <li>{@code TASK_LISTENER} → {@code "<elementId>:<eventType>"}
   * </ul>
   */
  public String handleKey() {
    return switch (kind) {
      case SERVICE_TASK -> elementId;
      case EXECUTION_LISTENER, TASK_LISTENER -> elementId + ":" + eventType;
    };
  }

  /**
   * Placeholder job-type that the builder writes into the BPMN model and that the runner prefixes
   * before deploy. Encoding is reversible via the binding map, so listener and service-task workers
   * never share a prefix slot.
   *
   * <ul>
   *   <li>{@code SERVICE_TASK} → {@code "<elementId>"}
   *   <li>{@code EXECUTION_LISTENER} → {@code "<elementId>:el:<start|end>"}
   *   <li>{@code TASK_LISTENER} → {@code "<elementId>:tl:<eventType>"}
   * </ul>
   */
  public String placeholderType() {
    return switch (kind) {
      case SERVICE_TASK -> elementId;
      // Hyphen separators: colons are accepted by the broker for service-task job types but
      // observed to cause task-listener jobs not to be delivered to subscribed workers on
      // Camunda 8.9. Hyphens are universally safe.
      case EXECUTION_LISTENER -> elementId + "-el-" + eventType;
      case TASK_LISTENER -> elementId + "-tl-" + eventType;
    };
  }
}
