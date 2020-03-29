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
package io.atomix.raft.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Raft storage test. */
public class RaftStorageTest {

  private static final Path PATH = Paths.get("target/test-logs/");

  @Test
  public void testDefaultConfiguration() throws Exception {
    final RaftStorage storage = RaftStorage.builder().build();
    assertEquals("atomix", storage.prefix());
    assertEquals(new File(System.getProperty("user.dir")), storage.directory());
    assertEquals(1024 * 1024 * 32, storage.maxLogSegmentSize());
    assertEquals(1024 * 1024, storage.maxLogEntriesPerSegment());
    assertTrue(storage.dynamicCompaction());
    assertEquals(.2, storage.freeDiskBuffer(), .01);
    assertTrue(storage.isFlushOnCommit());
    assertFalse(storage.isRetainStaleSnapshots());
    assertTrue(storage.statistics().getFreeMemory() > 0);
  }

  @Test
  public void testCustomConfiguration() throws Exception {
    final RaftStorage storage =
        RaftStorage.builder()
            .withPrefix("foo")
            .withDirectory(new File(PATH.toFile(), "foo"))
            .withMaxSegmentSize(1024 * 1024)
            .withMaxEntriesPerSegment(1024)
            .withDynamicCompaction(false)
            .withFreeDiskBuffer(.5)
            .withFlushOnCommit(false)
            .withRetainStaleSnapshots()
            .build();
    assertEquals("foo", storage.prefix());
    assertEquals(new File(PATH.toFile(), "foo"), storage.directory());
    assertEquals(1024 * 1024, storage.maxLogSegmentSize());
    assertEquals(1024, storage.maxLogEntriesPerSegment());
    assertFalse(storage.dynamicCompaction());
    assertEquals(.5, storage.freeDiskBuffer(), .01);
    assertFalse(storage.isFlushOnCommit());
    assertTrue(storage.isRetainStaleSnapshots());
  }

  @Test
  public void testCustomConfiguration2() throws Exception {
    final RaftStorage storage =
        RaftStorage.builder()
            .withDirectory(PATH.toString() + "/baz")
            .withDynamicCompaction()
            .withFlushOnCommit()
            .build();
    assertEquals(new File(PATH.toFile(), "baz"), storage.directory());
    assertTrue(storage.dynamicCompaction());
    assertTrue(storage.isFlushOnCommit());
  }

  @Test
  public void testStorageLock() throws Exception {
    final RaftStorage storage1 =
        RaftStorage.builder().withDirectory(PATH.toFile()).withPrefix("test").build();

    assertTrue(storage1.lock("a"));

    final RaftStorage storage2 =
        RaftStorage.builder().withDirectory(PATH.toFile()).withPrefix("test").build();

    assertFalse(storage2.lock("b"));

    final RaftStorage storage3 =
        RaftStorage.builder().withDirectory(PATH.toFile()).withPrefix("test").build();

    assertTrue(storage3.lock("a"));
  }

  @Before
  @After
  public void cleanupStorage() throws IOException {
    if (Files.exists(PATH)) {
      Files.walkFileTree(
          PATH,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                throws IOException {
              Files.delete(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc)
                throws IOException {
              Files.delete(dir);
              return FileVisitResult.CONTINUE;
            }
          });
    }
  }
}
