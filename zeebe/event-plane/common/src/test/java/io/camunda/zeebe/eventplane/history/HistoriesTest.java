/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.eventplane.store.StoredObject;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class HistoriesTest {

  private static final UUID A = UUID.fromString("00000000-0000-4000-8000-00000000000a");
  private static final UUID B = UUID.fromString("00000000-0000-4000-8000-00000000000b");
  private static final Instant NOON = Instant.parse("2026-10-20T12:00:00Z");

  @TempDir private Path directory;

  private ObjectStore store;

  @BeforeEach
  void setUp() {
    store = new FileSystemStore(directory);
  }

  @Test
  void shouldLoadNothingFromATenantWithoutHistories() throws IOException {
    // when
    final var lineage = Histories.load(store, "c", "default");

    // then
    assertThat(lineage.manifests()).isEmpty();
    assertThat(lineage.current("c", "default")).isEmpty();
  }

  @Test
  void shouldWriteAPartsManifestAtItsKey() throws IOException {
    // given
    final var part = part("c", "default", A, 3, NOON, Base.NOTHING);

    // when
    Histories.writePart(store, part);

    // then
    final var key = "c/default/histories/%s/3.json".formatted(A);
    assertThat(store.get(key)).hasValue(ManifestJson.write(part));
  }

  @Test
  void shouldWriteTheSameBytesWhenAPartIsWrittenAgain() throws IOException {
    // given
    final var part = part("c", "default", A, 3, NOON, Base.NOTHING);
    Histories.writePart(store, part);
    final var key = ObjectNames.historyPart(new LogId("c", "default", 3), A);
    final var first = store.get(key).orElseThrow();

    // when
    Histories.writePart(store, part);

    // then
    assertThat(store.get(key)).hasValue(first);
    assertThat(store.list(ObjectNames.historiesPrefix("c", "default"), null, 10)).hasSize(1);
  }

  @Test
  void shouldRejectAPartWithMoreThanOnePartition() {
    // given
    final var manifest =
        new HistoryManifest(1, "c", "default", A, NOON, Map.of(1, Base.NOTHING, 2, Base.NOTHING));

    // when, then
    assertThatThrownBy(() -> Histories.writePart(store, manifest))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected a part of history %s to have one partition, but got [1, 2]".formatted(A));
  }

  @Test
  void shouldLoadTheHistoriesAPartitionStarted() throws IOException {
    // given
    Histories.writePart(store, part("c", "default", A, 1, NOON, Base.NOTHING));

    // when
    final var lineage = Histories.load(store, "c", "default");

    // then
    assertThat(lineage.manifests())
        .containsExactly(manifest("c", "default", A, NOON, Map.of(1, Base.NOTHING)));
    assertThat(lineage.current("c", "default")).map(HistoryManifest::historyId).contains(A);
  }

  @Test
  void shouldPutThePartsOfAHistoryTogether() throws IOException {
    // given partitions that started a history, each on its own
    Histories.writePart(
        store, part("c", "default", B, 1, NOON.plusSeconds(1), new Base(A, 900, 400)));
    Histories.writePart(
        store, part("c", "default", B, 3, NOON.plusSeconds(3), new Base(A, 70, 30)));
    Histories.writePart(store, part("c", "default", B, 2, NOON.plusSeconds(2), Base.NOTHING));
    Histories.writePart(store, part("c", "default", A, 1, NOON, Base.NOTHING));

    // when
    final var lineage = Histories.load(store, "c", "default");

    // then the union of the partitions, and the latest creation time
    assertThat(lineage.manifest(B))
        .contains(
            manifest(
                "c",
                "default",
                B,
                NOON.plusSeconds(3),
                Map.of(1, new Base(A, 900, 400), 2, Base.NOTHING, 3, new Base(A, 70, 30))));
    assertThat(lineage.manifest(A)).isPresent();
    assertThat(lineage.current("c", "default")).map(HistoryManifest::historyId).contains(B);
  }

  @Test
  void shouldLoadPartsOfAPartitionThatAgree() throws IOException {
    // given the same partition in two parts, with the same base: an unusual but harmless copy
    final var one = part("c", "default", A, 1, NOON, Base.NOTHING);
    Histories.writePart(store, one);
    store.put(
        "c/default/histories/%s/copy.json".formatted(A),
        ManifestJson.write(
            manifest(
                "c", "default", A, NOON.plusSeconds(5), Map.of(1, Base.NOTHING, 2, Base.NOTHING))));

    // when
    final var lineage = Histories.load(store, "c", "default");

    // then
    assertThat(lineage.manifest(A))
        .contains(
            manifest(
                "c", "default", A, NOON.plusSeconds(5), Map.of(1, Base.NOTHING, 2, Base.NOTHING)));
  }

  @Test
  void shouldRejectTwoPartsThatDisagreeOnAPartition() throws IOException {
    // given partition 1 in two parts of history B, with different bases
    Histories.writePart(store, part("c", "default", B, 1, NOON, new Base(A, 900, 400)));
    store.put(
        "c/default/histories/%s/other.json".formatted(B),
        ManifestJson.write(
            manifest("c", "default", B, NOON, Map.of(1, new Base(A, 901, 401), 2, Base.NOTHING))));

    // when, then
    assertThatThrownBy(() -> Histories.load(store, "c", "default"))
        .isInstanceOfSatisfying(
            InconsistentHistoryException.class, e -> assertThat(e.history()).isEqualTo(B))
        .hasMessageContaining(
            "Expected the parts of history %s to agree on partition 1".formatted(B));
  }

  @Test
  void shouldLoadOnlyTheTenantsOwnHistories() throws IOException {
    // given histories of other tenants and clusters, one of them named like a prefix of ours
    Histories.writePart(store, part("c", "default", A, 1, NOON, Base.NOTHING));
    Histories.writePart(store, part("c", "default2", B, 1, NOON, Base.NOTHING));
    Histories.writePart(store, part("c", "other", B, 1, NOON, Base.NOTHING));
    Histories.writePart(store, part("c2", "default", B, 1, NOON, Base.NOTHING));
    store.put(ObjectNames.logPrefix(new LogId("c", "default", 1)) + "segment.json", new byte[] {1});

    // when
    final var lineage = Histories.load(store, "c", "default");

    // then
    assertThat(lineage.manifests()).extracting(HistoryManifest::historyId).containsExactly(A);
  }

  @Test
  void shouldKeepTheTenantsOfPercentEncodedNamesApart() throws IOException {
    // given
    Histories.writePart(store, part("prod/eu", "a b", A, 1, NOON, Base.NOTHING));
    Histories.writePart(store, part("prod/eu", "a%20b", B, 1, NOON, Base.NOTHING));

    // when, then
    assertThat(Histories.load(store, "prod/eu", "a b").manifests())
        .extracting(HistoryManifest::historyId)
        .containsExactly(A);
    assertThat(Histories.load(store, "prod/eu", "a%20b").manifests())
        .extracting(HistoryManifest::historyId)
        .containsExactly(B);
  }

  @Test
  void shouldRejectAPartOfAnotherTenant() throws IOException {
    // given a part under the tenant's prefix that says it belongs to another
    store.put(
        "c/default/histories/%s/1.json".formatted(A),
        ManifestJson.write(part("c", "other", A, 1, NOON, Base.NOTHING)));

    // when, then
    assertThatThrownBy(() -> Histories.load(store, "c", "default"))
        .isInstanceOfSatisfying(
            InconsistentHistoryException.class, e -> assertThat(e.history()).isEqualTo(A))
        .hasMessageContaining(
            "to belong to cluster 'c' and tenant 'default', but got 'c' and 'other'");
  }

  @Test
  void shouldRejectAPartThatIsNotAManifest() throws IOException {
    // given
    store.put(
        "c/default/histories/%s/1.json".formatted(A),
        "{\"version\":1}".getBytes(StandardCharsets.UTF_8));

    // when, then
    assertThatThrownBy(() -> Histories.load(store, "c", "default"))
        .isInstanceOf(InvalidHistoryJsonException.class);
  }

  @Test
  void shouldLoadEveryPartWhenTheListingHasSeveralPages() throws IOException {
    // given 11 histories, which a listing of 3 at a time reads in 4 pages
    final var expected = new ArrayList<UUID>();
    for (int i = 0; i < 11; i++) {
      final var id = new UUID(0, 100 + i);
      expected.add(id);
      Histories.writePart(
          store, part("c", "default", id, 1 + i % 2, NOON.plusSeconds(i), Base.NOTHING));
    }

    // when
    final var lineage = Histories.load(store, "c", "default", 3);

    // then
    assertThat(lineage.manifests())
        .extracting(HistoryManifest::historyId)
        .containsExactlyElementsOf(expected);
  }

  @Test
  void shouldLoadEveryPartWhenAPageEndsExactlyAtTheLastOne() throws IOException {
    // given 6 parts and a page of 3
    final var ids = new ArrayList<UUID>();
    for (int i = 0; i < 6; i++) {
      ids.add(new UUID(0, 100 + i));
      Histories.writePart(
          store, part("c", "default", ids.get(i), 1, NOON.plusSeconds(i), Base.NOTHING));
    }

    // when
    final var lineage = Histories.load(store, "c", "default", 3);

    // then
    assertThat(lineage.manifests()).hasSize(6);
  }

  @Test
  void shouldLoadHistoriesThatRelateToEachOther() throws IOException {
    // given B restored from A in partition 1, and a purge in partition 2
    Histories.writePart(store, part("c", "default", A, 1, NOON, Base.NOTHING));
    Histories.writePart(store, part("c", "default", A, 2, NOON, Base.NOTHING));
    Histories.writePart(
        store, part("c", "default", B, 1, NOON.plusSeconds(60), new Base(A, 900, 400)));
    Histories.writePart(store, part("c", "default", B, 2, NOON.plusSeconds(61), Base.NOTHING));

    // when
    final var lineage = Histories.load(store, "c", "default");

    // then
    assertThat(lineage.lastShared(A, B, 1)).isEqualTo(new Base(A, 900, 400));
    assertThat(lineage.lastShared(A, B, 2)).isEqualTo(Base.NOTHING);
    assertThat(lineage.path(B, 1)).hasSize(2);
  }

  @Test
  void shouldSkipAPartThatIsDeletedAfterTheListing() throws IOException {
    // given a store that loses a part between listing and reading it
    Histories.writePart(store, part("c", "default", A, 1, NOON, Base.NOTHING));
    Histories.writePart(store, part("c", "default", B, 1, NOON, Base.NOTHING));
    final var vanishing = ObjectNames.historyPart(new LogId("c", "default", 1), A);
    final var forgetful = new StoreThatForgets(store, vanishing);

    // when
    final var lineage = Histories.load(forgetful, "c", "default");

    // then
    assertThat(lineage.manifests()).extracting(HistoryManifest::historyId).containsExactly(B);
  }

  private static HistoryManifest part(
      final String cluster,
      final String tenant,
      final UUID history,
      final int partition,
      final Instant createdAt,
      final Base base) {
    return manifest(cluster, tenant, history, createdAt, Map.of(partition, base));
  }

  private static HistoryManifest manifest(
      final String cluster,
      final String tenant,
      final UUID history,
      final Instant createdAt,
      final Map<Integer, Base> partitions) {
    return new HistoryManifest(1, cluster, tenant, history, createdAt, partitions);
  }

  /** A store that has listed an object, and no longer has it when it is read. */
  private static final class StoreThatForgets implements ObjectStore {

    private final ObjectStore delegate;
    private final String forgotten;

    StoreThatForgets(final ObjectStore delegate, final String forgotten) {
      this.delegate = delegate;
      this.forgotten = forgotten;
    }

    @Override
    public URI root() {
      return delegate.root();
    }

    @Override
    public void put(final String key, final byte[] content) throws IOException {
      delegate.put(key, content);
    }

    @Override
    public Optional<byte[]> get(final String key) throws IOException {
      return key.equals(forgotten) ? Optional.empty() : delegate.get(key);
    }

    @Override
    public byte[] getRange(final String key, final long offset, final int length)
        throws IOException {
      return delegate.getRange(key, offset, length);
    }

    @Override
    public Optional<StoredObject> head(final String key) throws IOException {
      return delegate.head(key);
    }

    @Override
    public List<StoredObject> list(
        final String prefix, @Nullable final String startAfter, final int limit)
        throws IOException {
      return delegate.list(prefix, startAfter, limit);
    }

    @Override
    public List<String> listPrefixes(final String prefix) throws IOException {
      return delegate.listPrefixes(prefix);
    }

    @Override
    public void delete(final String key) throws IOException {
      delegate.delete(key);
    }
  }
}
