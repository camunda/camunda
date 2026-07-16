/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The single durability seam of a cleaner pass: it publishes a new clean set (manifest + its
 * segment files) as one atomic step and returns the currently-committed manifest. Everything a pass
 * does before {@link #commit} is reconstructible garbage — a crash before commit leaves the
 * previous manifest authoritative and the pass simply reruns (ADR 0001, decision 6.4).
 *
 * <h3>Why an injectable seam rather than a direct Raft snapshot write</h3>
 *
 * <p>In the final design (ADR 0001) the clean set <em>is</em> the partition's Raft snapshot, and
 * persisting a snapshot immediately triggers Raft log truncation up to the snapshot index. But
 * truncating the raw log below the cleaner point C without the composite fetch reader in place
 * would make positions ≤ C unreadable — the reader still resolves them against the live log.
 * Snapshot commit, the composite fetch reader, and the InstallSnapshot/bootstrap install path
 * therefore must land together (step 5, deliberately out of scope for this library). Until then the
 * commit point is this seam, whose default {@link FileManifestStore} persists the manifest as a
 * plain atomically- renamed file. Step 5 adapts this same interface onto the Raft snapshot store.
 *
 * <p>Threading: implementations are driven by the single cleaner actor; they need not be
 * thread-safe.
 */
public interface ManifestStore {

  /**
   * Atomically publishes a new committed manifest. The {@code newFiles} — the segment files the
   * manifest references that were written during this pass — must already be durably on disk;
   * implementations may additionally fsync or hardlink them (step 5 hardlinks them into the
   * snapshot directory). After a successful return, {@link #latest()} reflects this manifest.
   *
   * @param manifest the manifest to commit
   * @param newFiles the segment files newly written by this pass
   */
  void commit(CompactionManifest manifest, List<Path> newFiles);

  /**
   * Returns the currently-committed manifest, or {@link Optional#empty()} if the partition has
   * never been cleaned. A committed manifest that references a missing, wrong-length, or
   * checksum-mismatched segment file fails loudly rather than being returned —
   * derived-state-on-disk bugs must never be papered over.
   *
   * @return the committed manifest, if any
   */
  Optional<CompactionManifest> latest();
}
