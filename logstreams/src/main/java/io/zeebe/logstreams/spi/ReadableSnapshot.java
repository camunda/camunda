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
package io.zeebe.logstreams.spi;

import io.zeebe.logstreams.snapshot.InvalidSnapshotException;
import java.io.InputStream;

/** Represents a snapshot of the log. */
public interface ReadableSnapshot extends SnapshotMetadata {
  /**
   * Input stream to read the snapshot data.
   *
   * @return the snapshot data as input stream
   */
  InputStream getData();

  /**
   * Consumers of this API must call this method after having read the input stream. The method
   * validates that the bytes read are valid and closes any underlying resources.
   *
   * @throws InvalidSnapshotException if not valid
   */
  void validateAndClose() throws InvalidSnapshotException;

  /** Deletes the snapshot and related data. */
  void delete();

  /**
   * Reads the snapshot data and recover the given snapshot object. At the end, it validates that
   * the bytes read are valid and closes any underlying resources.
   *
   * @param snapshotSupport the snapshot object
   * @throws Exception if fails to recover the snapshot object
   * @throws InvalidSnapshotException if the snapshot is not valid
   */
  default void recoverFromSnapshot(SnapshotSupport snapshotSupport) throws Exception {
    snapshotSupport.reset();
    snapshotSupport.recoverFromSnapshot(getData());
    validateAndClose();
  }
}
