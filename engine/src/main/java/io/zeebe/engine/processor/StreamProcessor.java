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

import io.zeebe.logstreams.log.LoggedEvent;

/** Process events from a log stream. */
public interface StreamProcessor {

  long NO_EVENTS_PROCESSED = -1L;

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

  /**
   * Returns the last successful processed event position from the state. This is used after load
   * the latest snapshot and recover the state, to find the position for reprocessing.
   *
   * @return the last successful processed event position from the state
   */
  default long getPositionToRecoverFrom() {
    return NO_EVENTS_PROCESSED;
  }

  default long getFailedPosition(LoggedEvent currentEvent) {
    return -1;
  }
}
