/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.primitive.event;

import io.atomix.primitive.event.impl.DefaultEventType;
import io.atomix.utils.Identifier;

/** Raft event identifier. */
public interface EventType extends Identifier<String> {

  /**
   * Creates a new Raft event identifier.
   *
   * @param name the event name
   * @return the event identifier
   */
  static EventType from(final String name) {
    return new DefaultEventType(name);
  }

  /**
   * Simplifies the given event type.
   *
   * @param eventType the event type to simplify
   * @return the simplified event type
   */
  static EventType canonical(final EventType eventType) {
    return new DefaultEventType(eventType.id());
  }

  /**
   * Returns an identical event type in canonical form.
   *
   * @return an identical event type in canonical form
   */
  default EventType canonicalize() {
    return canonical(this);
  }
}
