/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.storage.journal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.storage.StorageException;
import io.atomix.storage.journal.index.JournalIndex;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.NamespaceImpl.Builder;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.zip.CRC32;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.BeforeParam;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JournalSegmentWriterTest {

  @ClassRule public static TemporaryFolder temp = new TemporaryFolder();
  public static final Namespace NAMESPACE = new Builder().register(Integer.class).build();
  private static JournalWriter<Integer> writer;

  @Parameter() public String testName;
  private final CRC32 crc32 = new CRC32();

  @Parameters(name = "{0}")
  public static String[] data() {
    return new String[] {"MappedJournal", "FileChannelJournal"};
  }

  @BeforeParam
  public static void setup(final String journalType) throws IOException {
    final FileChannel channel = mock(FileChannel.class);
    final JournalSegmentFile journalFile = mock(JournalSegmentFile.class);
    when(journalFile.openChannel(any())).thenReturn(channel);
    when(journalFile.file()).thenReturn(temp.newFile());

    final JournalSegmentDescriptor descriptor = mock(JournalSegmentDescriptor.class);
    when(descriptor.maxSegmentSize()).thenReturn(1024);

    final JournalSegment<Integer> segment = mock(JournalSegment.class);
    when(segment.descriptor()).thenReturn(descriptor);

    final JournalIndex index = mock(JournalIndex.class);
    if ("FileChannelJournal".equals(journalType)) {
      writer = new FileChannelJournalSegmentWriter<>(journalFile, segment, 1024, index, NAMESPACE);
    } else if ("MappedJournal".equals(journalType)) {
      writer = new MappedJournalSegmentWriter<>(journalFile, segment, 1024, index, NAMESPACE);
    } else {
      throw new IllegalArgumentException(
          String.format("Failed to setup due to unknown journal type '%s'", journalType));
    }
  }

  @Test(expected = StorageException.InvalidChecksum.class)
  public void shouldThrowExceptionOnFailedValidation() {
    writer.append(0, -1);
  }

  @Test
  public void shouldSucceedValidation() {
    // given
    final Integer entry = 0;
    final byte[] serialized = NAMESPACE.serialize(entry);

    crc32.reset();
    crc32.update(serialized);
    final int checksum = (int) crc32.getValue();

    // when/then
    writer.append(entry, checksum);
  }
}
