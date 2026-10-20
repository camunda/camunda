/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.eventplane.snapshot.SnapshotDescriptor.SnapshotFile;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.StoredObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SnapshotArchiveTest {

  private static final String ID = "10-1-40-30-0-abc";
  private static final UUID HISTORY = UUID.fromString("00000000-0000-4000-8000-000000000001");

  @TempDir private Path bucket;
  @TempDir private Path snapshot;
  @TempDir private Path downloads;
  private FileSystemStore store;
  private Map<String, Path> files;

  @BeforeEach
  void setUp() throws IOException {
    store = new FileSystemStore(bucket);
    files =
        Map.of(
            "000001.sst",
            write("000001.sst", "state"),
            ID + ".checksum",
            write(ID + ".checksum", "sum"));
  }

  @Test
  void shouldPutTheDescriptorAfterTheFiles() throws IOException {
    // given
    final var descriptor = descriptor(5, 3);

    // when
    SnapshotArchive.upload(store, "c", "default", 1, descriptor, files);

    // then
    final var objects = SnapshotArchive.objects(store, "c", "default", 1, ID);
    assertThat(objects)
        .extracting(StoredObject::key)
        .containsExactlyInAnyOrder(
            ObjectNames.snapshotFile("c", "default", 1, ID, "000001.sst"),
            ObjectNames.snapshotFile("c", "default", 1, ID, ID + ".checksum"),
            ObjectNames.snapshotDescriptor("c", "default", 1, ID));
    assertThat(SnapshotArchive.list(store, "c", "default", 1))
        .isEqualTo(new SnapshotListing(List.of(descriptor), List.of()));
  }

  @Test
  void shouldGetTheFilesBack() throws IOException {
    // given
    final var descriptor = descriptor(5, 3);
    SnapshotArchive.upload(store, "c", "default", 1, descriptor, files);

    // when
    final var downloaded =
        SnapshotArchive.download(store, "c", "default", 1, descriptor, downloads);

    // then
    assertThat(downloaded).containsOnlyKeys("000001.sst", ID + ".checksum");
    assertThat(downloaded.get("000001.sst")).hasContent("state");
    assertThat(downloaded.get(ID + ".checksum")).hasContent("sum");
  }

  @Test
  void shouldNotUploadAFileOfAnotherSizeThanTheDescriptorSays() {
    // given
    final var descriptor = descriptor(6, 3);

    // when, then
    assertThatThrownBy(() -> SnapshotArchive.upload(store, "c", "default", 1, descriptor, files))
        .isInstanceOf(IncompleteSnapshotException.class)
        .hasMessage(
            "Expected file '000001.sst' of snapshot %s to have 6 bytes, but got 5 bytes"
                .formatted(ID));
  }

  @Test
  void shouldFailToDownloadAMissingFile() throws IOException {
    // given
    final var descriptor = descriptor(5, 3);
    SnapshotArchive.upload(store, "c", "default", 1, descriptor, files);
    store.delete(ObjectNames.snapshotFile("c", "default", 1, ID, "000001.sst"));

    // when, then
    assertThatThrownBy(
            () -> SnapshotArchive.download(store, "c", "default", 1, descriptor, downloads))
        .isInstanceOf(IncompleteSnapshotException.class)
        .hasMessage(
            "Expected file '000001.sst' of snapshot %s to have 5 bytes, but got no such file"
                .formatted(ID));
  }

  @Test
  void shouldListADirectoryWithoutDescriptorApart() throws IOException {
    // given: an upload that stopped before its descriptor
    store.put(
        ObjectNames.snapshotFile("c", "default", 1, ID, "000001.sst"),
        "state".getBytes(StandardCharsets.UTF_8));

    // when
    final var listing = SnapshotArchive.list(store, "c", "default", 1);

    // then
    assertThat(listing).isEqualTo(new SnapshotListing(List.of(), List.of(ID)));
  }

  @Test
  void shouldDeleteTheDescriptorAndTheFiles() throws IOException {
    // given
    SnapshotArchive.upload(store, "c", "default", 1, descriptor(5, 3), files);

    // when
    SnapshotArchive.delete(store, "c", "default", 1, ID);

    // then
    assertThat(SnapshotArchive.objects(store, "c", "default", 1, ID)).isEmpty();
    assertThat(SnapshotArchive.list(store, "c", "default", 1))
        .isEqualTo(new SnapshotListing(List.of(), List.of()));
  }

  private Path write(final String name, final String content) throws IOException {
    return Files.writeString(snapshot.resolve(name), content);
  }

  private static SnapshotDescriptor descriptor(final long sstSize, final long checksumSize) {
    return new SnapshotDescriptor(
        1,
        ID,
        10,
        1,
        40,
        30,
        35,
        42,
        HISTORY,
        List.of(
            new SnapshotFile("000001.sst", sstSize),
            new SnapshotFile(ID + ".checksum", checksumSize)),
        Instant.parse("2026-10-20T12:00:00Z"));
  }
}
