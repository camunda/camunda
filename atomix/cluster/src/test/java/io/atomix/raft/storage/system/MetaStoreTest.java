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

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.storage.RaftStorage;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MetaStoreTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private MetaStore metaStore;
  private RaftStorage storage;

  @Before
  public void setup() throws IOException {
    storage = RaftStorage.builder().withDirectory(temporaryFolder.newFolder("store")).build();
    metaStore = new MetaStore(storage);
  }

  @Test
  public void shouldStoreAndLoadConfiguration() {
    // given
    final Configuration config = getConfiguration(1, 2);
    metaStore.storeConfiguration(config);

    // when
    final Configuration readConfig = metaStore.loadConfiguration();

    // then
    assertThat(readConfig.index()).isEqualTo(config.index());
    assertThat(readConfig.term()).isEqualTo(config.term());
    assertThat(readConfig.time()).isEqualTo(config.time());
    assertThat(readConfig.members())
        .containsExactlyInAnyOrder(config.members().toArray(new RaftMember[0]));
  }

  private Configuration getConfiguration(final long index, final long term) {
    return new Configuration(
        index,
        term,
        1234L,
        new ArrayList<>(
            Set.of(
                new DefaultRaftMember(MemberId.from("0"), Type.ACTIVE, Instant.ofEpochMilli(12345)),
                new DefaultRaftMember(
                    MemberId.from("2"), Type.PASSIVE, Instant.ofEpochMilli(12346)))));
  }

  @Test
  public void shouldStoreAndLoadTerm() {
    // when
    metaStore.storeTerm(2L);

    // then
    assertThat(metaStore.loadTerm()).isEqualTo(2L);
  }

  @Test
  public void shouldStoreAndLoadVote() {
    // when
    metaStore.storeVote(new MemberId("id"));

    // then
    assertThat(metaStore.loadVote().id()).isEqualTo("id");
  }

  @Test
  public void shouldLoadExistingConfiguration() throws IOException {
    // given
    final Configuration config = getConfiguration(1, 2);
    metaStore.storeConfiguration(config);

    // when
    metaStore.close();
    metaStore = new MetaStore(storage);

    // then
    final Configuration readConfig = metaStore.loadConfiguration();

    assertThat(readConfig.index()).isEqualTo(config.index());
    assertThat(readConfig.term()).isEqualTo(config.term());
    assertThat(readConfig.time()).isEqualTo(config.time());
    assertThat(readConfig.members())
        .containsExactlyInAnyOrder(config.members().toArray(new RaftMember[0]));
  }

  @Test
  public void shouldLoadExistingTerm() throws IOException {
    // given
    metaStore.storeTerm(2L);

    // when
    metaStore.close();
    metaStore = new MetaStore(storage);

    // then
    assertThat(metaStore.loadTerm()).isEqualTo(2L);
  }

  @Test
  public void shouldLoadExistingVote() throws IOException {
    // given
    metaStore.storeVote(new MemberId("id"));

    // when
    metaStore.close();
    metaStore = new MetaStore(storage);

    // then
    assertThat(metaStore.loadVote().id()).isEqualTo("id");
  }

  @Test
  public void shouldLoadEmptyMeta() {
    // when -then
    assertThat(metaStore.loadVote()).isNull();

    // when - then
    assertThat(metaStore.loadTerm()).isEqualTo(0);
  }

  @Test
  public void shouldLoadEmptyVoteWhenTermExists() {
    // given
    metaStore.storeTerm(1);

    // when - then
    assertThat(metaStore.loadVote()).isNull();
  }

  @Test
  public void shouldLoadEmptyConfig() {
    // when -then
    assertThat(metaStore.loadConfiguration()).isNull();
  }

  @Test
  public void shouldLoadLatestTermAndVoteAfterRestart() throws IOException {
    //  given
    metaStore.storeTerm(2L);
    metaStore.storeVote(MemberId.from("0"));
    metaStore.storeTerm(3L);
    metaStore.storeVote(MemberId.from("1"));

    // when
    metaStore.close();
    metaStore = new MetaStore(storage);

    // then
    assertThat(metaStore.loadTerm()).isEqualTo(3L);
    assertThat(metaStore.loadVote().id()).isEqualTo("1");
  }

  @Test
  public void shouldStoreConfigurationMultipleTimes() {
    // given
    final Configuration firstConfig = getConfiguration(1, 2);
    metaStore.storeConfiguration(firstConfig);
    final Configuration secondConfig = getConfiguration(3, 4);
    metaStore.storeConfiguration(secondConfig);

    // when
    final Configuration readConfig = metaStore.loadConfiguration();

    // then
    assertThat(readConfig.index()).isEqualTo(secondConfig.index());
    assertThat(readConfig.term()).isEqualTo(secondConfig.term());
    assertThat(readConfig.time()).isEqualTo(secondConfig.time());
    assertThat(readConfig.members())
        .containsExactlyInAnyOrder(secondConfig.members().toArray(new RaftMember[0]));
  }

  @Test
  public void shouldLoadLatestConfigurationAfterRestart() throws IOException {
    // given
    final Configuration firstConfig = getConfiguration(1, 2);
    metaStore.storeConfiguration(firstConfig);
    final Configuration secondConfig = getConfiguration(3, 4);
    metaStore.storeConfiguration(secondConfig);

    // when
    metaStore.close();
    metaStore = new MetaStore(storage);
    final Configuration readConfig = metaStore.loadConfiguration();

    // then
    assertThat(readConfig.index()).isEqualTo(secondConfig.index());
    assertThat(readConfig.term()).isEqualTo(secondConfig.term());
    assertThat(readConfig.time()).isEqualTo(secondConfig.time());
    assertThat(readConfig.members())
        .containsExactlyInAnyOrder(secondConfig.members().toArray(new RaftMember[0]));
  }

  @Test
  public void shouldStoreAndLoadLastFlushedIndex() {
    // given
    metaStore.storeLastFlushedIndex(5L);

    // when/then
    assertThat(metaStore.loadLastFlushedIndex()).isEqualTo(5L);
  }

  @Test
  public void shouldStoreAndLoadLastFlushedIndexAfterRestart() throws IOException {
    // given
    metaStore.storeLastFlushedIndex(5L);

    // when
    metaStore.close();
    metaStore = new MetaStore(storage);

    // then
    assertThat(metaStore.loadLastFlushedIndex()).isEqualTo(5L);
  }

  @Test
  public void shouldLoadLatestWrittenIndex() throws IOException {
    // given
    metaStore.storeLastFlushedIndex(5L);

    // when
    metaStore.storeLastFlushedIndex(7L);

    // then
    assertThat(metaStore.loadLastFlushedIndex()).isEqualTo(7L);

    // when
    metaStore.storeLastFlushedIndex(8L);

    metaStore.close();
    metaStore = new MetaStore(storage);

    // then
    assertThat(metaStore.loadLastFlushedIndex()).isEqualTo(8L);
  }

  @Test
  public void shouldStoreAndLoadAllMetadata() {
    // when
    metaStore.storeTerm(1L);
    metaStore.storeLastFlushedIndex(2L);
    metaStore.storeVote(MemberId.from("a"));

    // then
    assertThat(metaStore.loadTerm()).isEqualTo(1L);
    assertThat(metaStore.loadLastFlushedIndex()).isEqualTo(2L);
    assertThat(metaStore.loadVote()).isEqualTo(MemberId.from("a"));
  }
}
