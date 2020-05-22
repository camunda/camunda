/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.raft.snapshot;

/** A volatile snapshot which can be persisted. */
public interface PersistableSnapshot {

  /** Aborts the not yet persisted snapshot and removes all related data. */
  void abort();

  /**
   * Persists the snapshot with all his data and returns the representation of this snapshot.
   *
   * @return the persisted snapshot
   */
  PersistedSnapshot persist();
}
