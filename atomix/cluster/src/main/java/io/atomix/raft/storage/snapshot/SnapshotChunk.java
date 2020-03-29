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
package io.atomix.raft.storage.snapshot;

import java.nio.ByteBuffer;

/** Represents a serializable snapshot chunk */
public interface SnapshotChunk {

  /**
   * Returns the snapshot chunk identifier; the identifier is implementation dependent and its
   * semantics are dictated by its producer/consumer
   *
   * @return the snapshot chunk ID
   */
  ByteBuffer id();

  /**
   * Returns the snapshot chunk data.
   *
   * @return the snapshot chunk data
   */
  ByteBuffer data();
}
