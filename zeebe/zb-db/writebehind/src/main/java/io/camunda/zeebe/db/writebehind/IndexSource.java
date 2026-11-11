/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.DbValue;
import org.jspecify.annotations.Nullable;

/**
 * Walks the keys of a loaded {@link KeyIndex} and reads values through the store, so a scan opens
 * no storage iterator. A value is read only when {@link #readValueUnlessStale} asks for it: dirty
 * index, then cache, then one storage read.
 *
 * <p>The source reads the live key set and checks the index version after each key and each value
 * read, instead of working on a fixed copy. When the version changed, or after {@link
 * #MAX_STORAGE_READS} storage reads, it turns {@link #isStale() stale} and the cursor continues
 * with a storage view. One iterator is cheaper than many random reads.
 *
 * <p>The guarantee is weaker than a storage iterator's single instant, but only while another
 * context adds or deletes keys of this column family during a scan: then the scan can return a key
 * set of before and after that commit. Every returned entry was committed, none is returned twice
 * and none is skipped. The stream processor is the only writer of the engine's column families and
 * doesn't commit during its own scans, and transactions already see commits that were made after
 * they started.
 */
final class IndexSource implements CommittedSource {

  /** More storage reads than this in one scan are slower than one storage iterator. */
  static final int MAX_STORAGE_READS = 4;

  private final WriteBehindStore store;
  private final KeyIndex index;
  private final long version;

  private boolean forward = true;
  private @Nullable RawKey head;
  private boolean stale;
  private int storageReads;

  IndexSource(final WriteBehindStore store, final KeyIndex index, final long version) {
    this.store = store;
    this.index = index;
    this.version = version;
  }

  @Override
  public void seek(final byte[] key) {
    forward = true;
    head = index.keys().ceiling(RawKey.copyOf(key));
    verify();
  }

  @Override
  public void seekForPrev(final byte[] key) {
    forward = false;
    head = index.keys().floor(RawKey.copyOf(key));
    verify();
  }

  @Override
  public boolean isValid() {
    return !stale && head != null;
  }

  @Override
  public byte[] key() {
    final var current = head;
    if (stale || current == null) {
      throw new IllegalStateException("Expected the source to be valid, but it is not");
    }
    return current.bytes();
  }

  @Override
  public void advance() {
    final var current = head;
    if (stale || current == null) {
      throw new IllegalStateException("Expected the source to be valid, but it is not");
    }
    head = forward ? index.keys().higher(current) : index.keys().lower(current);
    verify();
  }

  @Override
  public boolean readValueUnlessStale(final DbValue target) {
    final var key = head;
    if (stale || key == null) {
      return false;
    }

    final var unflushed = store.lookupInMemory(key);
    final @Nullable DbValue value;
    if (unflushed != null) {
      value = unflushed.value();
    } else if (storageReads >= MAX_STORAGE_READS) {
      stale = true;
      return false;
    } else {
      storageReads++;
      value = store.readStorage(key, target);
    }

    // A key of the index without a value means that a commit deleted it; that commit changed the
    // version too, so this is only a safeguard
    if (value == null || index.version() != version) {
      stale = true;
      return false;
    }
    Values.copy(value, target);
    return true;
  }

  @Override
  public boolean isStale() {
    return stale;
  }

  @Override
  public void close() {}

  private void verify() {
    if (index.version() != version) {
      stale = true;
      head = null;
    }
  }
}
