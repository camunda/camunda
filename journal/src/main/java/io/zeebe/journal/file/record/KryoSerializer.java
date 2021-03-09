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

import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;
import java.nio.BufferOverflowException;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * The serializer that writes and reads a journal record according to the SBE schema defined. A
 * journal record consists of two parts - a metadata and an indexed record.
 *
 * <p>Metadata consists of the checksum and the length of the record. The record consists of index,
 * asqn and the data.
 */
public final class KryoSerializer implements JournalRecordSerializer {
  private static final Namespace NAMESPACE =
      new Namespace.Builder()
          .register(Namespaces.BASIC)
          .nextId(Namespaces.BEGIN_USER_CUSTOM_ID)
          .register(RecordData.class)
          .register(UnsafeBuffer.class)
          .name("Journal")
          .build();

  @Override
  public int writeData(
      final RecordData record, final MutableDirectBuffer buffer, final int offset) {
    final var serializedBytes = NAMESPACE.serialize(record);
    if (offset + serializedBytes.length > buffer.capacity()) {
      throw new BufferOverflowException();
    }
    buffer.putBytes(offset, serializedBytes);
    return serializedBytes.length;
  }

  @Override
  public int writeMetadata(
      final RecordMetadata metadata, final MutableDirectBuffer buffer, final int offset) {
    buffer.putLong(offset, metadata.checksum());
    buffer.putInt(offset + Long.BYTES, metadata.length());
    return Long.BYTES + Integer.BYTES;
  }

  @Override
  public int getMetadataLength() {
    return Long.BYTES + Integer.BYTES;
  }

  @Override
  public boolean hasMetadata(final DirectBuffer buffer, final int offset) {
    return buffer.getLong(offset) > 0 && buffer.getInt(offset + Long.BYTES) > 0;
  }

  @Override
  public RecordMetadata readMetadata(final DirectBuffer buffer, final int offset) {
    final long checksum = buffer.getLong(offset);
    final int length = buffer.getInt(offset + Long.BYTES);
    if (!(checksum > 0 && length > 0)) {
      throw new InvalidRecordException("No valid metadata exists. Cannot read buffer.");
    }
    return new RecordMetadata(checksum, length);
  }

  @Override
  public RecordData readData(final DirectBuffer buffer, final int offset, final int length) {
    final byte[] bufferToRead = new byte[length];
    buffer.getBytes(offset, bufferToRead);
    return NAMESPACE.deserialize(bufferToRead);
  }

  @Override
  public int getMetadataLength(final DirectBuffer buffer, final int offset) {
    return getMetadataLength();
  }
}
