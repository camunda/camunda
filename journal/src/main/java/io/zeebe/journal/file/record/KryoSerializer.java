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

import com.esotericsoftware.kryo.KryoException;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;
import io.zeebe.journal.JournalRecord;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class KryoSerializer implements JournalRecordBufferWriter, JournalRecordBufferReader {
  private static final Namespace NAMESPACE =
      new Namespace.Builder()
          .register(Namespaces.BASIC)
          .nextId(Namespaces.BEGIN_USER_CUSTOM_ID)
          .register(PersistedJournalRecord.class)
          .register(UnsafeBuffer.class)
          .name("Journal")
          .build();

  public JournalRecord read(final ByteBuffer buffer) {
    return NAMESPACE.deserialize(buffer);
  }

  public void write(final JournalRecord record, final ByteBuffer buffer) {
    try {
      NAMESPACE.serialize(record, buffer);
    } catch (final KryoException e) {
      // Happens when there is not enough space left in the buffer
      throw new BufferOverflowException();
    }
  }
}
