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

@FunctionalInterface
public interface LogReplicationAppender {

  /**
   * Appends a block of complete, serialized {@link io.zeebe.logstreams.log.LoggedEvent} to a log
   * stream, and updates the commit position to {@code commitPosition}.
   *
   * @param commitPosition the position of the last event in the {@code blockBuffer}
   * @param blockBuffer the buffer containing a block of log entries to be written into storage
   * @return the address at which the block has been written or error status code
   */
  long append(long commitPosition, byte[] blockBuffer);
}
