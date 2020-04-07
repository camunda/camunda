/*
 * Copyright 2017-present Open Networking Foundation
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

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import org.junit.Test;

/**
 * Segment descriptor test.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class JournalSegmentDescriptorTest {

  /** Tests the segment descriptor builder. */
  @Test
  public void testDescriptorBuilder() {
    final JournalSegmentDescriptor descriptor =
        JournalSegmentDescriptor.builder(ByteBuffer.allocate(JournalSegmentDescriptor.BYTES))
            .withId(2)
            .withIndex(1025)
            .withMaxSegmentSize(1024 * 1024)
            .withMaxEntries(2048)
            .build();

    assertEquals(2, descriptor.id());
    assertEquals(JournalSegmentDescriptor.VERSION, descriptor.version());
    assertEquals(1025, descriptor.index());
    assertEquals(1024 * 1024, descriptor.maxSegmentSize());
    assertEquals(2048, descriptor.maxEntries());

    assertEquals(0, descriptor.updated());
    final long time = System.currentTimeMillis();
    descriptor.update(time);
    assertEquals(time, descriptor.updated());
  }

  /** Tests copying the segment descriptor. */
  @Test
  public void testDescriptorCopy() {
    JournalSegmentDescriptor descriptor =
        JournalSegmentDescriptor.builder()
            .withId(2)
            .withIndex(1025)
            .withMaxSegmentSize(1024 * 1024)
            .withMaxEntries(2048)
            .build();

    final long time = System.currentTimeMillis();
    descriptor.update(time);

    descriptor = descriptor.copyTo(ByteBuffer.allocate(JournalSegmentDescriptor.BYTES));

    assertEquals(2, descriptor.id());
    assertEquals(JournalSegmentDescriptor.VERSION, descriptor.version());
    assertEquals(1025, descriptor.index());
    assertEquals(1024 * 1024, descriptor.maxSegmentSize());
    assertEquals(2048, descriptor.maxEntries());
    assertEquals(time, descriptor.updated());
  }
}
