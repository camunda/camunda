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

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.storage.log.RaftLogFlusher;
import io.camunda.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.After;
import org.junit.Test;

/** Raft storage test. */
public class RaftStorageTest {

  private static final Path PATH = Paths.get("target/test-logs/");

  @Test
  public void testDefaultConfiguration() {
    final RaftStorage storage = RaftStorage.builder().build();
    assertThat(storage.prefix()).isEqualTo("atomix");
    assertThat(storage.directory()).isEqualTo(new File(System.getProperty("user.dir")));
  }

  @Test
  public void testCustomConfiguration() {
    final RaftStorage storage =
        RaftStorage.builder()
            .withPrefix("foo")
            .withDirectory(new File(PATH.toFile(), "foo"))
            .withMaxSegmentSize(1024 * 1024)
            .withFreeDiskSpace(100)
            .withFlusherFactory(RaftLogFlusher.Factory::noop)
            .build();
    assertThat(storage.prefix()).isEqualTo("foo");
    assertThat(storage.directory()).isEqualTo(new File(PATH.toFile(), "foo"));
  }

  @Test
  public void canAcquireLockOnEmptyDirectory() {
    // given empty directory in PATH

    // when
    final RaftStorage storage1 =
        RaftStorage.builder().withDirectory(PATH.toFile()).withPrefix("test").build();

    // then
    assertThat(storage1.lock("a")).isTrue();
  }

  @Test
  public void cannotLockAlreadyLockedDirectory() {
    // given
    final RaftStorage storage1 =
        RaftStorage.builder().withDirectory(PATH.toFile()).withPrefix("test").build();
    storage1.lock("a");

    // when
    final RaftStorage storage2 =
        RaftStorage.builder().withDirectory(PATH.toFile()).withPrefix("test").build();

    // then
    assertThat(storage2.lock("b")).isFalse();
  }

  @Test
  public void canAcquireLockOnDirectoryLockedBySameNode() {
    // given
    final RaftStorage storage1 =
        RaftStorage.builder().withDirectory(PATH.toFile()).withPrefix("test").build();
    storage1.lock("a");

    // when
    final RaftStorage storage3 =
        RaftStorage.builder().withDirectory(PATH.toFile()).withPrefix("test").build();

    // then
    assertThat(storage3.lock("a")).isTrue();
  }

  @After
  public void cleanupStorage() throws IOException {
    FileUtil.deleteFolderIfExists(PATH);
  }
}
