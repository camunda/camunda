/*
 * Copyright 2017-present Open Networking Foundation
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
 * limitations under the License.
 */
package io.camunda.zeebe.journal.file;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.nio.file.Path;

/**
 * Segment file utility.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public final class SegmentFile {

  private static int deletedFileIndex = 0;
  private static final char PART_SEPARATOR = '-';
  private static final char EXTENSION_SEPARATOR = '.';
  private static final String EXTENSION = "log";
  private static final String DELETE_EXTENSION = "deleted";
  private static final char DELETE_EXTENSION_SEPARATOR = '_';
  private final File file;
  private Path fileMarkedForDeletion;

  /**
   * @throws IllegalArgumentException if {@code file} is not a valid segment file
   */
  SegmentFile(final File file) {
    this.file = file;
  }

  /**
   * Returns a boolean value indicating whether the given file appears to be a parsable segment
   * file.
   *
   * @throws NullPointerException if {@code file} is null
   */
  public static boolean isSegmentFile(final String name, final File file) {
    return isSegmentFile(name, file.getName());
  }

  /**
   * Returns a boolean value indicating whether the given file appears to be a parsable segment
   * file.
   *
   * @param journalName the name of the journal
   * @param fileName the name of the file to check
   * @throws NullPointerException if {@code file} is null
   */
  public static boolean isSegmentFile(final String journalName, final String fileName) {
    checkNotNull(journalName, "journalName cannot be null");
    checkNotNull(fileName, "fileName cannot be null");

    if (getSegmentIdFromPath(fileName) == -1) {
      return false;
    }

    return fileName.startsWith(journalName);
  }

  /**
   * Returns the segment's file id or -1 if the log segment name was not correctly formatted. Please
   * note that this is not the same as the actual id of the segment that can be found in the {@link
   * SegmentDescriptor}. Due to async preparation of the next segment before use, the file id can be
   * larger than the actual id.
   */
  static int getSegmentIdFromPath(final String name) {
    checkNotNull(name, "name cannot be null");

    final int partSeparator = name.lastIndexOf(PART_SEPARATOR);
    final int extensionSeparator = name.lastIndexOf(EXTENSION_SEPARATOR);

    if (extensionSeparator == -1
        || partSeparator == -1
        || extensionSeparator < partSeparator
        || !name.endsWith(EXTENSION)) {
      return -1;
    }

    try {
      return Integer.parseInt(name.substring(partSeparator + 1, extensionSeparator));
    } catch (final NumberFormatException e) {
      return -1;
    }
  }

  /** Creates a segment file for the given directory, log name, segment ID, and segment version. */
  static File createSegmentFile(final String name, final File directory, final long id) {
    return new File(
        directory,
        String.format(
            "%s%s%d%s%s",
            checkNotNull(name, "name cannot be null"),
            PART_SEPARATOR,
            id,
            EXTENSION_SEPARATOR,
            EXTENSION));
  }

  /**
   * Returns the segment file.
   *
   * @return The segment file.
   */
  public File file() {
    return file;
  }

  public String name() {
    return file.getName();
  }

  public Path getFileMarkedForDeletion() {
    if (fileMarkedForDeletion == null) {
      final String renamedFileName =
          String.format(
              "%s%c%d-%s",
              file.getName(), DELETE_EXTENSION_SEPARATOR, deletedFileIndex++, DELETE_EXTENSION);
      fileMarkedForDeletion = Path.of(file.getParent(), renamedFileName);
    }
    return fileMarkedForDeletion;
  }

  public static boolean isDeletedSegmentFile(final String journalName, final String fileName) {
    checkNotNull(journalName, "journalName cannot be null");
    checkNotNull(fileName, "fileName cannot be null");

    if (!fileName.endsWith(DELETE_EXTENSION)) {
      return false;
    }
    final var deleteExtensionIndex = fileName.lastIndexOf(DELETE_EXTENSION_SEPARATOR);
    return isSegmentFile(journalName, fileName.substring(0, deleteExtensionIndex));
  }
}
