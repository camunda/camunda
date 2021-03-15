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
package io.zeebe.journal.file.record;

import java.util.Objects;

public final class RecordMetadata {

  private final long checksum;
  private final int length;

  public RecordMetadata(final long checksum, final int recordLength) {
    this.checksum = checksum;
    length = recordLength;
  }

  public long checksum() {
    return checksum;
  }

  public int length() {
    return length;
  }

  @Override
  public int hashCode() {
    return Objects.hash(checksum, length);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RecordMetadata that = (RecordMetadata) o;
    return checksum == that.checksum && length == that.length;
  }

  @Override
  public String toString() {
    return "RecordMetadata{" + "checksum=" + checksum + ", length=" + length + '}';
  }
}
