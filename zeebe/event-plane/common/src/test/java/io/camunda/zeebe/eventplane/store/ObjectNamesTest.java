/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class ObjectNamesTest {

  private static final UUID HISTORY = UUID.fromString("7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071");
  private static final String ULID = "01JCZ4A1B2C3D4E5F6G7H8J9KM";

  @Test
  void shouldKeepUnreservedCharacters() {
    // when, then
    assertThat(ObjectNames.encode("Cluster-1._~")).isEqualTo("Cluster-1._~");
  }

  @Test
  void shouldEncodeEverythingElseAsUtf8() {
    // when, then
    assertThat(ObjectNames.encode("a/b c%ü")).isEqualTo("a%2Fb%20c%25%C3%BC");
    assertThat(ObjectNames.encode("😀")).isEqualTo("%F0%9F%98%80");
  }

  @Test
  void shouldEncodeNamesThatWouldNavigate() {
    // when, then
    assertThat(ObjectNames.encode(".")).isEqualTo("%2E");
    assertThat(ObjectNames.encode("..")).isEqualTo("%2E%2E");
    assertThat(ObjectNames.encode("...")).isEqualTo("...");
  }

  @Test
  void shouldDecodeWhatWasEncoded() {
    // given
    final var names =
        List.of(
            "default", "prod/eu", "a b", "%", "%2F", "ü", "😀", ".", "..", "x.y~z_1-2", "\u0000");

    // when, then
    for (final var name : names) {
      assertThat(ObjectNames.decode(ObjectNames.encode(name))).isEqualTo(name);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"a b", "a/b", "%", "%2", "%2g", "%zz", "%2f", "%C3", "%FF", "ü", "a%41"})
  void shouldRejectAsEncodedWhatEncodeCouldNotHaveMade(final String encoded) {
    // when, then
    assertThatThrownBy(() -> ObjectNames.decode(encoded))
        .isInstanceOf(InvalidObjectNameException.class)
        .hasMessageContaining("Expected a name in percent-encoded UTF-8")
        .hasMessageContaining("but got '" + encoded + "'");
  }

  @Test
  void shouldNameObjectsAsTheContractDoes() {
    // when, then
    assertThat(ObjectNames.historyPart("prod/eu", "default", HISTORY, 3))
        .isEqualTo("prod%2Feu/default/histories/7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071/3.json");
    assertThat(ObjectNames.segment("prod/eu", "default", 3, HISTORY, 1, 40, 0, ULID))
        .isEqualTo(
            "prod%2Feu/default/3/7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071/"
                + "00000000000000000001-00000000000000000040-L0-01JCZ4A1B2C3D4E5F6G7H8J9KM.seg");
    assertThat(ObjectNames.retentionMarker("c", "t", 3, ULID))
        .isEqualTo("c/t/3/retention/01JCZ4A1B2C3D4E5F6G7H8J9KM.json");
    assertThat(ObjectNames.snapshotDescriptor("c", "t", 3, "12-1-30-30"))
        .isEqualTo("c/t/3/snapshots/12-1-30-30/descriptor.json");
    assertThat(ObjectNames.snapshotFile("c", "t", 3, "12-1-30-30", "000012.sst"))
        .isEqualTo("c/t/3/snapshots/12-1-30-30/000012.sst");
    assertThat(ObjectNames.group("g")).isEqualTo("_groups/g/group.json");
    assertThat(ObjectNames.worker("g", "w-01JD")).isEqualTo("_groups/g/workers/w-01JD.json");
    assertThat(ObjectNames.cursor("g", "prod/eu", "default", 3))
        .isEqualTo("_groups/g/cursors/prod%2Feu/default/3.json");
  }

  @Test
  void shouldNameTheDirectoriesOfObjects() {
    // when, then
    assertThat(ObjectNames.clusterPrefix("prod/eu")).isEqualTo("prod%2Feu/");
    assertThat(ObjectNames.tenantPrefix("c", "a b")).isEqualTo("c/a%20b/");
    assertThat(ObjectNames.historiesPrefix("c", "t")).isEqualTo("c/t/histories/");
    assertThat(ObjectNames.logPrefix("c", "t", 3)).isEqualTo("c/t/3/");
    assertThat(ObjectNames.segmentsPrefix("c", "t", 3, HISTORY))
        .isEqualTo("c/t/3/7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071/");
    assertThat(ObjectNames.retentionPrefix("c", "t", 3)).isEqualTo("c/t/3/retention/");
    assertThat(ObjectNames.snapshotsPrefix("c", "t", 3)).isEqualTo("c/t/3/snapshots/");
    assertThat(ObjectNames.snapshotPrefix("c", "t", 3, "12-1-30-30"))
        .isEqualTo("c/t/3/snapshots/12-1-30-30/");
    assertThat(ObjectNames.groupsPrefix()).isEqualTo("_groups/");
    assertThat(ObjectNames.workersPrefix("g")).isEqualTo("_groups/g/workers/");
    assertThat(ObjectNames.cursorsPrefix("g")).isEqualTo("_groups/g/cursors/");
  }

  @Test
  void shouldStartEveryKeyWithItsDirectoriesPrefix() {
    // given
    final var segment = ObjectNames.segment("c", "t", 3, HISTORY, 1, 2, 0, ULID);
    final var part = ObjectNames.historyPart("c", "t", HISTORY, 3);

    // when, then
    assertThat(segment)
        .startsWith(ObjectNames.segmentsPrefix("c", "t", 3, HISTORY))
        .startsWith(ObjectNames.logPrefix("c", "t", 3))
        .startsWith(ObjectNames.tenantPrefix("c", "t"))
        .startsWith(ObjectNames.clusterPrefix("c"));
    assertThat(part)
        .startsWith(ObjectNames.historiesPrefix("c", "t"))
        .startsWith(ObjectNames.tenantPrefix("c", "t"));
    assertThat(ObjectNames.retentionMarker("c", "t", 3, ULID))
        .startsWith(ObjectNames.retentionPrefix("c", "t", 3));
    assertThat(ObjectNames.snapshotDescriptor("c", "t", 3, "s1"))
        .startsWith(ObjectNames.snapshotPrefix("c", "t", 3, "s1"))
        .startsWith(ObjectNames.snapshotsPrefix("c", "t", 3));
    assertThat(ObjectNames.worker("g", "w")).startsWith(ObjectNames.workersPrefix("g"));
    assertThat(ObjectNames.cursor("g", "c", "t", 3)).startsWith(ObjectNames.cursorsPrefix("g"));
    assertThat(ObjectNames.group("g")).startsWith(ObjectNames.groupsPrefix());
  }

  @Test
  void shouldSortSegmentsByFirstIndexThenLastIndex() {
    // given
    final var keys =
        List.of(
            ObjectNames.segment("c", "t", 3, HISTORY, 9, 10, 0, ULID),
            ObjectNames.segment("c", "t", 3, HISTORY, 10, 10, 0, ULID),
            ObjectNames.segment("c", "t", 3, HISTORY, 100, 200, 0, ULID),
            ObjectNames.segment("c", "t", 3, HISTORY, 100, 200, 1, ULID),
            ObjectNames.segment("c", "t", 3, HISTORY, 1_000, 1_000, 0, ULID),
            ObjectNames.segment("c", "t", 3, HISTORY, Long.MAX_VALUE - 1, Long.MAX_VALUE, 0, ULID));

    // when, then the keys are in log order as text, though 9 < 10 < 100 < 1000 numerically too
    assertThat(keys).isSorted();
  }

  @Test
  void shouldKeepSegmentsOfNewerTimeLaterAmongTheSameIndexes() {
    // given
    final var earlier = Ulid.of(1_790_000_000_000L, new byte[10]);
    final var later = Ulid.of(1_790_000_000_001L, new byte[10]);

    // when, then
    assertThat(ObjectNames.segment("c", "t", 3, HISTORY, 1, 5, 1, earlier))
        .isLessThan(ObjectNames.segment("c", "t", 3, HISTORY, 1, 5, 1, later));
  }

  @Test
  void shouldNotLetANameAddAPathSegment() {
    // when
    final var key = ObjectNames.segment("a/../b", "..", 3, HISTORY, 1, 5, 0, ULID);

    // then
    assertThat(key).startsWith("a%2F..%2Fb/%2E%2E/3/");
    assertThat(ObjectNames.cursor("g/x", "c", "t", 3))
        .isEqualTo("_groups/g%2Fx/cursors/c/t/3.json");
    assertThat(ObjectNames.worker("g", "../w")).isEqualTo("_groups/g/workers/..%2Fw.json");
    assertThat(ObjectNames.snapshotFile("c", "t", 3, "s", ".."))
        .isEqualTo("c/t/3/snapshots/s/%2E%2E");
  }

  @Test
  void shouldRejectAClusterThatStartsWithAnUnderscore() {
    // when, then
    assertThatThrownBy(() -> ObjectNames.clusterPrefix("_groups"))
        .isInstanceOf(InvalidObjectNameException.class)
        .hasMessage("Expected a cluster id that doesn't start with '_', but got '_groups'");
    assertThatThrownBy(() -> ObjectNames.historyPart("_c", "t", HISTORY, 1))
        .isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> ObjectNames.segment("_c", "t", 1, HISTORY, 1, 1, 0, ULID))
        .isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> ObjectNames.cursor("g", "_c", "t", 1))
        .isInstanceOf(InvalidObjectNameException.class);
    assertThat(ObjectNames.tenantPrefix("c", "_t")).as("a tenant may").isEqualTo("c/_t/");
  }

  @Test
  void shouldRejectNamesThatAreEmpty() {
    // when, then
    assertThatThrownBy(() -> ObjectNames.clusterPrefix(""))
        .isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> ObjectNames.tenantPrefix("c", ""))
        .isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> ObjectNames.group("")).isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> ObjectNames.worker("g", ""))
        .isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> ObjectNames.snapshotFile("c", "t", 1, "s", ""))
        .isInstanceOf(InvalidObjectNameException.class);
  }

  @Test
  void shouldRejectAPartitionBelowOne() {
    // when, then
    assertThatThrownBy(() -> ObjectNames.logPrefix("c", "t", 0))
        .isInstanceOf(InvalidObjectNameException.class)
        .hasMessage("Expected a partition of 1 or more, but got '0'");
    assertThatThrownBy(() -> ObjectNames.historyPart("c", "t", HISTORY, -1))
        .isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> ObjectNames.cursor("g", "c", "t", 0))
        .isInstanceOf(InvalidObjectNameException.class);
  }

  @Test
  void shouldRejectASegmentThatIsNotOneOrAUlidThatIsNot() {
    // when, then
    assertThatThrownBy(() -> ObjectNames.segment("c", "t", 1, HISTORY, 5, 4, 0, ULID))
        .isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> ObjectNames.segment("c", "t", 1, HISTORY, -1, 4, 0, ULID))
        .isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> ObjectNames.segment("c", "t", 1, HISTORY, 1, 4, -1, ULID))
        .isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> ObjectNames.segment("c", "t", 1, HISTORY, 1, 4, 0, "not-a-ulid"))
        .isInstanceOf(InvalidObjectNameException.class)
        .hasMessage("Expected a ULID, but got 'not-a-ulid'");
    assertThatThrownBy(() -> ObjectNames.retentionMarker("c", "t", 1, "not-a-ulid"))
        .isInstanceOf(InvalidObjectNameException.class);
  }

  @Test
  void shouldParseASegmentsNameFromItsKey() {
    // given
    final var key = ObjectNames.segment("prod/eu", "default", 3, HISTORY, 41, 1_096_704, 1, ULID);

    // when
    final var name = ObjectNames.parseSegment(key);

    // then
    assertThat(name).isEqualTo(new SegmentName(HISTORY, 41, 1_096_704, 1, ULID));
  }

  @Test
  void shouldParseASegmentsNameWhateverItsKeyStartsWith() {
    // given
    final var below = ObjectNames.segment("c", "t", 3, HISTORY, 1, 2, 0, ULID);
    final var relative = below.substring(ObjectNames.logPrefix("c", "t", 3).length());

    // when, then
    assertThat(ObjectNames.parseSegment(relative)).isEqualTo(ObjectNames.parseSegment(below));
    assertThat(ObjectNames.parseSegment("any/prefix/" + relative))
        .isEqualTo(ObjectNames.parseSegment(below));
  }

  @Test
  void shouldGiveBackWhatSegmentWasGiven() {
    // given
    final var name = new SegmentName(HISTORY, 7, Long.MAX_VALUE, 12, ULID);

    // when
    final var key = ObjectNames.segment("c", "t", 3, name);

    // then
    assertThat(ObjectNames.parseSegment(key)).isEqualTo(name);
    assertThat(key)
        .isEqualTo(ObjectNames.segment("c", "t", 3, HISTORY, 7, Long.MAX_VALUE, 12, ULID));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "segment.seg",
        "c/t/3/7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071/",
        "c/t/3/7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071/00000000000000000001-00000000000000000040-L0-01JCZ4A1B2C3D4E5F6G7H8J9KM.json",
        "c/t/3/7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071/1-40-L0-01JCZ4A1B2C3D4E5F6G7H8J9KM.seg",
        "c/t/3/7F3A9C0E-4B2D-4E8F-9A1B-2C3D4E5F6071/00000000000000000001-00000000000000000040-L0-01JCZ4A1B2C3D4E5F6G7H8J9KM.seg",
        "c/t/3/7f3a9c0e-4b2d-4e8f-9a1b/00000000000000000001-00000000000000000040-L0-01JCZ4A1B2C3D4E5F6G7H8J9KM.seg",
        "c/t/3/7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071/00000000000000000001-00000000000000000040-L-01JCZ4A1B2C3D4E5F6G7H8J9KM.seg",
        "c/t/3/7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071/00000000000000000001-00000000000000000040-L0-01JCZ4A1B2C3D4E5F6G7H8J9K.seg",
        "c/t/3/7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071/00000000000000000001-00000000000000000040-L0-01JCZ4A1B2C3D4E5F6G7H8J9KI.seg",
        "c/t/3/7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071/00000000000000000040-00000000000000000001-L0-01JCZ4A1B2C3D4E5F6G7H8J9KM.seg",
        "c/t/3/7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071/99999999999999999999-99999999999999999999-L0-01JCZ4A1B2C3D4E5F6G7H8J9KM.seg",
        "c/t/3/7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071/00000000000000000001-00000000000000000040-L99999999999-01JCZ4A1B2C3D4E5F6G7H8J9KM.seg",
      })
  void shouldRejectAKeyThatIsNotASegments(final String key) {
    // when, then
    assertThatThrownBy(() -> ObjectNames.parseSegment(key))
        .isInstanceOf(InvalidObjectNameException.class)
        .hasMessageContaining("but got '" + key + "'");
  }

  @Test
  void shouldParseACursorsKeyBackIntoGroupClusterTenantAndPartition() {
    // given
    final var key = ObjectNames.cursor("my group", "prod/eu", "größe", 12);

    // when
    final var name = ObjectNames.parseCursor(key);

    // then
    assertThat(name).isEqualTo(new CursorName("my group", "prod/eu", "größe", 12));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "_groups/g/cursors/c/t/3",
        "_groups/g/cursors/c/t/3.json/",
        "_groups/g/cursors/c/3.json",
        "_groups/g/cursors/c/t/x/3.json",
        "_groups/g/cursors/c/t/0.json",
        "_groups/g/cursors/c/t/03.json",
        "_groups/g/cursors/c/t/03x.json",
        "_groups/g/cursors/c/t/-3.json",
        "_groups/g/cursors/c/t/99999999999.json",
        "_groups/g/workers/w.json",
        "_groups/g/cursors/_c/t/3.json",
        "_groups/g/cursors/c/t/3.json.tmp",
        "_groups/g/cursors/c%2/t/3.json",
        "_groups/g/cursors/c%2f/t/3.json",
        "_groups/g/cursors//t/3.json",
        "c/t/3.json",
      })
  void shouldRejectAKeyThatIsNotACursors(final String key) {
    // when, then
    assertThatThrownBy(() -> ObjectNames.parseCursor(key))
        .isInstanceOf(InvalidObjectNameException.class);
  }
}
