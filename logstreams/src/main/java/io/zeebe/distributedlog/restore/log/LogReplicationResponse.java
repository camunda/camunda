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
package io.zeebe.distributedlog.restore.log;

public interface LogReplicationResponse {

  /** @return position of the last event to be serialized */
  long getToPosition();

  /**
   * If the response {@link #getToPosition()} is lower than what was requested (e.g. send buffer was
   * filled early), then the server can indicate here whether it has more events available in the
   * range that was requested.
   *
   * @return if true, indicates the server has more events available, false otherwise
   */
  boolean hasMoreAvailable();

  /**
   * @return a block of complete, serialized {@link io.zeebe.logstreams.log.LoggedEvent}; can be
   *     null or empty
   */
  byte[] getSerializedEvents();

  /** @return true if the response can be processed, false otherwise */
  default boolean isValid() {
    return getToPosition() > 0
        && (getSerializedEvents() != null && getSerializedEvents().length > 0);
  }
}
