/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshotReservation;
import io.camunda.zeebe.snapshots.SnapshotId;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import org.agrona.BitUtil;
import org.agrona.LangUtil;

/**
 * Handles in-memory and persisted reservations for a given snapshot.
 *
 * <p>Persisted reservations are stored in the file ${reservationsDirectory}/${snapshotId}.bin} in
 * binary format, see {@link PersistedReservationsV1}
 */
public class FileBasedSnapshotReservations implements AutoCloseable {

  // Header format
  // +++++++++++++++++++++++++
  private static final int HEADER_SIZE = BitUtil.SIZE_OF_INT * 2;
  private static final int CURRENT_VERSION = 1;
  private final Set<FileBasedSnapshotReservation> inMemory = new HashSet<>();
  private final PersistedReservationsV1 persisted;
  private byte nextReservationId;
  private FileChannel fileChannel;
  private final ConcurrencyControl actor;
  private int version;

  /**
   * @param snapshot the corresponding Snapshot
   * @param actor the actor to run future on. It must be on an IO bound pool
   */
  public FileBasedSnapshotReservations(
      final FileBasedSnapshot snapshot,
      final Path reservationsDirectory,
      final ConcurrencyControl actor) {
    this.actor = actor;
    persisted = new PersistedReservationsV1();
    final var path = reservationsDirectory.resolve(fileName(snapshot.getSnapshotId()));
    try {
      final var file = path.toFile();
      final var fileCreated = file.createNewFile();
      fileChannel =
          // File opened with the flag O_DIRECT
          FileChannel.open(
              path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
      if (!fileCreated) {
        persisted.readFromFile();
      } else {
        persisted.initializeByteBuffer();
      }
    } catch (final IOException e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  public FileBasedSnapshotReservation inMemoryReservation() {
    final var reservation = FileBasedSnapshotReservation.inMemory(this);
    inMemory.add(reservation);
    return reservation;
  }

  // to be called inside an actor from IO bound scheduler
  public FileBasedSnapshotReservation persistedReservation() throws IOException {
    if (nextReservationId == Byte.MAX_VALUE) {
      throw new IllegalStateException(
          "Too many reservations have been created: " + nextReservationId);
    }
    final var reservation = FileBasedSnapshotReservation.persisted(this, nextReservationId++);
    persisted.addPersistedReservation(reservation);
    return reservation;
  }

  public boolean isReserved() {
    return persisted.hasReservations() || !inMemory.isEmpty();
  }

  public ActorFuture<Void> removeReservation(final FileBasedSnapshotReservation reservation) {
    if (reservation.isInMemory()) {
      inMemory.remove(reservation);
      return CompletableActorFuture.completed(null);
    } else {
      final ActorFuture<Void> future = actor.createFuture();
      actor.run(
          () -> {
            try {
              persisted.removePersistedReservation(reservation);
              future.complete(null);
            } catch (final IOException e) {
              future.completeExceptionally(e);
            }
          });
      return future;
    }
  }

  @Override
  public void close() throws Exception {
    fileChannel.close();
  }

  public PersistedSnapshotReservation getPersistedSnapshotReservation(final byte b) {
    if (persisted.persistedReservations.get(HEADER_SIZE + b) == 1) {
      return FileBasedSnapshotReservation.persisted(this, b);
    } else {
      throw new IllegalArgumentException("No persisted reservation found for id " + b);
    }
  }

  public String fileName(final SnapshotId snapshotId) {
    return String.format("%s.bin", snapshotId.getSnapshotIdAsString());
  }

  class PersistedReservationsV1 {
    // ++++++++++++++++++++++++++
    // |0 8 16 32 | 40 48 56 64 |
    // | VERSION  | EMPTY       |
    // | 256 bytes: reservations|
    // |........................|
    // |........................|
    // |........................|
    // |....................264 |
    // +++++++++++++++++++++++++
    // each reservation is written at position HEADER_SIZE + reservationId with the value 1
    // to check if there is any reservation, it's enough to check if any byte is set to 1 (after the
    // header)
    private final ByteBuffer persistedReservations = ByteBuffer.allocate(HEADER_SIZE + 256);

    // Read the persisted reservations from the file and save them into persistedReservations
    private void readFromFile() throws IOException {
      fileChannel.read(persistedReservations, 0);
      persistedReservations.flip();
      version = persistedReservations.getInt();
      if (version != CURRENT_VERSION) {
        throw new IllegalStateException(
            String.format(
                "PersistedReservations contains an incompatible version: %d. Current version is %d.",
                version, CURRENT_VERSION));
      }
      persistedReservations.rewind();
      nextReservationId = (byte) (maxReservationId() + 1);
    }

    // Initialize the persistedReservations buffer if the file was not created yet.
    private void initializeByteBuffer() throws IOException {
      persistedReservations.putInt(CURRENT_VERSION);
      persistedReservations.putInt(0);
      version = CURRENT_VERSION;
      nextReservationId = 0;
      writeReservations();
    }

    // Writes the entire persistedReservations buffer to the file.
    // considering that the size of the bytebuffer is 264 bytes, it's ok to write the entire buffer
    // The fileChannel is opened in O_DIRECT mode: flush is not needed
    public void writeReservations() throws IOException {
      persistedReservations.rewind();
      final var written = fileChannel.write(persistedReservations, 0);
      if (written == 0) {
        throw new IllegalStateException("Failed to persist reservations. No bytes written.");
      }
    }

    private void addPersistedReservation(final FileBasedSnapshotReservation reservation)
        throws IOException {
      persistedReservations.put(HEADER_SIZE + reservation.reservationId(), (byte) 1);
      writeReservations();
    }

    private void removePersistedReservation(final FileBasedSnapshotReservation reservation)
        throws IOException {
      persistedReservations.put(HEADER_SIZE + reservation.reservationId(), (byte) 0);
      writeReservations();
    }

    /** There is no reservation if all bytes are set to zero. */
    private boolean hasReservations() {
      int v = 0;
      final var array = persisted.persistedReservations.array();
      for (int i = HEADER_SIZE; i < array.length; i++) {
        v |= array[i];
      }
      return v != 0;
    }

    private int maxReservationId() {
      int maxReservationIndex = -1;
      final var array = persisted.persistedReservations.array();
      for (int i = HEADER_SIZE; i < array.length; i++) {
        if (array[i] == 1) {
          maxReservationIndex = i - HEADER_SIZE;
        }
      }
      return maxReservationIndex;
    }
  }
}
