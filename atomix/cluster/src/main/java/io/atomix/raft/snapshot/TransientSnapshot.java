/*
 * Copyright © 2020  camunda services GmbH (info@camunda.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package io.atomix.raft.snapshot;

import java.nio.file.Path;
import java.util.function.Predicate;

/** A transient snapshot which can be persisted after taking a snapshot. */
public interface TransientSnapshot extends PersistableSnapshot {

  /**
   * Takes a snapshot on the given path. This can be persisted later via calling {@link
   * PersistableSnapshot#persist()}. Based on the implementation this could mean that this is writen
   * before on a temporary folder and then moved to the valid snapshot directory.
   *
   * @param takeSnapshot the predicate which should take the snapshot and should return true on
   *     success
   * @return true on success, false otherwise
   */
  boolean take(Predicate<Path> takeSnapshot);
}
