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
package io.zeebe.gossip;

import org.agrona.DirectBuffer;

/** Publish custom events via Gossip's infection-style protocol. */
public interface GossipEventPublisher {
  /**
   * Publish a custom event.
   *
   * @param type the type of the event
   * @param payload the event as payload
   * @param offset the payload buffer's offset
   * @param length the payload buffer's length
   */
  void publishEvent(DirectBuffer type, DirectBuffer payload, int offset, int length);

  default void publishEvent(DirectBuffer type, DirectBuffer payload) {
    publishEvent(type, payload, 0, payload.capacity());
  }
}
