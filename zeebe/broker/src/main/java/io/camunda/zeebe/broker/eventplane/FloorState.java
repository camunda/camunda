/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.eventplane.publisher.UploadFloor;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

/**
 * The partition's upload floor in its state ({@link ZbColumnFamilies#EVENT_PLANE_FLOOR}), so that
 * snapshots carry it. Every replica keeps its own, and compaction stops at its position; a held
 * floor, at {@link #HELD}, stops compaction where it is until the first upload. Like any state, an
 * instance belongs to the thread of its transaction context.
 */
public final class FloorState {

  /** The position of a held floor, and of a floor whose uploads held no records yet. */
  public static final long HELD = -1;

  private static final String KEY = "floor";

  private final TransactionContext transactionContext;
  private final DbString key = new DbString();
  private final FloorEntry entry = new FloorEntry();
  private final ColumnFamily<DbString, FloorEntry> floors;

  public FloorState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    this.transactionContext = transactionContext;
    key.wrapString(KEY);
    floors =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.EVENT_PLANE_FLOOR, transactionContext, key, entry);
  }

  /** The floor's position, {@link #HELD} if nothing was uploaded yet, or empty without a floor. */
  public OptionalLong position() {
    final var stored = floors.get(key);
    return stored == null ? OptionalLong.empty() : OptionalLong.of(stored.position());
  }

  /** The floor of the last upload, or null without a floor or while it is held. */
  public @Nullable UploadFloor floor() {
    final var stored = floors.get(key);
    return stored == null || stored.isHeld() ? null : stored.toFloor();
  }

  /**
   * Stores the floor of an upload.
   *
   * @throws FloorMovedBackException if its position is below the stored one's
   */
  public void store(final UploadFloor floor) {
    transactionContext.runInTransaction(
        () -> {
          final var stored = floors.get(key);
          if (stored != null && floor.position() < stored.position()) {
            throw new FloorMovedBackException(stored.position(), floor.position());
          }
          floors.upsert(key, entry.set(floor));
        });
  }

  /**
   * Stores the floor if it is ahead of the stored one: at a higher position, or at the same one and
   * a higher index. Returns whether it stored it.
   */
  public boolean advance(final UploadFloor floor) {
    final var stored = floors.get(key);
    final boolean ahead =
        stored == null
            || floor.position() > stored.position()
            || (floor.position() == stored.position() && floor.index() > stored.index());
    if (ahead) {
      floors.upsert(key, entry.set(floor));
    }
    return ahead;
  }

  /** Stores a held floor, unless there is a floor already. */
  public void holdIfAbsent() {
    transactionContext.runInTransaction(
        () -> {
          if (floors.get(key) == null) {
            floors.insert(key, entry.hold());
          }
        });
  }

  /** Deletes the floor, if there is one, so that it no longer stops compaction. */
  public void delete() {
    floors.deleteIfExists(key);
  }
}
