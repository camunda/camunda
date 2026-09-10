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
package io.camunda.runner;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import java.util.Map;
import java.util.function.Function;

/**
 * Scoped fluent helper for attaching listeners to the most-recently-declared flow node. Returned by
 * {@link LiveBpmn#listeners(java.util.function.Consumer)}; offers the same {@code .on(...)} API as
 * {@link LiveBpmn} (overloaded by enum type for both execution and task listeners) but as a
 * brackets-grouped block so a multi-listener attachment reads as one visual unit.
 *
 * <pre>
 * .serviceTask("greet", lambda)
 * .listeners(l -> l
 *     .on(start, lambda)
 *     .on(end,   lambda))
 * .serviceTask("ship", lambda)
 * </pre>
 *
 * <p>This is a thin facade that delegates to {@link LiveBpmn}'s public {@code .on(...)} methods —
 * the runtime effect is identical to writing the calls flat.
 */
public final class Listeners {

  private final LiveBpmn parent;

  Listeners(final LiveBpmn parent) {
    this.parent = parent;
  }

  public Listeners on(
      final ZeebeExecutionListenerEventType eventType,
      final Function<Job, Map<String, Object>> handler) {
    parent.on(eventType, handler);
    return this;
  }

  public Listeners on(final ZeebeExecutionListenerEventType eventType, final JobConsumer handler) {
    parent.on(eventType, handler);
    return this;
  }

  public Listeners on(
      final ZeebeTaskListenerEventType eventType,
      final Function<Job, Map<String, Object>> handler) {
    parent.on(eventType, handler);
    return this;
  }

  public Listeners on(final ZeebeTaskListenerEventType eventType, final JobConsumer handler) {
    parent.on(eventType, handler);
    return this;
  }
}
