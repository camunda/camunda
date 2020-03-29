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
package io.atomix.raft.storage.snapshot.impl;

import static org.junit.Assert.assertEquals;

import io.atomix.storage.buffer.Buffer;
import io.atomix.storage.buffer.HeapBuffer;
import org.junit.Test;

/** Snapshot descriptor test. */
public class DefaultSnapshotDescriptorTest {

  @Test
  public void testSnapshotDescriptor() throws Exception {
    final DefaultSnapshotDescriptor descriptor =
        DefaultSnapshotDescriptor.builder().withIndex(2).withTimestamp(3).withTerm(4).build();
    assertEquals(2, descriptor.index());
    assertEquals(3, descriptor.timestamp());
    assertEquals(4, descriptor.term());
    assertEquals(1, descriptor.version());
  }

  @Test
  public void testCopySnapshotDescriptor() throws Exception {
    DefaultSnapshotDescriptor descriptor =
        DefaultSnapshotDescriptor.builder().withIndex(2).withTimestamp(3).withTerm(4).build();
    final Buffer buffer = HeapBuffer.allocate(DefaultSnapshotDescriptor.BYTES);
    descriptor.copyTo(buffer);
    buffer.flip();
    descriptor = new DefaultSnapshotDescriptor(buffer);
    assertEquals(2, descriptor.index());
    assertEquals(3, descriptor.timestamp());
    assertEquals(4, descriptor.term());
    assertEquals(1, descriptor.version());
  }
}
