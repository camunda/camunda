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

/**
 * Represents a listener which can be added to the {@link PersistedSnapshotStore} to be notified
 * when a new {@link PersistedSnapshot} is persisted at this store.
 */
public interface PersistedSnapshotListener {

  /**
   * Is called when a new {@link PersistedSnapshot} was persisted.
   *
   * @param newPersistedSnapshot the new persisted snapshots
   */
  void onNewSnapshot(PersistedSnapshot newPersistedSnapshot);
}
