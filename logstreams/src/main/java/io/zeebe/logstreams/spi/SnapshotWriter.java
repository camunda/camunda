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

import java.io.OutputStream;

/** Writer to create a snapshot. */
public interface SnapshotWriter {

  /**
   * Returns the output stream of the snapshot which should be used to write the snapshot data.
   * Finish the write operation with {@link #commit()} or {@link #abort()}.
   *
   * @return the snapshot output stream
   */
  OutputStream getOutputStream();

  /**
   * Completes the snapshot by closing the output stream and writing the checksum.
   *
   * @throws Exception if fails to write the checksum
   */
  void commit() throws Exception;

  /** Refuse the snapshot by closing the output stream and deleting the snapshot data. */
  void abort();

  /**
   * Writes the given snapshot to the output stream.
   *
   * @param snapshotSupport the snapshot object
   * @return
   * @throws Exception if fails to write the snapshot
   */
  default long writeSnapshot(SnapshotSupport snapshotSupport) throws Exception {
    return snapshotSupport.writeSnapshot(getOutputStream());
  }

  /**
   * Completes the snapshot by closing the output stream and writing its checksum, iff the checksum
   * is equal to the given checksum.
   *
   * <p>It will call abort on exception, or if the checksums do not match.
   *
   * @param checksum checksum to check against
   * @throws Exception
   */
  void validateAndCommit(byte[] checksum) throws Exception;
}
