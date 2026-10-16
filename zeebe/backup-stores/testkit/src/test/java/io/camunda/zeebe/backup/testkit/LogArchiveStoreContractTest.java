/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behavior every {@link LogArchiveStore} implementation must provide. */
public abstract class LogArchiveStoreContractTest {

  /** The store under test; must be empty and isolated from other tests' keys. */
  protected abstract LogArchiveStore store();

  private static String newPrefix() {
    return "log-archive-" + UUID.randomUUID() + "/";
  }

  @Test
  void shouldReadBackWhatWasPut() {
    // given
    final var key = newPrefix() + "commits/1.json";
    final var content = "hello".getBytes(StandardCharsets.UTF_8);

    // when
    store().put(key, content).join();

    // then
    assertThat(store().getIfExists(key).join()).hasValue(content);
  }

  @Test
  void shouldReturnEmptyForMissingKey() {
    assertThat(store().getIfExists(newPrefix() + "missing").join()).isEmpty();
  }

  @Test
  void shouldOverwriteExistingKey() {
    // given
    final var key = newPrefix() + "root/_latest.json";
    store().put(key, new byte[] {1, 2, 3}).join();

    // when
    store().put(key, new byte[] {4}).join();

    // then
    assertThat(store().getIfExists(key).join()).hasValue(new byte[] {4});
  }

  @Test
  void shouldStoreEmptyObject() {
    // given
    final var key = newPrefix() + "empty";

    // when
    store().put(key, new byte[0]).join();

    // then
    assertThat(store().getIfExists(key).join()).hasValue(new byte[0]);
  }

  @Test
  void shouldReadExactRange() {
    // given
    final var key = newPrefix() + "log/1-100.log";
    final var content =
        IntStream.range(0, 256)
            .collect(java.io.ByteArrayOutputStream::new, (o, i) -> o.write(i), (a, b) -> {})
            .toByteArray();
    store().put(key, content).join();

    // when
    final var range = store().getRange(key, 10, 20).join();

    // then
    assertThat(range).isEqualTo(java.util.Arrays.copyOfRange(content, 10, 30));
  }

  @Test
  void shouldReturnAvailableBytesWhenRangeExceedsObject() {
    // given
    final var key = newPrefix() + "short";
    store().put(key, new byte[] {1, 2, 3, 4, 5}).join();

    // when
    final var range = store().getRange(key, 3, 100).join();

    // then
    assertThat(range).containsExactly(4, 5);
  }

  @Test
  void shouldReturnEmptyWhenRangeStartsPastEnd() {
    // given
    final var key = newPrefix() + "short";
    store().put(key, new byte[] {1, 2, 3}).join();

    // when
    final var range = store().getRange(key, 10, 5).join();

    // then
    assertThat(range).isEmpty();
  }

  @Test
  void shouldFailRangeReadOfMissingKey() {
    assertThatThrownBy(() -> store().getRange(newPrefix() + "missing", 0, 10).join())
        .isInstanceOf(java.util.concurrent.CompletionException.class);
  }

  @Test
  void shouldDownloadToFile(@TempDir final Path dir) throws IOException {
    // given
    final var key = newPrefix() + "state/snap/file";
    final var content = "snapshot-bytes".getBytes(StandardCharsets.UTF_8);
    store().put(key, content).join();
    final var target = dir.resolve("downloaded");

    // when
    store().getToFile(key, target).join();

    // then
    assertThat(Files.readAllBytes(target)).isEqualTo(content);
  }

  @Test
  void shouldReplaceExistingTargetOnDownload(@TempDir final Path dir) throws IOException {
    // given
    final var key = newPrefix() + "file";
    store().put(key, new byte[] {7}).join();
    final var target = dir.resolve("downloaded");
    Files.write(target, new byte[] {1, 2, 3, 4});

    // when
    store().getToFile(key, target).join();

    // then
    assertThat(Files.readAllBytes(target)).containsExactly(7);
  }

  @Test
  void shouldUploadFile(@TempDir final Path dir) throws IOException {
    // given
    final var key = newPrefix() + "state/snap/chunk";
    final var source = dir.resolve("source");
    final var content = new byte[100_000];
    new java.util.Random(1).nextBytes(content);
    Files.write(source, content);

    // when
    store().putFile(key, source).join();

    // then
    assertThat(store().getIfExists(key).join()).hasValue(content);
  }

  @Test
  void shouldListKeysByPrefixSorted() {
    // given
    final var prefix = newPrefix();
    final var other = newPrefix();
    store().put(prefix + "b/2", new byte[1]).join();
    store().put(prefix + "a/1", new byte[1]).join();
    store().put(prefix + "a/10", new byte[1]).join();
    store().put(prefix + "c", new byte[1]).join();
    store().put(other + "a/1", new byte[1]).join();

    // when
    final var all = store().list(prefix).join();
    final var nested = store().list(prefix + "a/").join();

    // then
    assertThat(all).containsExactly(prefix + "a/1", prefix + "a/10", prefix + "b/2", prefix + "c");
    assertThat(nested).containsExactly(prefix + "a/1", prefix + "a/10");
  }

  @Test
  void shouldListNothingForUnknownPrefix() {
    assertThat(store().list(newPrefix()).join()).isEmpty();
  }

  @Test
  void shouldListMoreKeysThanOnePage() {
    // given
    final var prefix = newPrefix();
    final var expected = new ArrayList<String>();
    final var futures =
        IntStream.range(0, 1100)
            .mapToObj(
                i -> {
                  final var key = prefix + "commits/%05d.json".formatted(i);
                  expected.add(key);
                  return store().put(key, new byte[] {1});
                })
            .toList();
    futures.forEach(java.util.concurrent.CompletableFuture::join);
    Collections.sort(expected);

    // when
    final List<String> listed = store().list(prefix).join();

    // then
    assertThat(listed).containsExactlyElementsOf(expected);
  }
}
