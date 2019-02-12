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
package io.zeebe.logstreams.processor;

import io.zeebe.logstreams.log.LoggedEvent;

/** Process events from a log stream. */
public interface StreamProcessor {

  /**
   * Returns a specific processor to process the event which is read from the log stream, if
   * available.
   *
   * @param event the event to process
   * @return specific processor to process the event, or <code>null</code> if the event can't be
   *     processed
   */
  EventProcessor onEvent(LoggedEvent event);

  /**
   * Callback which is invoked by the controller when it opens. An implementation can provide any
   * setup logic here.
   */
  default void onOpen(StreamProcessorContext context) {
    // do nothing
  }

  /**
   * Callback which is invoked by the controller when the recovery is done. Implementation could
   * contain logic which should not be done on recovery, but afterwards.
   */
  default void onRecovered() {}

  /**
   * Callback which is invoked by the controller when it closes. An implementation can provide any
   * clean up logic here.
   */
  default void onClose() {
    // no nothing
  }
}
