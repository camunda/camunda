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

import io.camunda.zeebe.eventplane.segment.LogId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class ObjectNamesTest {

  private static final UUID HISTORY = UUID.fromString("7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071");
  private static final String ULID = "01JCZ4A1B2C3D4E5F6G7H8J9KM";
  private static final LogId LOG = new LogId("c", "t", 3);
  private static final LogId ENCODED = new LogId("prod/eu", "default", 3);

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
    assertThat(ObjectNames.historyPart(ENCODED, HISTORY))
        .isEqualTo("prod%2Feu/default/histories/7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071/3.json");
    assertThat(ObjectNames.segment(ENCODED, HISTORY, 1, 40, 0, ULID))
        .isEqualTo(
            "prod%2Feu/default/3/7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071/"
                + "00000000000000000001-00000000000000000040-L0-01JCZ4A1B2C3D4E5F6G7H8J9KM.seg");
    assertThat(ObjectNames.retentionMarker(LOG, ULID))
        .isEqualTo("c/t/3/retention/01JCZ4A1B2C3D4E5F6G7H8J9KM.json");
    assertThat(ObjectNames.snapshotDescriptor(LOG, "12-1-30-30"))
        .isEqualTo("c/t/3/snapshots/12-1-30-30/descriptor.json");
    assertThat(ObjectNames.snapshotFile(LOG, "12-1-30-30", "000012.sst"))
        .isEqualTo("c/t/3/snapshots/12-1-30-30/000012.sst");
    assertThat(ObjectNames.group("g")).isEqualTo("_groups/g/group.json");
    assertThat(ObjectNames.worker("g", "w-01JD")).isEqualTo("_groups/g/workers/w-01JD.json");
    assertThat(ObjectNames.cursor("g", ENCODED))
        .isEqualTo("_groups/g/cursors/prod%2Feu/default/3.json");
  }

  @Test
  void shouldNameTheDirectoriesOfObjects() {
    // when, then
    assertThat(ObjectNames.clusterPrefix("prod/eu")).isEqualTo("prod%2Feu/");
    assertThat(ObjectNames.tenantPrefix("c", "a b")).isEqualTo("c/a%20b/");
    assertThat(ObjectNames.historiesPrefix("c", "t")).isEqualTo("c/t/histories/");
    assertThat(ObjectNames.logPrefix(LOG)).isEqualTo("c/t/3/");
    assertThat(ObjectNames.segmentsPrefix(LOG, HISTORY))
        .isEqualTo("c/t/3/7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071/");
    assertThat(ObjectNames.retentionPrefix(LOG)).isEqualTo("c/t/3/retention/");
    assertThat(ObjectNames.snapshotsPrefix(LOG)).isEqualTo("c/t/3/snapshots/");
    assertThat(ObjectNames.snapshotPrefix(LOG, "12-1-30-30"))
        .isEqualTo("c/t/3/snapshots/12-1-30-30/");
    assertThat(ObjectNames.groupsPrefix()).isEqualTo("_groups/");
    assertThat(ObjectNames.workersPrefix("g")).isEqualTo("_groups/g/workers/");
    assertThat(ObjectNames.cursorsPrefix("g")).isEqualTo("_groups/g/cursors/");
  }

  @Test
  void shouldStartEveryKeyWithItsDirectoriesPrefix() {
    // given
    final var segment = ObjectNames.segment(LOG, HISTORY, 1, 2, 0, ULID);
    final var part = ObjectNames.historyPart(LOG, HISTORY);

    // when, then
    assertThat(segment)
        .startsWith(ObjectNames.segmentsPrefix(LOG, HISTORY))
        .startsWith(ObjectNames.logPrefix(LOG))
        .startsWith(ObjectNames.tenantPrefix("c", "t"))
        .startsWith(ObjectNames.clusterPrefix("c"));
    assertThat(part)
        .startsWith(ObjectNames.historiesPrefix("c", "t"))
        .startsWith(ObjectNames.tenantPrefix("c", "t"));
    assertThat(ObjectNames.retentionMarker(LOG, ULID)).startsWith(ObjectNames.retentionPrefix(LOG));
    assertThat(ObjectNames.snapshotDescriptor(LOG, "s1"))
        .startsWith(ObjectNames.snapshotPrefix(LOG, "s1"))
        .startsWith(ObjectNames.snapshotsPrefix(LOG));
    assertThat(ObjectNames.worker("g", "w")).startsWith(ObjectNames.workersPrefix("g"));
    assertThat(ObjectNames.cursor("g", LOG)).startsWith(ObjectNames.cursorsPrefix("g"));
    assertThat(ObjectNames.group("g")).startsWith(ObjectNames.groupsPrefix());
  }

  @Test
  void shouldSortSegmentsByFirstIndexThenLastIndex() {
    // given
    final var keys =
        List.of(
            ObjectNames.segment(LOG, HISTORY, 9, 10, 0, ULID),
            ObjectNames.segment(LOG, HISTORY, 10, 10, 0, ULID),
            ObjectNames.segment(LOG, HISTORY, 100, 200, 0, ULID),
            ObjectNames.segment(LOG, HISTORY, 100, 200, 1, ULID),
            ObjectNames.segment(LOG, HISTORY, 1_000, 1_000, 0, ULID),
            ObjectNames.segment(LOG, HISTORY, Long.MAX_VALUE - 1, Long.MAX_VALUE, 0, ULID));

    // when, then the keys are in log order as text, though 9 < 10 < 100 < 1000 numerically too
    assertThat(keys).isSorted();
  }

  @Test
  void shouldKeepSegmentsOfNewerTimeLaterAmongTheSameIndexes() {
    // given
    final var earlier = Ulid.of(1_790_000_000_000L, new byte[10]);
    final var later = Ulid.of(1_790_000_000_001L, new byte[10]);

    // when, then
    assertThat(ObjectNames.segment(LOG, HISTORY, 1, 5, 1, earlier))
        .isLessThan(ObjectNames.segment(LOG, HISTORY, 1, 5, 1, later));
  }

  @Test
  void shouldNotLetANameAddAPathSegment() {
    // when
    final var key = ObjectNames.segment(new LogId("a/../b", "..", 3), HISTORY, 1, 5, 0, ULID);

    // then
    assertThat(key).startsWith("a%2F..%2Fb/%2E%2E/3/");
    assertThat(ObjectNames.cursor("g/x", LOG)).isEqualTo("_groups/g%2Fx/cursors/c/t/3.json");
    assertThat(ObjectNames.worker("g", "../w")).isEqualTo("_groups/g/workers/..%2Fw.json");
    assertThat(ObjectNames.snapshotFile(LOG, "s", "..")).isEqualTo("c/t/3/snapshots/s/%2E%2E");
  }

  @Test
  void shouldRejectAClusterThatStartsWithAnUnderscore() {
    // when, then
    assertThatThrownBy(() -> ObjectNames.clusterPrefix("_groups"))
        .isInstanceOf(InvalidObjectNameException.class)
        .hasMessage("Expected a cluster id that doesn't start with '_', but got '_groups'");
    final var underscore = new LogId("_c", "t", 1);
    assertThatThrownBy(() -> ObjectNames.historyPart(underscore, HISTORY))
        .isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> ObjectNames.segment(underscore, HISTORY, 1, 1, 0, ULID))
        .isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> ObjectNames.cursor("g", underscore))
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
    assertThatThrownBy(() -> ObjectNames.snapshotFile(LOG, "s", ""))
        .isInstanceOf(InvalidObjectNameException.class);
  }

  @Test
  void shouldRejectASegmentThatIsNotOneOrAUlidThatIsNot() {
    // when, then
    assertThatThrownBy(() -> ObjectNames.segment(LOG, HISTORY, 5, 4, 0, ULID))
        .isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> ObjectNames.segment(LOG, HISTORY, -1, 4, 0, ULID))
        .isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> ObjectNames.segment(LOG, HISTORY, 1, 4, -1, ULID))
        .isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> ObjectNames.segment(LOG, HISTORY, 1, 4, 0, "not-a-ulid"))
        .isInstanceOf(InvalidObjectNameException.class)
        .hasMessage("Expected a ULID, but got 'not-a-ulid'");
    assertThatThrownBy(() -> ObjectNames.retentionMarker(LOG, "not-a-ulid"))
        .isInstanceOf(InvalidObjectNameException.class);
  }

  @Test
  void shouldParseASegmentsNameFromItsKey() {
    // given
    final var key = ObjectNames.segment(ENCODED, HISTORY, 41, 1_096_704, 1, ULID);

    // when
    final var name = ObjectNames.parseSegment(key);

    // then
    assertThat(name).isEqualTo(new SegmentName(HISTORY, 41, 1_096_704, 1, ULID));
  }

  @Test
  void shouldParseASegmentsNameWhateverItsKeyStartsWith() {
    // given
    final var below = ObjectNames.segment(LOG, HISTORY, 1, 2, 0, ULID);
    final var relative = below.substring(ObjectNames.logPrefix(LOG).length());

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
    final var key = ObjectNames.segment(LOG, name);

    // then
    assertThat(ObjectNames.parseSegment(key)).isEqualTo(name);
    assertThat(key).isEqualTo(ObjectNames.segment(LOG, HISTORY, 7, Long.MAX_VALUE, 12, ULID));
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
  void shouldParseACursorsKeyBackIntoGroupAndLog() {
    // given
    final var log = new LogId("prod/eu", "größe", 12);
    final var key = ObjectNames.cursor("my group", log);

    // when
    final var name = ObjectNames.parseCursor(key);

    // then
    assertThat(name).isEqualTo(new CursorName("my group", log));
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
        "_groups/g/cursors/%20/t/3.json",
        "_groups/g/cursors/c/%20/3.json",
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
