/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.state.instance;

import io.zeebe.db.DbValue;
import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class StoredRecord implements DbValue {

  private static final int PURPOSE_OFFSET = 0;
  private static final int PURPOSE_LENGTH = BitUtil.SIZE_OF_BYTE;
  private static final int RECORD_OFFSET = PURPOSE_LENGTH;

  private final IndexedRecord record;
  private Purpose purpose;

  public StoredRecord(IndexedRecord record, Purpose purpose) {
    this.record = record;
    this.purpose = purpose;
  }

  /** deserialization constructor */
  public StoredRecord() {
    this.record = new IndexedRecord();
  }

  public IndexedRecord getRecord() {
    return record;
  }

  public Purpose getPurpose() {
    return purpose;
  }

  public long getKey() {
    return record.getKey();
  }

  @Override
  public int getLength() {
    return PURPOSE_LENGTH + record.getLength();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    buffer.putByte(PURPOSE_OFFSET, (byte) purpose.ordinal());
    record.write(buffer, RECORD_OFFSET);
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    final int purposeOrdinal = buffer.getByte(offset + PURPOSE_OFFSET);
    purpose = Purpose.values()[purposeOrdinal];
    record.wrap(buffer, offset + RECORD_OFFSET, length - PURPOSE_LENGTH);
  }

  public enum Purpose {
    // Order is important, as we use the ordinal for persistence
    DEFERRED,
    FAILED
  }
}
