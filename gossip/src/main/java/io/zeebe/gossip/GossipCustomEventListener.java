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

/** Listen on custom gossip events. */
@FunctionalInterface
public interface GossipCustomEventListener {
  /**
   * Handle the custom event. If the event is handled asynchronously then the data should be copied
   * (another invocation reuse the same data).
   *
   * @param senderId the node id of the (original) sender of the event
   * @param payload the event as payload
   */
  void onEvent(int senderId, DirectBuffer payload);
}
