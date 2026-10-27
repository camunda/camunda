/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api.scheduling;

import io.camunda.zeebe.protocol.record.intent.Intent;

/**
 * Represents a cache to be used by the {@link
 * io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService}, which allows it to cache which
 * commands it has written and avoid writing them again until they've been removed from the cache.
 */
public interface ScheduledCommandCache {

  /**
   * Add the given intent and key pair to the cache.
   *
   * @param intent intent to cache
   * @param key key to cache
   */
  void add(final Intent intent, final long key);

  /** Returns true if the given intent and key pair is already cached. */
  boolean contains(final Intent intent, final long key);

  /** Removes the given intent/key pair from the cache. */
  void remove(final Intent intent, final long key);

  /** Clears the underlying cache of all intent/key pairs. */
  void clear();

  /** A dummy cache implementation which does nothing, i.e. caches nothing. */
  final class NoopScheduledCommandCache
      implements StageableScheduledCommandCache, StagedScheduledCommandCache {

    @Override
    public void persist() {}

    @Override
    public void rollback() {}

    @Override
    public void add(final Intent intent, final long key) {}

    @Override
    public boolean contains(final Intent intent, final long key) {
      return false;
    }

    @Override
    public void remove(final Intent intent, final long key) {}

    @Override
    public void clear() {}

    @Override
    public StagedScheduledCommandCache stage() {
      return this;
    }
  }

  /**
   * Represents staged changes to the cache that have not been persisted yet. Call {@link
   * #persist()} to do so.
   *
   * <p>Once persisted, staged changes can be rolled back by calling {@link #rollback()}. This will
   * remove all staged changes from the main cache.
   *
   * <p>See {@link StageableScheduledCommandCache} for more.
   */
  interface ScheduledCommandCacheChanges {

    void persist();

    void rollback();
  }

  /**
   * A {@link ScheduledCommandCache} which allows staging changes before persisting them. This
   * enables you to stage new keys to be added to the cache, and only actually commit them to the
   * real cache when you're sure that the scheduled commands have been written.
   */
  interface StageableScheduledCommandCache extends ScheduledCommandCache {

    /** Returns a new stage for this cache, where modifications are temporary. */
    StagedScheduledCommandCache stage();
  }

  /**
   * A cache where modifications are staged but not added to the main cache which produced it. Call
   * {@link #persist()} to do so.
   *
   * <p>The semantics of each operation are changed slightly:
   *
   * <p>A staged {@link #add(Intent, long)} will buffer the intent/key pair, and all buffered pairs
   * are added to the main cache on {@link #persist()}.
   *
   * <p>A staged {@link #remove(Intent, long)} only removes buffered intent/key pairs.
   *
   * <p>A staged {@link #contains(Intent, long)} first looks up the buffered intent/key pairs, and
   * if not found, will also perform a look-up in the main cache.
   *
   * <p>A staged {@link #clear()} only removes the staged keys, and does not touch the main cache.
   */
  interface StagedScheduledCommandCache
      extends ScheduledCommandCache, ScheduledCommandCacheChanges {}
}
