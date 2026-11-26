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
package io.atomix.raft.storage.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Named.named;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.StorageException;
import io.camunda.zeebe.journal.fs.PosixFs;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MetaStoreTest {
  @TempDir Path temporaryFolder;
  @AutoClose MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private MetaStore metaStore;
  private RaftStorage storage;

  @BeforeEach
  public void setup() throws IOException {
    storage = RaftStorage.builder(meterRegistry).withDirectory(temporaryFolder.toFile()).build();
    metaStore = new MetaStore(storage, meterRegistry);
  }

  @AfterEach
  public void tearDown() {
    metaStore.close();
  }

  @Test
  void shouldDropPageCacheOfExistingFilesBeforeReadingThem() throws IOException {
    // given - the files exist from a previous start
    metaStore.close();
    final var posixFs = mock(PosixFs.class);

    // when
    metaStore = new MetaStore(storage, meterRegistry, posixFs);

    // then
    verify(posixFs).dropPageCache(temporaryFolder.resolve(storage.prefix() + ".meta"));
    verify(posixFs).dropPageCache(temporaryFolder.resolve(storage.prefix() + ".conf"));
  }

  @Test
  void shouldNotDropPageCacheOfNewFiles() throws IOException {
    // given
    final var newStorage =
        RaftStorage.builder(meterRegistry)
            .withDirectory(temporaryFolder.resolve("new").toFile())
            .build();
    final var posixFs = mock(PosixFs.class);

    // when
    try (final var newMetaStore = new MetaStore(newStorage, meterRegistry, posixFs)) {

      // then
      verifyNoInteractions(posixFs);
    }
  }

  @Nested
  final class MetadataTest {

    @Test
    void shouldStoreAndLoadTerm() {
      // when
      metaStore.storeTerm(2L);

      // then
      assertThat(metaStore.loadTerm()).isEqualTo(2L);
    }

    @Test
    void shouldStoreAndLoadVote() {
      // when
      metaStore.storeVote(new MemberId("id"));

      // then
      assertThat(metaStore.loadVote().id()).isEqualTo("id");
    }

    @Test
    void shouldLoadExistingTerm() throws IOException {
      // given
      metaStore.storeTerm(2L);

      // when
      metaStore.close();
      metaStore = new MetaStore(storage, meterRegistry);

      // then
      assertThat(metaStore.loadTerm()).isEqualTo(2L);
    }

    @Test
    void shouldLoadExistingVote() throws IOException {
      // given
      metaStore.storeVote(new MemberId("id"));

      // when
      metaStore.close();
      metaStore = new MetaStore(storage, meterRegistry);

      // then
      assertThat(metaStore.loadVote().id()).isEqualTo("id");
    }

    @Test
    void shouldLoadEmptyMeta() {
      // when -then
      assertThat(metaStore.loadVote()).isNull();

      // when - then
      assertThat(metaStore.loadTerm()).isEqualTo(0);
    }

    @Test
    void shouldLoadEmptyVoteWhenTermExists() {
      // given
      metaStore.storeTerm(1);

      // when - then
      assertThat(metaStore.loadVote()).isNull();
    }

    @Test
    void shouldLoadLatestValuesAfterRestart() throws IOException {
      //  given
      metaStore.storeTerm(2L);
      metaStore.storeCommitIndex(1L);
      metaStore.storeVote(MemberId.from("1"));
      metaStore.storeTerm(3L);
      metaStore.storeCommitIndex(2L);
      // different length than previous value
      metaStore.storeVote(MemberId.from("11029830219830192831"));

      // when
      metaStore.close();
      metaStore = new MetaStore(storage, meterRegistry);

      // then
      assertThat(metaStore.loadTerm()).isEqualTo(3L);
      assertThat(metaStore.commitIndex()).isEqualTo(2L);
      assertThat(metaStore.loadVote().id()).isEqualTo("11029830219830192831");
    }

    @Test
    void shouldStoreAndLoadLastFlushedIndex() {
      // given
      metaStore.storeLastFlushedIndex(5L);

      // when/then
      assertThat(metaStore.loadLastFlushedIndex()).isEqualTo(5L);
    }

    @Test
    void shouldStoreAndLoadLastFlushedIndexAfterRestart() throws IOException {
      // given
      metaStore.storeLastFlushedIndex(5L);

      // when
      metaStore.close();
      metaStore = new MetaStore(storage, meterRegistry);

      // then
      assertThat(metaStore.loadLastFlushedIndex()).isEqualTo(5L);
    }

    @Test
    void shouldLoadLatestWrittenIndex() throws IOException {
      // given
      metaStore.storeLastFlushedIndex(5L);

      // when
      metaStore.storeLastFlushedIndex(7L);

      // then
      assertThat(metaStore.loadLastFlushedIndex()).isEqualTo(7L);

      // when
      metaStore.storeLastFlushedIndex(8L);

      metaStore.close();
      metaStore = new MetaStore(storage, meterRegistry);

      // then
      assertThat(metaStore.loadLastFlushedIndex()).isEqualTo(8L);
    }

    @Test
    void shouldStoreAndLoadAllMetadata() {
      // when
      metaStore.storeTerm(1L);
      metaStore.storeLastFlushedIndex(2L);
      metaStore.storeCommitIndex(12);
      metaStore.storeVote(MemberId.from("a1029381092831"));

      // then
      assertThat(metaStore.loadTerm()).isEqualTo(1L);
      assertThat(metaStore.loadLastFlushedIndex()).isEqualTo(2L);
      assertThat(metaStore.commitIndex()).isEqualTo(12);
      assertThat(metaStore.loadVote()).isEqualTo(MemberId.from("a1029381092831"));
    }
  }

  @Nested
  final class ConfigurationTest {

    @Test
    void shouldLoadEmptyConfig() {
      // when -then
      assertThat(metaStore.loadConfiguration()).isNull();
    }

    @ParameterizedTest
    @MethodSource("provideConfigurations")
    void shouldStoreAndLoadConfiguration(final Configuration config) {
      // given
      metaStore.storeConfiguration(config);

      // when
      final Configuration readConfig = metaStore.loadConfiguration();

      // then
      assertThat(readConfig).isEqualTo(config);
    }

    @Test
    void shouldLoadExistingConfiguration() throws IOException {
      // given
      final Configuration config = getConfiguration(1, 2);
      metaStore.storeConfiguration(config);

      // when
      metaStore.close();
      metaStore = new MetaStore(storage, meterRegistry);

      // then
      final Configuration readConfig = metaStore.loadConfiguration();

      assertThat(readConfig).isEqualTo(config);
    }

    @Test
    void shouldLoadLatestConfigurationAfterRestart() throws IOException {
      // given
      final Configuration firstConfig = getConfiguration(1, 2);
      metaStore.storeConfiguration(firstConfig);
      final Configuration secondConfig = getConfiguration(3, 4);
      metaStore.storeConfiguration(secondConfig);

      // when
      metaStore.close();
      metaStore = new MetaStore(storage, meterRegistry);
      final Configuration readConfig = metaStore.loadConfiguration();

      // then
      assertThat(readConfig).isEqualTo(secondConfig);
    }

    @Test
    void shouldStoreConfigurationMultipleTimes() {
      // given
      final Configuration firstConfig = getConfiguration(1, 2);
      metaStore.storeConfiguration(firstConfig);
      final Configuration secondConfig = getConfiguration(3, 4);
      metaStore.storeConfiguration(secondConfig);

      // when
      final Configuration readConfig = metaStore.loadConfiguration();

      // then
      assertThat(readConfig).isEqualTo(secondConfig);
    }

    private static Configuration getConfiguration(final long index, final long term) {
      return new Configuration(index, term, 1234L, getMembers("0", "1"));
    }

    private static ArrayList<RaftMember> getMembers(final String m0, final String m1) {
      return new ArrayList<>(
          Set.of(
              new DefaultRaftMember(MemberId.from(m0), Type.ACTIVE, Instant.ofEpochMilli(12345)),
              new DefaultRaftMember(MemberId.from(m1), Type.PASSIVE, Instant.ofEpochMilli(12346))));
    }

    private static Stream<Arguments> provideConfigurations() {
      return Stream.of(
          Arguments.of(named("default", getConfiguration(2, 3))),
          Arguments.of(
              named(
                  "joint consensus",
                  new Configuration(2, 3, 1234L, getMembers("0", "2"), getMembers("0", "1")))),
          Arguments.of(
              named(
                  "force configuration",
                  new Configuration(2, 3, 1235L, getMembers("0", "3"), List.of(), true))));
    }
  }

  @Nested
  final class WriteFailureTest {

    @Test
    void shouldRecordFailedWriteForThePartition() throws IOException {
      // given - the caller may swallow the exception, e.g. in a future's callback
      final var failingStore = spy(metaStore);
      final var error = new IOException("Input/output error");
      doThrow(error).when(failingStore).write(any(), any(), anyBoolean());

      // when
      final var thrown = catchThrowable(() -> failingStore.storeTerm(2L));

      // then
      assertThat(thrown).isInstanceOf(StorageException.class).hasCause(error);
      assertThat(storage.syncFailure().get()).isSameAs(thrown);
    }

    @Test
    void shouldFailWritesAfterAnotherSyncOfThePartitionFailed() {
      // given - e.g. flushing the journal failed
      storage.syncFailure().record(new IOException("Input/output error"));

      // when
      final var thrown = catchThrowable(() -> metaStore.storeTerm(2L));

      // then
      assertThat(thrown).isInstanceOf(StorageException.class);
      assertThat(metaStore.loadTerm()).isZero();
    }

    @Test
    void shouldFailEveryWriteAfterFailedWrite() throws IOException {
      // given - like a failed fsync, the write fails once and would then seem to succeed
      final var failingStore = spy(metaStore);
      final var error = new IOException("Input/output error");
      doThrow(error).doCallRealMethod().when(failingStore).write(any(), any(), anyBoolean());
      assertThatThrownBy(() -> failingStore.storeTerm(2L)).isInstanceOf(StorageException.class);

      // when
      final var thrown = catchThrowable(() -> failingStore.storeVote(MemberId.from("1")));

      // then - no later write is attempted, so none can seem to succeed
      assertThat(thrown).isInstanceOf(StorageException.class).hasRootCause(error);
      verify(failingStore, times(1)).write(any(), any(), anyBoolean());
    }
  }
}
