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
package io.zeebe.gossip.protocol;

@FunctionalInterface
public interface CustomEventConsumer {
  /**
   * Consume the given event.
   *
   * @return <code>true</code> when the event is new (i.e. the internal state has changed).
   */
  boolean consumeCustomEvent(CustomEvent event);

  /**
   * Return a composed consumer that invoke this consumer followed by the given consumer. The next
   * consumer if only invoked when the previous consumer returns <code>true</code>.
   */
  default CustomEventConsumer andThen(CustomEventConsumer after) {
    return event -> {
      boolean changed = consumeCustomEvent(event);
      if (changed) {
        changed = after.consumeCustomEvent(event);
      }
      return changed;
    };
  }
}
