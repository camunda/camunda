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
package io.zeebe.logstreams.state;

import java.util.function.Predicate;

public interface SnapshotController {
  /**
   * Takes a snapshot based on the given metadata: - last successful processed event - last written
   * event (regardless of whether or not it is committed) - term of the last written event
   *
   * @param metadata current state metadata
   */
  void takeSnapshot(SnapshotMetadata metadata) throws Exception;

  /**
   * Recovers the state from the latest snapshot and returns the corresponding metadata. The
   * metadata is used by the StreamProcessController to know where to seek to in the log stream.
   * Defaults to: (-1, -1, term)
   *
   * @param commitPosition current log stream commit position
   * @return recovered state metadata
   */
  SnapshotMetadata recover(long commitPosition, int term, Predicate<SnapshotMetadata> filter)
      throws Exception;

  /**
   * Purges old and invalid snapshots. Should be done once we are sure we can discard other
   * snapshots, e.g. during recovery.
   *
   * @param metadata last taken/recovered snapshot metadata
   */
  void purgeAllExcept(SnapshotMetadata metadata) throws Exception;
}
