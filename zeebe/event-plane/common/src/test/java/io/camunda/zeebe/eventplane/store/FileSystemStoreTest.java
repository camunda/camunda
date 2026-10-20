/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FileSystemStoreTest extends ObjectStoreContractTest {

  @TempDir private Path directory;

  @Override
  protected ObjectStore openStore() {
    return new FileSystemStore(directory.resolve("store"));
  }

  @Test
  void shouldHaveAFileUriAsItsRoot() {
    // when
    final var root = store.root();

    // then
    assertThat(root.getScheme()).isEqualTo("file");
    assertThat(root.toString()).startsWith("file:///").endsWith("/store/");
  }

  @Test
  void shouldKeepAnObjectAsAFileAtItsKey() throws IOException {
    // when
    store.put("c/t/3/a.json", bytes("content"));

    // then
    assertThat(directory.resolve("store/c/t/3/a.json")).hasContent("content");
  }

  @Test
  void shouldLeaveNoTemporaryFileAfterAPut() throws IOException {
    // when
    store.put("c/t/3/a.json", bytes("content"));
    store.put("c/t/3/a.json", bytes("replaced"));

    // then
    try (final Stream<Path> files = Files.list(directory.resolve("store/c/t/3"))) {
      assertThat(files.map(path -> path.getFileName().toString())).containsExactly("a.json");
    }
  }

  @Test
  void shouldNotListTemporaryFiles() throws IOException {
    // given a put that is still writing, or that a crash interrupted
    store.put("c/t/3/a.json", bytes("content"));
    Files.writeString(
        directory.resolve("store/c/t/3/" + FileSystemStore.TEMPORARY_PREFIX + "interrupted"), "x");
    Files.createDirectories(directory.resolve("store/c/t/9"));
    Files.writeString(
        directory.resolve("store/c/t/9/" + FileSystemStore.TEMPORARY_PREFIX + "only"), "x");

    // when, then
    assertThat(keys(store.list("", null, 10))).containsExactly("c/t/3/a.json");
    assertThat(store.listPrefixes("c/t/")).containsExactly("c/t/3/");
  }

  @Test
  void shouldCreateItsRootOnTheFirstPut() throws IOException {
    // given
    final var root = directory.resolve("not/there/yet");
    final var other = new FileSystemStore(root);

    // when, then
    assertThat(other.list("", null, 10)).isEmpty();
    assertThat(other.listPrefixes("")).isEmpty();
    assertThat(other.get("a")).isEmpty();
    other.put("a", bytes("x"));
    assertThat(root.resolve("a")).hasContent("x");
  }

  @Test
  void shouldShowAReaderTheOldContentOrTheNewNeverPartOfIt() throws Exception {
    // given two contents that differ in every byte, and that take long to write
    final var old = new byte[4_000_000];
    final var replacement = new byte[4_000_000];
    Arrays.fill(old, (byte) 'o');
    Arrays.fill(replacement, (byte) 'n');
    store.put("object", old);
    final var done = new AtomicBoolean();
    final var torn = new AtomicReference<String>();
    final var reader =
        new Thread(
            () -> {
              try {
                while (!done.get()) {
                  final var content = store.get("object").orElseThrow();
                  final var first = content[0];
                  if (content.length != old.length
                      || (first != 'o' && first != 'n')
                      || content[content.length - 1] != first
                      || content[content.length / 2] != first) {
                    torn.set(
                        "Read %d bytes from %c to %c"
                            .formatted(content.length, first, content[content.length - 1]));
                    return;
                  }
                }
              } catch (final IOException e) {
                torn.set(e.toString());
              }
            });
    reader.start();

    // when
    for (int i = 0; i < 20; i++) {
      store.put("object", i % 2 == 0 ? replacement : old);
    }
    done.set(true);
    reader.join();

    // then
    assertThat(torn.get()).isNull();
  }
}
