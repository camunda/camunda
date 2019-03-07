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

import io.zeebe.logstreams.log.LogStreamRecordWriter;

/** Process an event from a log stream. An implementation may be specified for one type of event. */
public interface EventProcessor {
  /**
   * Process the event. Do no execute any side effect, or write an event, or update the internal
   * state.
   */
  default void processEvent() {}
  /**
   * Is called when processing failed due to an unexpected exception. Do clean up work.
   *
   * @param exception the exception which was catched during processing
   */
  default void processingFailed(Exception exception) {}

  /**
   * (Optional) Execute the side effects which are caused by the processed event. A side effect can
   * be e.g., the reply of a client request. Note that the controller may invoke this method
   * multiple times if the execution fails.
   *
   * @return <code>true</code>, if the execution completes successfully or no side effect was
   *     executed.
   */
  default boolean executeSideEffects() {
    return true;
  }

  /**
   * (Optional) Write an event to the log stream that is caused by the processed event. Note that
   * the controller may invoke this method multiple times if the write operation fails.
   *
   * @param writer the log stream writer to write the event to the target log stream.
   * @return
   *     <li>the position of the written event, or
   *     <li>zero, if no event was written, or
   *     <li>a negate value, if the write operation fails.
   */
  default long writeEvent(LogStreamRecordWriter writer) {
    return 0;
  }
}
