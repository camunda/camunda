/*
 * Copyright 2015-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package io.atomix.raft.storage.snapshot.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import io.atomix.utils.AtomixIOException;
import java.io.File;
import java.io.IOException;

/** Represents a snapshot file on disk. */
public final class DefaultSnapshotFile {

  private static final char PART_SEPARATOR = '-';
  private static final char EXTENSION_SEPARATOR = '.';
  private static final String EXTENSION = "snapshot";
  private final File file;
  private File temporaryFile;

  /**
   * Creates a new DefaultSnapshotFile with references to a permanent snapshot file (for reading)
   * and a temporary snapshot file (for writing). The temporary file can be null if the snapshot is
   * already completed and should not be written to.
   *
   * @param file the snapshot file which is used for reading
   * @param temporaryFile the snapshot file which is used for writing
   */
  public DefaultSnapshotFile(final File file, final File temporaryFile) {
    this.file = file;
    this.temporaryFile = temporaryFile;
  }

  /**
   * Returns a boolean value indicating whether the given file appears to be a parsable snapshot
   * file.
   *
   * @throws NullPointerException if {@code file} is null
   */
  public static boolean isSnapshotFile(final File file) {
    checkNotNull(file, "file cannot be null");
    final String fileName = file.getName();

    // The file name should contain an extension separator.
    if (fileName.lastIndexOf(EXTENSION_SEPARATOR) == -1) {
      return false;
    }

    // The file name should end with the snapshot extension.
    if (!fileName.endsWith("." + EXTENSION)) {
      return false;
    }

    // Parse the file name parts.
    final String[] parts =
        fileName
            .substring(0, fileName.lastIndexOf(EXTENSION_SEPARATOR))
            .split(String.valueOf(PART_SEPARATOR));

    // The total number of file name parts should be at least 2.
    if (parts.length < 2) {
      return false;
    }

    // The second part of the file name should be numeric.
    // Subtract from the number of parts to ensure PART_SEPARATOR can be used in snapshot names.
    return isNumeric(parts[parts.length - 1]);

    // Otherwise, assume this is a snapshot file.
  }

  /**
   * Returns a boolean indicating whether the given string value is numeric.
   *
   * @param value The value to check.
   * @return Indicates whether the given string value is numeric.
   */
  private static boolean isNumeric(final String value) {
    for (final char c : value.toCharArray()) {
      if (!Character.isDigit(c)) {
        return false;
      }
    }
    return true;
  }

  /** Creates a snapshot file for the given directory, log name, and snapshot index. */
  public static File createSnapshotFile(
      final File directory, final String serverName, final long index) {
    return new File(directory, createSnapshotFileName(serverName, index));
  }

  /** Creates a snapshot file name from the given parameters. */
  public static String createSnapshotFileName(final String serverName, final long index) {
    return String.format("%s-%d.%s", serverName, index, EXTENSION);
  }

  /** Creates a temporary file for writing snapshots. */
  public static File createTemporaryFile(final File base) {
    try {
      final File file = File.createTempFile(base.getName(), null);
      file.deleteOnExit();
      return file;
    } catch (final IOException e) {
      throw new AtomixIOException(e);
    }
  }

  /**
   * Returns the snapshot lock file name.
   *
   * @return the snapshot lock file name
   */
  public File temporaryFile() {
    return temporaryFile;
  }

  public void clearTemporaryFile() {
    temporaryFile = null;
  }

  /**
   * Returns the snapshot file.
   *
   * @return The snapshot file.
   */
  public File file() {
    return file;
  }

  /**
   * Returns the snapshot name.
   *
   * @return the snapshot name
   */
  public String name() {
    return parseName(file.getName());
  }

  @VisibleForTesting
  static String parseName(final String fileName) {
    return fileName.substring(
        0, fileName.lastIndexOf(PART_SEPARATOR, fileName.lastIndexOf(PART_SEPARATOR) - 1));
  }
}
