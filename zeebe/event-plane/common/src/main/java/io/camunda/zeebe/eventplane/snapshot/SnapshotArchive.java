/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.snapshot;

import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.snapshot.SnapshotDescriptor.SnapshotFile;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.eventplane.store.StoredObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A log's snapshots in the store, each under {@link ObjectNames#snapshotPrefix}: its files, and its
 * descriptor. The descriptor is written after every file and deleted before any, so a descriptor in
 * the store means that all of the snapshot's files are there.
 */
public final class SnapshotArchive {

  /** The name of a snapshot's descriptor in its directory. */
  public static final String DESCRIPTOR = "descriptor.json";

  private static final int PAGE_SIZE = 1000;

  private SnapshotArchive() {}

  /**
   * Puts the snapshot's files, then its descriptor.
   *
   * @param files the files by name, the names the descriptor has
   * @throws IllegalArgumentException if the files' names aren't the descriptor's
   * @throws IncompleteSnapshotException if a file's size isn't what the descriptor says
   */
  public static void upload(
      final ObjectStore store,
      final LogId log,
      final SnapshotDescriptor descriptor,
      final Map<String, Path> files)
      throws IOException {
    final var names =
        descriptor.files().stream().map(SnapshotFile::name).collect(Collectors.toSet());
    if (!names.equals(files.keySet())) {
      throw new IllegalArgumentException(
          "Expected the files of snapshot %s to be %s, as its descriptor says, but got %s"
              .formatted(descriptor.snapshotId(), names, files.keySet()));
    }
    for (final var file : descriptor.files()) {
      final var content = Files.readAllBytes(Objects.requireNonNull(files.get(file.name())));
      if (content.length != file.size()) {
        throw new IncompleteSnapshotException(
            descriptor.snapshotId(), file.name(), file.size(), content.length);
      }
      store.put(ObjectNames.snapshotFile(log, descriptor.snapshotId(), file.name()), content);
    }
    store.put(ObjectNames.snapshotDescriptor(log, descriptor.snapshotId()), descriptor.toJson());
  }

  /**
   * The descriptor of a snapshot, if the store has it.
   *
   * @throws InvalidSnapshotDescriptorException if it isn't a valid descriptor
   */
  public static Optional<SnapshotDescriptor> descriptor(
      final ObjectStore store, final LogId log, final String snapshotId) throws IOException {
    final var content = store.get(ObjectNames.snapshotDescriptor(log, snapshotId));
    return content.isPresent()
        ? Optional.of(SnapshotDescriptor.fromJson(content.get()))
        : Optional.empty();
  }

  /**
   * The log's snapshots: those with a descriptor, and the directories without one.
   *
   * @throws InvalidSnapshotDescriptorException if a descriptor isn't valid, or names another
   *     snapshot than its directory
   */
  public static SnapshotListing list(final ObjectStore store, final LogId log) throws IOException {
    final var prefix = ObjectNames.snapshotsPrefix(log);
    final var complete = new ArrayList<SnapshotDescriptor>();
    final var withoutDescriptor = new ArrayList<String>();
    for (final var directory : store.listPrefixes(prefix)) {
      final var snapshotId =
          ObjectNames.decode(directory.substring(prefix.length(), directory.length() - 1));
      final var descriptor = descriptor(store, log, snapshotId);
      if (descriptor.isEmpty()) {
        withoutDescriptor.add(snapshotId);
      } else if (!descriptor.get().snapshotId().equals(snapshotId)) {
        throw new InvalidSnapshotDescriptorException(
            "snapshotId",
            "'%s', as the directory says".formatted(snapshotId),
            "'%s'".formatted(descriptor.get().snapshotId()));
      } else {
        complete.add(descriptor.get());
      }
    }
    return new SnapshotListing(complete, withoutDescriptor);
  }

  /**
   * Gets the snapshot's files into a directory, and checks their sizes.
   *
   * @return the files by name, as the snapshot store's restore takes them
   * @throws IncompleteSnapshotException if a file is missing or of another size
   */
  public static Map<String, Path> download(
      final ObjectStore store,
      final LogId log,
      final SnapshotDescriptor descriptor,
      final Path directory)
      throws IOException {
    Files.createDirectories(directory);
    final var files = new HashMap<String, Path>();
    for (final var file : descriptor.files()) {
      final var content =
          store.get(ObjectNames.snapshotFile(log, descriptor.snapshotId(), file.name()));
      if (content.isEmpty() || content.get().length != file.size()) {
        throw new IncompleteSnapshotException(
            descriptor.snapshotId(),
            file.name(),
            file.size(),
            content.map(bytes -> (long) bytes.length).orElse(IncompleteSnapshotException.MISSING));
      }
      final var target = directory.resolve(file.name());
      Files.write(target, content.get());
      files.put(file.name(), target);
    }
    return files;
  }

  /**
   * Deletes a snapshot: its descriptor first, then its files, so that no reader finds a descriptor
   * without them. A deletion that stops in between leaves files without a descriptor, which {@link
   * #deleteFiles} removes.
   */
  public static void delete(final ObjectStore store, final LogId log, final String snapshotId)
      throws IOException {
    store.delete(ObjectNames.snapshotDescriptor(log, snapshotId));
    deleteFiles(store, log, snapshotId);
  }

  /** Deletes every object in a snapshot's directory. */
  public static void deleteFiles(final ObjectStore store, final LogId log, final String snapshotId)
      throws IOException {
    for (final var object : objects(store, log, snapshotId)) {
      store.delete(object.key());
    }
  }

  /** The objects in a snapshot's directory, its descriptor among them if it has one. */
  public static List<StoredObject> objects(
      final ObjectStore store, final LogId log, final String snapshotId) throws IOException {
    final var prefix = ObjectNames.snapshotPrefix(log, snapshotId);
    final var objects = new ArrayList<StoredObject>();
    String startAfter = null;
    while (true) {
      final var page = store.list(prefix, startAfter, PAGE_SIZE);
      objects.addAll(page);
      if (page.size() < PAGE_SIZE) {
        return objects;
      }
      startAfter = page.getLast().key();
    }
  }
}
