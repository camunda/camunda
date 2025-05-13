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
import io.camunda.zeebe.snapshots.sbe.MessageHeaderDecoder;
import io.camunda.zeebe.snapshots.sbe.MessageHeaderEncoder;
import io.camunda.zeebe.snapshots.sbe.ReservationReason;
import io.camunda.zeebe.snapshots.sbe.ReservationsDecoder;
import io.camunda.zeebe.snapshots.sbe.ReservationsEncoder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.agrona.LangUtil;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Handles in-memory and persisted reservations for a given snapshot.
 *
 * <p>Persisted reservations are stored in the file ${reservationsDirectory}/${snapshotId}.bin} in
 * binary format, see {@link PersistedReservationsV1}
 */
public class FileBasedSnapshotReservationStore implements AutoCloseable {

  private final Set<FileBasedSnapshotReservation> inMemory = new HashSet<>();
  private final PersistedReservationsV1 persisted;
  private FileChannel fileChannel;
  private final ConcurrencyControl actor;
  private final InstantSource clock;
  private final Path path;

  /**
   * @param snapshot the corresponding Snapshot
   * @param actor the actor to run future on. It must be on an IO bound pool
   */
  public FileBasedSnapshotReservationStore(
      final FileBasedSnapshot snapshot,
      final Path reservationsDirectory,
      final ConcurrencyControl actor,
      final InstantSource clock) {
    this.actor = actor;
    this.clock = clock;
    persisted = new PersistedReservationsV1();
    path = reservationsDirectory.resolve(fileName(snapshot.getSnapshotId()));
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
        persisted.writeReservations();
      }
    } catch (final IOException e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  Path path() {
    return path;
  }

  public FileBasedSnapshotReservation inMemoryReservation() {
    final var reservation = FileBasedSnapshotReservation.inMemory(this);
    inMemory.add(reservation);
    return reservation;
  }

  // to be called inside an actor from IO bound scheduler
  public FileBasedSnapshotReservation persistedReservation(
      final UUID id, final long validUntil, final PersistedSnapshotReservation.Reason reason)
      throws IOException {
    final var reservation = FileBasedSnapshotReservation.persisted(this, id, validUntil, reason);
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

  public PersistedSnapshotReservation getPersistedSnapshotReservation(final UUID id) {
    return persisted.reservations.get(id);
  }

  public String fileName(final SnapshotId snapshotId) {
    return String.format("%s.bin", snapshotId.getSnapshotIdAsString());
  }

  class PersistedReservationsV1 {
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final ReservationsEncoder reservationsEncoder = new ReservationsEncoder();
    private final ReservationsDecoder reservationsDecoder = new ReservationsDecoder();
    private final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
    private final HashMap<UUID, FileBasedSnapshotReservation> reservations = new HashMap<>();

    // Read the persisted reservations from the file and save them into persistedReservations
    private void readFromFile() throws IOException {
      fileChannel.read(buffer.byteBuffer(), 0);
      buffer.byteBuffer().flip();
      reservationsDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
      reservations.clear();
      final var reservationsInFiles = reservationsDecoder.reservationsList();
      reservationsInFiles
          .iterator()
          .forEachRemaining(
              r -> {
                final var reservation = r.reservation();
                final var id = new UUID(reservation.high(), reservation.low());
                final var validUntil = r.validUntil();
                final var reason =
                    switch (r.reason()) {
                      case BACKUP -> PersistedSnapshotReservation.Reason.BACKUP;
                      case SCALE_UP -> PersistedSnapshotReservation.Reason.SCALE_UP;
                      default -> throw new IllegalStateException("Unknown reason " + r.reason());
                    };
                reservations.put(
                    id,
                    new FileBasedSnapshotReservation(
                        FileBasedSnapshotReservationStore.this, id, false, validUntil, reason));
              });
    }

    // Writes the entire persistedReservations buffer to the file.
    // considering that the size of the bytebuffer is 264 bytes, it's ok to write the entire buffer
    // The fileChannel is opened in O_DIRECT mode: flush is not needed
    public void writeReservations() throws IOException {
      reservationsEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
      final var writer = reservationsEncoder.reservationsListCount(reservations.size());
      reservations.forEach(
          (id, r) -> {
            final var reservation = writer.next();
            reservation
                .reservation()
                .high(r.reservationId().getMostSignificantBits())
                .low(r.reservationId().getLeastSignificantBits());
            reservation.validUntil(r.validUntil());
            reservation.reason(ReservationReason.get(r.reason().code()));
          });
      buffer.byteBuffer().rewind();
      final var written = fileChannel.write(buffer.byteBuffer(), 0);
      if (written == 0) {
        throw new IllegalStateException("Failed to persist reservations. No bytes written.");
      }
    }

    private void addPersistedReservation(final FileBasedSnapshotReservation reservation)
        throws IOException {
      reservations.put(reservation.reservationId(), reservation);
      writeReservations();
    }

    private void removePersistedReservation(final FileBasedSnapshotReservation reservation)
        throws IOException {
      reservations.remove(reservation.reservationId());
      writeReservations();
    }

    boolean hasReservations() {
      var validReservations = false;
      for (final var reservation : reservations.values()) {
        if (reservation.validUntil() >= 0 && reservation.validUntil() > clock.millis()) {
          validReservations = true;
          break;
        }
      }
      return validReservations;
    }
  }
}
