/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor;

import io.zeebe.logstreams.log.LogStreamRecordWriter;

/** Process an event from a log stream. An implementation may be specified for one type of event. */
public interface EventProcessor {
  /**
   * Process the event. Do no execute any side effect, or write an event, or update the internal
   * state.
   */
  default void processEvent() {}
  /**
   * Is called when during the execution of {@link #processEvent()} or {@link
   * #writeEvent(LogStreamRecordWriter)} an unexpected exception was thrown.
   *
   * <p>The implementation should do clean up work.
   *
   * @param throwable the throwable which was catched during execution
   */
  default void onError(Throwable throwable) {}

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
