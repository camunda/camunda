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
package io.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;
import io.zeebe.journal.JournalReader;
import io.zeebe.journal.file.record.PersistedJournalRecord;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SegmentedJournalReaderTest {

  private static final int ENTRIES_PER_SEGMENT = 2;
  @TempDir Path directory;
  private final Namespace namespace =
      new Namespace.Builder()
          .register(Namespaces.BASIC)
          .nextId(Namespaces.BEGIN_USER_CUSTOM_ID)
          .register(PersistedJournalRecord.class)
          .register(UnsafeBuffer.class)
          .name("Journal")
          .build();
  private final DirectBuffer data = new UnsafeBuffer("test".getBytes(StandardCharsets.UTF_8));
  private final int entrySize =
      namespace.serialize(new PersistedJournalRecord(1, 1, Integer.MAX_VALUE, data)).length
          + Integer.BYTES;
  private JournalReader reader;
  private SegmentedJournal journal;

  @BeforeEach
  void setup() {
    journal =
        SegmentedJournal.builder()
            .withDirectory(directory.resolve("data").toFile())
            .withMaxSegmentSize(entrySize * ENTRIES_PER_SEGMENT + JournalSegmentDescriptor.BYTES)
            .withMaxEntrySize(entrySize)
            .withJournalIndexDensity(5)
            .build();
    reader = journal.openReader();
  }

  @Test
  void shouldReadAfterCompact() {
    // given
    final int entriesPerSegment = 10;
    long asqn = 1;

    for (int i = 1; i <= entriesPerSegment * 5; i++) {
      assertThat(journal.append(asqn++, data).index()).isEqualTo(i);
    }
    assertThat(reader.hasNext()).isTrue();

    // when - compact up to the first index of segment 3
    final int indexToCompact = entriesPerSegment * 2 + 1;
    journal.deleteUntil(indexToCompact);

    // then
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next().index()).isEqualTo(indexToCompact);
  }
}
