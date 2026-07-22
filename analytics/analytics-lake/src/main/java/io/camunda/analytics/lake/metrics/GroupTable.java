/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.metrics;

import java.util.Arrays;

/**
 * Open-addressing table mapping a group key — window slot plus one code per dim — to a dense group
 * index {@code [0, count)}, allocation-free per lookup at steady state (arrays grow rarely and are
 * reused across flush windows). The dense index is what {@link MetricsRider} keys its accumulator
 * pools by; the key parts are stored columnar so drain can read them back per group.
 *
 * <p>Flush thread only, like everything the rider owns.
 */
final class GroupTable {

  private static final int INITIAL_CAPACITY = 256; // power of two

  private final int dimCount;

  private int[] table; // group index + 1; 0 = empty slot
  private long[] slotByGroup;
  private int[][] dimCodesByGroup; // [dim][group]
  private int count;

  GroupTable(final int dimCount) {
    this.dimCount = dimCount;
    table = new int[INITIAL_CAPACITY * 2];
    slotByGroup = new long[INITIAL_CAPACITY];
    dimCodesByGroup = new int[dimCount][INITIAL_CAPACITY];
  }

  int count() {
    return count;
  }

  long slotOf(final int group) {
    return slotByGroup[group];
  }

  int dimCodeOf(final int dim, final int group) {
    return dimCodesByGroup[dim][group];
  }

  /**
   * Dense index of the group keyed by {@code (slot, dimCodes[0..dimCount))}, adding it if unseen.
   */
  int findOrAdd(final long slot, final int[] dimCodes) {
    final int mask = table.length - 1;
    int probe = (int) (mix(slot, dimCodes) & mask);
    while (true) {
      final int entry = table[probe];
      if (entry == 0) {
        return add(slot, dimCodes, probe);
      }
      final int group = entry - 1;
      if (matches(group, slot, dimCodes)) {
        return group;
      }
      probe = (probe + 1) & mask;
    }
  }

  /** Forgets every group; storage is retained for the next window. */
  void clear() {
    Arrays.fill(table, 0);
    count = 0;
  }

  private boolean matches(final int group, final long slot, final int[] dimCodes) {
    if (slotByGroup[group] != slot) {
      return false;
    }
    for (int d = 0; d < dimCount; d++) {
      if (dimCodesByGroup[d][group] != dimCodes[d]) {
        return false;
      }
    }
    return true;
  }

  private int add(final long slot, final int[] dimCodes, final int probe) {
    if (count == slotByGroup.length) {
      grow();
      // growing rehashed everything; re-probe for the (still absent) key's new empty slot.
      return findOrAdd(slot, dimCodes);
    }
    final int group = count++;
    slotByGroup[group] = slot;
    for (int d = 0; d < dimCount; d++) {
      dimCodesByGroup[d][group] = dimCodes[d];
    }
    table[probe] = group + 1;
    return group;
  }

  private void grow() {
    final int newGroupCapacity = slotByGroup.length * 2;
    slotByGroup = Arrays.copyOf(slotByGroup, newGroupCapacity);
    for (int d = 0; d < dimCount; d++) {
      dimCodesByGroup[d] = Arrays.copyOf(dimCodesByGroup[d], newGroupCapacity);
    }
    table = new int[newGroupCapacity * 2];
    final int mask = table.length - 1;
    final int[] scratch = new int[dimCount];
    for (int group = 0; group < count; group++) {
      for (int d = 0; d < dimCount; d++) {
        scratch[d] = dimCodesByGroup[d][group];
      }
      int probe = (int) (mix(slotByGroup[group], scratch) & mask);
      while (table[probe] != 0) {
        probe = (probe + 1) & mask;
      }
      table[probe] = group + 1;
    }
  }

  private static long mix(final long slot, final int[] dimCodes) {
    long h = slot * 0x9E3779B97F4A7C15L;
    for (final int code : dimCodes) {
      h = (h ^ code) * 0xC2B2AE3D27D4EB4FL;
    }
    return h ^ (h >>> 32);
  }
}
