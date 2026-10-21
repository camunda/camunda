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
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.eventplane.segment.LogId;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** What every {@link ObjectStore} does the same way. A store's own test extends it. */
abstract class ObjectStoreContractTest {

  private static final UUID HISTORY = UUID.fromString("7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071");
  private static final LogId LOG = new LogId("c", "default", 1);

  protected ObjectStore store;

  /** A store without objects, which no other test shares. */
  protected abstract ObjectStore openStore() throws IOException;

  @BeforeEach
  void open() throws IOException {
    store = openStore();
  }

  @AfterEach
  void close() throws IOException {
    store.close();
  }

  @Test
  void shouldHaveARootThatEndsInASlashAndNameObjectsBelowIt() {
    // when
    final var root = store.root();
    final var uri = store.uri("a/b.json");

    // then
    assertThat(root.toString()).endsWith("/");
    assertThat(uri).isEqualTo(URI.create(root + "a/b.json"));
  }

  @Test
  void shouldReadWhatWasPut() throws IOException {
    // given
    final var content = "hello".getBytes(StandardCharsets.UTF_8);

    // when
    store.put("a/b.json", content);

    // then
    assertThat(store.get("a/b.json")).hasValue(content);
  }

  @Test
  void shouldReplaceAnObject() throws IOException {
    // given
    store.put("a.json", bytes("first, and longer"));

    // when
    store.put("a.json", bytes("second"));

    // then
    assertThat(store.get("a.json")).hasValue(bytes("second"));
    assertThat(store.head("a.json")).get().extracting(StoredObject::size).isEqualTo(6L);
  }

  @Test
  void shouldKeepEveryByteOfBinaryContent() throws IOException {
    // given
    final var content = new byte[300_000];
    new Random(42).nextBytes(content);
    content[0] = 0;
    content[1] = (byte) 0xFF;

    // when
    store.put("binary/object", content);

    // then
    assertThat(store.get("binary/object")).hasValue(content);
    assertThat(store.getRange("binary/object", 1, 4)).containsExactly(slice(content, 1, 4));
  }

  @Test
  void shouldKeepAnEmptyObject() throws IOException {
    // when
    store.put("empty", new byte[0]);

    // then
    assertThat(store.get("empty")).hasValue(new byte[0]);
    assertThat(store.head("empty")).get().extracting(StoredObject::size).isEqualTo(0L);
    assertThat(store.getRange("empty", 0, 10)).isEmpty();
    assertThat(store.list("", null, 10)).extracting(StoredObject::key).containsExactly("empty");
  }

  @Test
  void shouldGetNothingOfAMissingObjectOrOfADirectory() throws IOException {
    // given
    store.put("a/b/c.json", bytes("x"));

    // when, then
    assertThat(store.get("missing.json")).isEmpty();
    assertThat(store.get("a/b")).isEmpty();
    assertThat(store.get("a/b/c.json/d")).isEmpty();
    assertThat(store.head("a/b")).isEmpty();
  }

  @Test
  void shouldReadARangeOfAnObject() throws IOException {
    // given
    store.put("range", bytes("0123456789"));

    // when, then
    assertThat(store.getRange("range", 2, 3)).isEqualTo(bytes("234"));
    assertThat(store.getRange("range", 0, 10)).isEqualTo(bytes("0123456789"));
    assertThat(store.getRange("range", 9, 1)).isEqualTo(bytes("9"));
  }

  @Test
  void shouldClipARangeAtTheEndOfTheObject() throws IOException {
    // given
    store.put("range", bytes("0123456789"));

    // when, then
    assertThat(store.getRange("range", 7, 100)).isEqualTo(bytes("789"));
    assertThat(store.getRange("range", 0, Integer.MAX_VALUE)).isEqualTo(bytes("0123456789"));
  }

  @Test
  void shouldReadNothingOfARangeAtOrAfterTheEnd() throws IOException {
    // given
    store.put("range", bytes("0123456789"));

    // when, then
    assertThat(store.getRange("range", 10, 5)).isEmpty();
    assertThat(store.getRange("range", 11, 5)).isEmpty();
    assertThat(store.getRange("range", 1L << 40, 5)).isEmpty();
  }

  @Test
  void shouldReadNothingOfAnEmptyRange() throws IOException {
    // given
    store.put("range", bytes("0123456789"));

    // when, then
    assertThat(store.getRange("range", 3, 0)).isEmpty();
    assertThatThrownBy(() -> store.getRange("missing", 3, 0))
        .isInstanceOf(NoSuchObjectException.class);
  }

  @Test
  void shouldFailARangeOfAMissingObject() {
    // when, then
    assertThatThrownBy(() -> store.getRange("missing", 0, 10))
        .isInstanceOfSatisfying(
            NoSuchObjectException.class, e -> assertThat(e.key()).isEqualTo("missing"))
        .isInstanceOf(IOException.class);
  }

  @Test
  void shouldRejectARangeWithANegativeOffsetOrLength() throws IOException {
    // given
    store.put("range", bytes("0123456789"));

    // when, then
    assertThatThrownBy(() -> store.getRange("range", -1, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> store.getRange("range", 0, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldHeadAnObjectsSizeAndModificationTime() throws IOException {
    // given
    final var before = Instant.now().minusSeconds(60);

    // when
    store.put("a/head.json", bytes("12345"));

    // then
    final var head = store.head("a/head.json");
    assertThat(head).isPresent();
    assertThat(head.get().key()).isEqualTo("a/head.json");
    assertThat(head.get().size()).isEqualTo(5);
    assertThat(head.get().lastModified()).isBetween(before, Instant.now().plusSeconds(60));
    assertThat(store.head("a/other.json")).isEmpty();
  }

  @Test
  void shouldListKeysInTheOrderOfTheirBytes() throws IOException {
    // given keys that differ in the character after a shared start, and in a directory or not
    putAll("b/c", "a0", "a/z", "a.json", "a/b", "a-1");

    // when
    final var keys = keys(store.list("", null, 100));

    // then '-' sorts before '.', which sorts before '/', which sorts before '0'
    assertThat(keys).containsExactly("a-1", "a.json", "a/b", "a/z", "a0", "b/c");
  }

  @Test
  void shouldListOnlyKeysThatStartWithThePrefix() throws IOException {
    // given
    putAll("b/c", "a0", "a/z", "a.json", "a/b", "a-1", "ab/x");

    // when, then
    assertThat(keys(store.list("a/", null, 100))).containsExactly("a/b", "a/z");
    assertThat(keys(store.list("a", null, 100)))
        .containsExactly("a-1", "a.json", "a/b", "a/z", "a0", "ab/x");
    assertThat(keys(store.list("a.", null, 100))).containsExactly("a.json");
    assertThat(keys(store.list("a/b", null, 100))).containsExactly("a/b");
    assertThat(keys(store.list("ab/", null, 100))).containsExactly("ab/x");
    assertThat(keys(store.list("c", null, 100))).isEmpty();
    assertThat(keys(store.list("a/z/", null, 100))).isEmpty();
  }

  @Test
  void shouldListWithAPartialNameInADirectory() throws IOException {
    // given segment names that start with 20-digit indexes
    final var dir = "c/t/3/";
    putAll(
        dir + "%020d-a.seg".formatted(1),
        dir + "%020d-b.seg".formatted(12),
        dir + "%020d-c.seg".formatted(13),
        dir + "%020d-d.seg".formatted(120),
        dir + "retention/x.json");

    // when the prefix is the start of the names of indexes 10 to 19
    final var keys = keys(store.list(dir + "%019d".formatted(1), null, 100));

    // then
    assertThat(keys)
        .containsExactly(dir + "%020d-b.seg".formatted(12), dir + "%020d-c.seg".formatted(13));
  }

  @Test
  void shouldListAfterAKey() throws IOException {
    // given
    putAll("b/c", "a0", "a/z", "a.json", "a/b", "a-1");

    // when, then
    assertThat(keys(store.list("", "a.json", 100))).containsExactly("a/b", "a/z", "a0", "b/c");
    assertThat(keys(store.list("", "a/b", 100))).containsExactly("a/z", "a0", "b/c");
    assertThat(keys(store.list("", "b/c", 100))).isEmpty();
    assertThat(keys(store.list("", "z", 100))).isEmpty();
  }

  @Test
  void shouldListAfterAKeyThatIsNotThere() throws IOException {
    // given
    putAll("b/c", "a0", "a/z", "a.json", "a/b", "a-1");

    // when, then
    assertThat(keys(store.list("", "a/c", 100))).containsExactly("a/z", "a0", "b/c");
    assertThat(keys(store.list("", "a/", 100))).containsExactly("a/b", "a/z", "a0", "b/c");
    assertThat(keys(store.list("", "a", 100)))
        .containsExactly("a-1", "a.json", "a/b", "a/z", "a0", "b/c");
    assertThat(keys(store.list("", "", 100)))
        .containsExactly("a-1", "a.json", "a/b", "a/z", "a0", "b/c");
  }

  @Test
  void shouldListAfterAKeyWithinAPrefix() throws IOException {
    // given
    putAll("b/c", "a0", "a/z", "a.json", "a/b", "a-1");

    // when, then
    assertThat(keys(store.list("a/", "a/b", 100))).containsExactly("a/z");
    assertThat(keys(store.list("a/", "0", 100))).containsExactly("a/b", "a/z");
    assertThat(keys(store.list("a/", "a0", 100))).isEmpty();
    assertThat(keys(store.list("a/", "a-1", 100))).containsExactly("a/b", "a/z");
  }

  @Test
  void shouldListAtMostTheLimit() throws IOException {
    // given
    putAll("b/c", "a0", "a/z", "a.json", "a/b", "a-1");

    // when, then
    assertThat(keys(store.list("", null, 2))).containsExactly("a-1", "a.json");
    assertThat(keys(store.list("", "a.json", 2))).containsExactly("a/b", "a/z");
    assertThat(keys(store.list("", null, 6))).hasSize(6);
    assertThat(keys(store.list("", null, 1_000))).hasSize(6);
    assertThat(store.list("", null, 0)).isEmpty();
    assertThatThrownBy(() -> store.list("", null, -1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldListEveryObjectWhenPagedByTheLastKey() throws IOException {
    // given
    final var expected = new ArrayList<String>();
    for (int i = 0; i < 25; i++) {
      final var key = "p/%03d/object".formatted(i);
      expected.add(key);
      store.put(key, bytes("x"));
    }

    // when
    final var found = new ArrayList<String>();
    String after = null;
    while (true) {
      final var page = store.list("p/", after, 10);
      found.addAll(keys(page));
      if (page.size() < 10) {
        break;
      }
      after = page.getLast().key();
    }

    // then
    assertThat(found).isEqualTo(expected);
  }

  @Test
  void shouldListTheSizeOfEachObject() throws IOException {
    // given
    store.put("s/one", bytes("1"));
    store.put("s/three", bytes("333"));

    // when
    final var objects = store.list("s/", null, 10);

    // then
    assertThat(objects)
        .extracting(StoredObject::key, StoredObject::size)
        .containsExactly(tuple("s/one", 1L), tuple("s/three", 3L));
    assertThat(objects).allSatisfy(object -> assertThat(object.lastModified()).isNotNull());
  }

  @Test
  void shouldListThePrefixesDirectlyBelowAPrefix() throws IOException {
    // given
    putAll("x/a/1", "x/a/2", "x/b/c/3", "x/d.json", "y/e");

    // when, then
    assertThat(store.listPrefixes("x/")).containsExactly("x/a/", "x/b/");
    assertThat(store.listPrefixes("")).containsExactly("x/", "y/");
    assertThat(store.listPrefixes("x/b/")).containsExactly("x/b/c/");
    assertThat(store.listPrefixes("x/a/")).isEmpty();
    assertThat(store.listPrefixes("z/")).isEmpty();
  }

  @Test
  void shouldSortPrefixesLikeKeys() throws IOException {
    // given
    putAll("a0/x", "a/x", "a-b/x", "a.b/x");

    // when, then
    assertThat(store.listPrefixes("")).containsExactly("a-b/", "a.b/", "a/", "a0/");
  }

  @Test
  void shouldNotListAPrefixOnceItsLastObjectIsDeleted() throws IOException {
    // given
    putAll("x/a/1", "x/a/deeper/2", "x/b/3");

    // when
    store.delete("x/a/1");
    store.delete("x/a/deeper/2");

    // then
    assertThat(store.listPrefixes("x/")).containsExactly("x/b/");
    assertThat(store.listPrefixes("")).containsExactly("x/");
  }

  @Test
  void shouldRejectAPrefixListingWithoutATrailingSlash() {
    // when, then
    assertThatThrownBy(() -> store.listPrefixes("x"))
        .isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> store.listPrefixes("/"))
        .isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> store.listPrefixes("x//"))
        .isInstanceOf(InvalidObjectNameException.class);
  }

  @Test
  void shouldDeleteAnObject() throws IOException {
    // given
    putAll("a/1", "a/2");

    // when
    store.delete("a/1");

    // then
    assertThat(store.get("a/1")).isEmpty();
    assertThat(store.head("a/1")).isEmpty();
    assertThat(keys(store.list("", null, 10))).containsExactly("a/2");
  }

  @Test
  void shouldDeleteAnObjectThatIsNotThere() throws IOException {
    // given
    store.put("a/1", bytes("x"));

    // when
    store.delete("a/missing");
    store.delete("missing/deeper/still");
    store.delete("a");

    // then
    assertThat(keys(store.list("", null, 10))).containsExactly("a/1");
  }

  @Test
  void shouldPutAgainAfterADelete() throws IOException {
    // given
    store.put("a/1", bytes("first"));
    store.delete("a/1");

    // when
    store.put("a/1", bytes("second"));

    // then
    assertThat(store.get("a/1")).hasValue(bytes("second"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "/a", "a/", "a//b", "a/./b", "a/../b", "../a", ".", "..", "a/.."})
  void shouldRejectAKeyThatIsNotOneOfNonEmptySegments(final String key) {
    // when, then
    assertThatThrownBy(() -> store.put(key, bytes("x")))
        .isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> store.get(key)).isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> store.head(key)).isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> store.getRange(key, 0, 1))
        .isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> store.delete(key)).isInstanceOf(InvalidObjectNameException.class);
  }

  @Test
  void shouldKeepTheObjectsOfPercentEncodedNames() throws IOException {
    // given clusters and tenants whose names are not plain
    final var ulid = Ulid.next();
    final var keys =
        List.of(
            ObjectNames.segment(new LogId("prod/eu", "default", 3), HISTORY, 1, 40, 0, ulid),
            ObjectNames.segment(new LogId("prod/eu", "tenant one", 3), HISTORY, 1, 40, 0, ulid),
            ObjectNames.segment(new LogId("prod/eu", "tenant%one", 3), HISTORY, 1, 40, 0, ulid),
            ObjectNames.segment(new LogId("prod/eu", "größe", 3), HISTORY, 41, 80, 1, ulid),
            ObjectNames.segment(new LogId("..", "..", 3), HISTORY, 1, 40, 0, ulid),
            ObjectNames.historyPart(new LogId("prod/eu", "default", 3), HISTORY));
    for (final var key : keys) {
      store.put(key, bytes(key));
    }

    // when
    final var clusters = store.listPrefixes("");
    final var tenants = store.listPrefixes(ObjectNames.clusterPrefix("prod/eu"));
    final var segments =
        keys(
            store.list(
                ObjectNames.segmentsPrefix(new LogId("prod/eu", "tenant one", 3), HISTORY),
                null,
                10));

    // then
    assertThat(clusters.stream().map(prefix -> decodeBelow("", prefix)))
        .containsExactlyInAnyOrder("prod/eu", "..");
    assertThat(
            tenants.stream()
                .map(prefix -> decodeBelow(ObjectNames.clusterPrefix("prod/eu"), prefix)))
        .containsExactlyInAnyOrder("default", "tenant one", "tenant%one", "größe");
    assertThat(segments)
        .containsExactly(
            ObjectNames.segment(new LogId("prod/eu", "tenant one", 3), HISTORY, 1, 40, 0, ulid));
    for (final var key : keys) {
      assertThat(store.get(key)).hasValue(bytes(key));
    }
    assertThat(ObjectNames.parseSegment(segments.getFirst()).lastIndex()).isEqualTo(40);
  }

  @Test
  void shouldListASegmentsDirectoryInLogOrder() throws IOException {
    // given segments put out of order
    final var first = ObjectNames.segment(LOG, HISTORY, 1, 40, 0, Ulid.next());
    final var second = ObjectNames.segment(LOG, HISTORY, 41, 99, 0, Ulid.next());
    final var third = ObjectNames.segment(LOG, HISTORY, 100, 120, 0, Ulid.next());
    final var rollup = ObjectNames.segment(LOG, HISTORY, 1, 99, 1, Ulid.next());
    for (final var key : List.of(third, rollup, first, second)) {
      store.put(key, bytes("x"));
    }

    // when
    final var listed = keys(store.list(ObjectNames.segmentsPrefix(LOG, HISTORY), null, 10));

    // then names sort by first index, and by last index of the same first
    assertThat(listed).containsExactly(first, rollup, second, third);
    assertThat(keys(store.list(ObjectNames.segmentsPrefix(LOG, HISTORY), first, 10)))
        .containsExactly(rollup, second, third);
  }

  protected void putAll(final String... keys) throws IOException {
    for (final var key : keys) {
      store.put(key, bytes(key));
    }
  }

  /** The name that an encoded directory below a parent directory has. */
  private static String decodeBelow(final String parent, final String directory) {
    return ObjectNames.decode(directory.substring(parent.length(), directory.length() - 1));
  }

  protected static byte[] bytes(final String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  protected static List<String> keys(final List<StoredObject> objects) {
    return objects.stream().map(StoredObject::key).toList();
  }

  private static byte[] slice(final byte[] content, final int offset, final int length) {
    return Arrays.copyOfRange(content, offset, offset + length);
  }
}
