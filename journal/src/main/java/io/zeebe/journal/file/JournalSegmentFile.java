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
package io.zeebe.journal.file;

import static com.google.common.base.Preconditions.checkNotNull;

import io.zeebe.journal.StorageException;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/**
 * Segment file utility.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
final class JournalSegmentFile {
  private static final char PART_SEPARATOR = '-';
  private static final char EXTENSION_SEPARATOR = '.';
  private static final String EXTENSION = "log";
  private final File file;

  /** @throws IllegalArgumentException if {@code file} is not a valid segment file */
  JournalSegmentFile(final File file) {
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

    final int partSeparator = fileName.lastIndexOf(PART_SEPARATOR);
    final int extensionSeparator = fileName.lastIndexOf(EXTENSION_SEPARATOR);

    if (extensionSeparator == -1
        || partSeparator == -1
        || extensionSeparator < partSeparator
        || !fileName.endsWith(EXTENSION)) {
      return false;
    }

    for (int i = partSeparator + 1; i < extensionSeparator; i++) {
      if (!Character.isDigit(fileName.charAt(i))) {
        return false;
      }
    }

    return fileName.startsWith(journalName);
  }

  /** Creates a segment file for the given directory, log name, segment ID, and segment version. */
  static File createSegmentFile(final String name, final File directory, final long id) {
    return new File(
        directory, String.format("%s-%d.log", checkNotNull(name, "name cannot be null"), id));
  }

  /**
   * Returns the segment file.
   *
   * @return The segment file.
   */
  public File file() {
    return file;
  }

  FileChannel openChannel(final StandardOpenOption... options) {
    try {
      return FileChannel.open(file.toPath(), options);
    } catch (final IOException e) {
      throw new StorageException(e);
    }
  }

  public String name() {
    return file.getName();
  }
}
