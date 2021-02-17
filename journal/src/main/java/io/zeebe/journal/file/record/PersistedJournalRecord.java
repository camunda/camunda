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

import com.google.common.base.Objects;
import io.zeebe.journal.JournalRecord;
import org.agrona.DirectBuffer;

/** Journal Record */
public final class PersistedJournalRecord implements JournalRecord {

  private final DirectBuffer data;
  private final long index;
  private final long asqn;
  private final int checksum;

  public PersistedJournalRecord(
      final long index, final long asqn, final int checksum, final DirectBuffer data) {
    this.index = index;
    this.asqn = asqn;
    this.checksum = checksum;
    this.data = data;
  }

  @Override
  public long index() {
    return index;
  }

  @Override
  public long asqn() {
    return asqn;
  }

  @Override
  public int checksum() {
    return checksum;
  }

  @Override
  public DirectBuffer data() {
    return data;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    } else if (o == null || !o.getClass().equals(this.getClass())) {
      return false;
    }

    final PersistedJournalRecord other = (PersistedJournalRecord) o;
    return this.asqn == other.asqn
        && this.checksum == other.checksum
        && this.index == other.index
        && this.data.equals(other.data);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(asqn, checksum, index, data);
  }
}
