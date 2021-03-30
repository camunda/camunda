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
package io.zeebe.journal;

/** Defines exceptions thrown by the Journal */
public class JournalException extends RuntimeException {

  public JournalException(final String message) {
    super(message);
  }

  public JournalException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public JournalException(final Throwable cause) {
    super(cause);
  }

  /** Exception thrown when storage runs out of disk space. */
  public static class OutOfDiskSpace extends JournalException {
    public OutOfDiskSpace(final String message) {
      super(message);
    }
  }

  /** Exception thrown when an entry has an invalid checksum. */
  public static class InvalidChecksum extends JournalException {
    public InvalidChecksum(final String message) {
      super(message);
    }
  }

  /** Exception thrown when an entry's index is not the expected one. */
  public static class InvalidIndex extends JournalException {
    public InvalidIndex(final String message) {
      super(message);
    }
  }
}
