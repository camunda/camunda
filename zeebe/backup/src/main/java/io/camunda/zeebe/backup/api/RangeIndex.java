/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * This maintains an index of backup ranges, allowing efficient tracking of contiguous backups. It
 * supports adding and removing backups, as well as looking up which range a backup belongs to.
 *
 * @implSpec
 *     <ul>
 *       <li>Ranges that only contain a single backup are allowed to support merging of indices.
 *           This is required for backup stores which maintain separate indices for each node.
 *     </ul>
 *
 * @implNote Maintains two sorted sets of ranges, one sorted by the first backup and one sorted by
 *     the last backup. This allows efficient lookup of ranges by backup id, as well as efficient
 *     iteration in both ascending and descending order. By construction, the index is guaranteed to
 *     only have a single range for each first and last backup.
 */
public final class RangeIndex {
  private final TreeSet<Range> rangesByFirstBackup =
      new TreeSet<>(
          Comparator.comparing(
              Range::firstBackup, Comparator.comparingLong(BackupIdentifier::checkpointId)));

  private final TreeSet<Range> rangesByLastBackup =
      new TreeSet<>(
          Comparator.comparing(
              Range::lastBackup, Comparator.comparingLong(BackupIdentifier::checkpointId)));

  /** Returns the ranges in descending order by the last backup, the "best" range first. */
  public SortedSet<Range> descendingRanges() {
    return Collections.unmodifiableSortedSet(rangesByLastBackup.reversed());
  }

  /** Returns the ranges in ascending order by the first backup, the "oldest" range first. */
  public SortedSet<Range> ascendingRanges() {
    return Collections.unmodifiableSortedSet(rangesByFirstBackup);
  }

  /**
   * Adds a backup to the range index, potentially merging existing ranges.
   *
   * @apiNote If this backup is already contained in an existing range, the previous and next
   *     backups in the descriptor must not contradict the existing range. For Example, you cannot
   *     have an existing range [1, 3] and add backup 2 with previousBackup=null. Usage of this
   *     method must ensure that such contradictions do not occur. In practice, this is guaranteed
   *     because `nextBackup` is never known for new backups and `previousBackup` is always known
   *     and correct because checkpoint creation is linear.
   * @throws IllegalArgumentException if the backup contradicts an existing range.
   */
  public void add(final BackupIdentifier id, final BackupDescriptor descriptor) {
    final var addedRange =
        new Range(
            descriptor.previousBackup() != null ? descriptor.previousBackup() : id,
            descriptor.nextBackup() != null ? descriptor.nextBackup() : id);

    final var existingRange = lookup(id);
    if (existingRange != null) {
      if (existingRange.equals(addedRange)) {
        // already contained, nothing to do
        return;
      }
      if ((existingRange.firstBackup().checkpointId() < addedRange.firstBackup().checkpointId()
          || existingRange.lastBackup().checkpointId() > addedRange.firstBackup().checkpointId())) {
        throw new IllegalArgumentException(
            "Cannot add backup "
                + id
                + " with descriptor "
                + descriptor
                + " because it is smaller than the existing range "
                + existingRange);
      }
    }

    // by construction, there can only be one adjacent range on each side
    final var left = leftAdjacent(addedRange);
    final var right = rightAdjacent(addedRange);
    if (left != null && right != null) {
      connectRanges(left, right);
    } else if (left != null) {
      addToEnd(left, addedRange);
    } else if (right != null) {
      addToStart(addedRange, right);
    } else {
      addSingle(addedRange);
    }
  }

  private void addSingle(final Range addedRange) {
    rangesByLastBackup.add(addedRange);
    rangesByFirstBackup.add(addedRange);
  }

  private void addToStart(final Range addedRange, final Range right) {
    final var mergedRange = new Range(addedRange.firstBackup(), right.lastBackup());
    rangesByLastBackup.remove(right);
    rangesByFirstBackup.remove(right);
    rangesByLastBackup.add(mergedRange);
    rangesByFirstBackup.add(mergedRange);
  }

  private void addToEnd(final Range left, final Range addedRange) {
    final var mergedRange = new Range(left.firstBackup(), addedRange.lastBackup());
    rangesByLastBackup.remove(left);
    rangesByFirstBackup.remove(left);
    rangesByLastBackup.add(mergedRange);
    rangesByFirstBackup.add(mergedRange);
  }

  private void connectRanges(final Range left, final Range right) {
    final var mergedRange = new Range(left.firstBackup(), right.lastBackup());
    rangesByLastBackup.remove(left);
    rangesByFirstBackup.remove(left);
    rangesByLastBackup.remove(right);
    rangesByFirstBackup.remove(right);
    rangesByLastBackup.add(mergedRange);
    rangesByFirstBackup.add(mergedRange);
  }

  private Range rightAdjacent(final Range addedRange) {
    final var closestRight = rangesByFirstBackup.higher(addedRange);
    return Range.adjacent(addedRange, closestRight) ? closestRight : null;
  }

  private Range leftAdjacent(final Range addedRange) {
    final var closestLeft = rangesByLastBackup.lower(addedRange);
    return Range.adjacent(closestLeft, addedRange) ? closestLeft : null;
  }

  /**
   * Removes a backup from the range index, potentially splitting existing ranges.
   *
   * @apiNote The descriptor is trusted and used to construct the new ranges last and first backups.
   *     Usage of this method must ensure that the descriptor is consistent with the existing range
   *     containing the backup to be removed. For example, you cannot have an existing range [1, 3]
   *     and remove backup 2 with nextBackup=null. In practice, this should be guaranteed because
   *     this information is looked up from the backup store.
   */
  public void remove(final BackupIdentifier id, final BackupDescriptor descriptor) {
    if (lookup(id) instanceof final Range existingRange) {
      if (existingRange.firstBackup.equals(id) && existingRange.lastBackup.equals(id)) {
        removeSingle(existingRange, id, descriptor);
      } else if (descriptor.previousBackup() == null) {
        removeFromStart(existingRange, id, descriptor);
      } else if (descriptor.nextBackup() == null) {
        removeFromEnd(existingRange, id, descriptor);
      } else {
        removeFromMiddle(existingRange, id, descriptor);
      }
    }
  }

  private void removeSingle(
      final Range existingRange, final BackupIdentifier id, final BackupDescriptor descriptor) {
    if (descriptor.previousBackup() != null || descriptor.nextBackup() != null) {
      throw new IllegalArgumentException(
          "Cannot remove backup "
              + id
              + " with descriptor "
              + descriptor
              + " because it is larger than the existing range "
              + existingRange);
    }
    rangesByLastBackup.remove(existingRange);
    rangesByFirstBackup.remove(existingRange);
  }

  private void removeFromEnd(
      final Range existingRange, final BackupIdentifier id, final BackupDescriptor descriptor) {
    if (descriptor.previousBackup().checkpointId() < existingRange.firstBackup().checkpointId()) {
      throw new IllegalArgumentException(
          "Cannot remove backup "
              + id
              + " with descriptor "
              + descriptor
              + " because the previous backup is smaller than the existing range "
              + existingRange);
    }
    final var updatedRange = new Range(existingRange.firstBackup(), descriptor.previousBackup());
    rangesByLastBackup.remove(existingRange);
    rangesByFirstBackup.remove(existingRange);
    rangesByLastBackup.add(updatedRange);
    rangesByFirstBackup.add(updatedRange);
  }

  private void removeFromStart(
      final Range existingRange, final BackupIdentifier id, final BackupDescriptor descriptor) {
    if (descriptor.nextBackup().checkpointId() > existingRange.lastBackup().checkpointId()) {
      throw new IllegalArgumentException(
          "Cannot remove backup "
              + id
              + " with descriptor "
              + descriptor
              + " because the next backup is larger than the existing range "
              + existingRange);
    }
    final var updatedRange = new Range(descriptor.nextBackup(), existingRange.lastBackup());
    rangesByLastBackup.remove(existingRange);
    rangesByFirstBackup.remove(existingRange);
    rangesByLastBackup.add(updatedRange);
    rangesByFirstBackup.add(updatedRange);
  }

  private void removeFromMiddle(
      final Range existingRange, final BackupIdentifier id, final BackupDescriptor descriptor) {
    if (descriptor.previousBackup().checkpointId() > existingRange.lastBackup().checkpointId()
        || descriptor.nextBackup().checkpointId() < existingRange.firstBackup().checkpointId()) {
      throw new IllegalArgumentException(
          "Cannot remove backup "
              + id
              + " with descriptor "
              + descriptor
              + " because it is larger than the existing range "
              + existingRange);
    }
    final Range left = new Range(existingRange.firstBackup, descriptor.previousBackup());
    final Range right = new Range(descriptor.nextBackup(), existingRange.lastBackup);
    rangesByLastBackup.remove(existingRange);
    rangesByFirstBackup.remove(existingRange);
    rangesByLastBackup.add(left);
    rangesByFirstBackup.add(left);
    rangesByLastBackup.add(right);
    rangesByFirstBackup.add(right);
  }

  public Range lookup(final BackupIdentifier id) {
    final var singletonRange = new Range(id, id);
    final var candidateRange = rangesByFirstBackup.floor(singletonRange);
    if (candidateRange != null && candidateRange.contains(id)) {
      return candidateRange;
    } else {
      return null;
    }
  }

  /** A range of backups, defined by a first and last backup identifier. */
  public record Range(BackupIdentifier firstBackup, BackupIdentifier lastBackup) {
    public Range {
      Objects.requireNonNull(firstBackup);
      lastBackup = Objects.requireNonNullElse(lastBackup, firstBackup);
      if (firstBackup.checkpointId() > lastBackup.checkpointId()) {
        throw new IllegalArgumentException(
            "First backup's checkpointId must be less than or equal to the last backup's checkpointId");
      }
    }

    /**
     * Checks whether the given id is contained in the range, either as the first or last backup, or
     * in between.
     */
    public boolean contains(final BackupIdentifier id) {
      return id.checkpointId() >= firstBackup.checkpointId()
          && id.checkpointId() <= lastBackup.checkpointId();
    }

    public boolean contains(final Range range) {
      return contains(range.firstBackup()) && contains(range.lastBackup());
    }

    /**
     * Checks whether two ranges are adjacent, i.e., the last backup of the left range is the first
     * backup of the right range.
     *
     * <p>This is a static method to allow null ranges to be passed in.
     */
    public static boolean adjacent(final Range left, final Range right) {
      return left != null && right != null && left.lastBackup().equals(right.firstBackup());
    }

    /**
     * Expends the range to include the given id. If the id is already included, it'll return the
     * same range, otherwise it'll return a range with either the first or last backup updated.
     */
    public Range add(final BackupIdentifier id) {
      if (id.checkpointId() < firstBackup.checkpointId()) {
        return new Range(id, lastBackup);
      } else if (id.checkpointId() > lastBackup.checkpointId()) {
        return new Range(firstBackup, id);
      } else {
        return this;
      }
    }

    /**
     * Splits the range by removing the given id. If the id is at the boundary of the range, it'll
     * return a single range (with the boundary removed). If the id is in the middle of the range,
     * it'll return two ranges.
     *
     * <p>The descriptor is trusted and used to construct the new ranges last and first backups.
     */
    public SplitRanges remove(final BackupIdentifier id, final BackupDescriptor descriptor) {
      final Range left;
      final Range right;
      if (firstBackup.equals(id)) {
        left = null;
      } else {
        left = new Range(firstBackup, descriptor.previousBackup());
      }
      if (lastBackup.equals(id)) {
        right = null;
      } else {
        right = new Range(descriptor.nextBackup(), lastBackup);
      }
      return new SplitRanges(left, right);
    }

    /**
     * @param left may be null if there's no left range
     * @param right may be null if there's no right range
     */
    public record SplitRanges(Range left, Range right) {}
  }
}
