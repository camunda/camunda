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

import java.util.Iterator;

public interface CustomEventSupplier {
  int customEventSize();

  /** Return an iterator for custom events which returns at most the given amount of events. */
  Iterator<CustomEvent> customEventViewIterator(int max);

  /**
   * Return an iterator for custom events which returns at most the given amount of events. In
   * contrast to {@link #customEventViewIterator(int)}, the returned events should be removed
   * afterwards (depending on the spread count).
   */
  Iterator<CustomEvent> customEventDrainIterator(int max);
}
