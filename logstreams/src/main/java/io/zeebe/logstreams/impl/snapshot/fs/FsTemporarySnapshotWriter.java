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
package io.zeebe.logstreams.impl.snapshot.fs;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Extends FsSnapshotWriter, delegating the write of the checksum + data to it, and moving the data
 * file to its correct path at the end.
 *
 * <p>In this respect, dataFile => temporaryFile, and snapshotFile is the correct final destination.
 */
public class FsTemporarySnapshotWriter extends FsSnapshotWriter {
  private File snapshotFile;

  @SuppressWarnings("WeakerAccess")
  public FsTemporarySnapshotWriter(
      final FsSnapshotStorageConfiguration config,
      final File temporaryFile,
      final File checksumFile,
      final File snapshotFile,
      final FsReadableSnapshot lastSnapshot) {
    super(config, temporaryFile, checksumFile, lastSnapshot);
    this.snapshotFile = snapshotFile;
  }

  @Override
  protected void writeToDisk(byte[] checksum) throws Exception {
    try {
      super.writeToDisk(checksum);

      // TODO: evaluate if REPLACE_EXISTING is safe here
      Files.move(
          dataFile.toPath(),
          snapshotFile.toPath(),
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);

      //noinspection ResultOfMethodCallIgnored
      dataFile.delete();
    } catch (final Exception ex) {
      abort();
      throw ex;
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  @Override
  public void abort() {
    super.abort();
    snapshotFile.delete();
  }

  @Override
  protected String getDataFileName() {
    return snapshotFile.getName();
  }

  public File getSnapshotFile() {
    return snapshotFile;
  }
}
