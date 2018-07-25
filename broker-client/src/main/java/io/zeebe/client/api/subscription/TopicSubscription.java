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
package io.zeebe.client.api.subscription;

public interface TopicSubscription {

  /** @return true if this subscription is currently active and events are received for it */
  boolean isOpen();

  /**
   * @return true if this subscription is not open and is not in the process of opening or closing
   */
  boolean isClosed();

  /**
   * Closes this subscription and stops receiving new entries. Blocks until all previously received
   * entries have been handed to a handler.
   */
  void close();
}
