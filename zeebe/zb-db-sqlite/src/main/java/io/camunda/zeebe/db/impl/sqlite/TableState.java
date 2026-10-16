/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What all {@link SqliteColumnFamily} instances of one column family share: the layout of the
 * table, which changes once if the table falls back to an opaque key, and the iterations that are
 * currently running on it.
 */
final class TableState {

  private KeyLayout layout;
  private final List<Cursor> cursors = new ArrayList<>();

  TableState(final KeyLayout layout) {
    this.layout = layout;
  }

  KeyLayout layout() {
    return layout;
  }

  void layout(final KeyLayout layout) {
    this.layout = layout;
  }

  List<Cursor> cursors() {
    return cursors;
  }

  /** The position of a running iteration. */
  static final class Cursor {
    private final boolean reverse;
    private final byte[] prefix;
    private byte @Nullable [] current;

    /**
     * @param reverse whether the iteration moves towards smaller keys
     * @param prefix the serialized prefix the iteration is restricted to, empty if it is not
     */
    Cursor(final boolean reverse, final byte[] prefix) {
      this.reverse = reverse;
      this.prefix = prefix;
    }

    /** Moves the cursor to the key that the iteration visits now. */
    void moveTo(final byte[] key) {
      current = key;
    }

    /** Whether the iteration has yet to visit the key, if it is within the iterated range. */
    boolean isAhead(final byte[] key, final int length) {
      final var position = current;
      if (position == null || length < prefix.length) {
        return false;
      }
      if (!Arrays.equals(key, 0, prefix.length, prefix, 0, prefix.length)) {
        return false;
      }
      final int order = Arrays.compareUnsigned(key, 0, length, position, 0, position.length);
      return reverse ? order < 0 : order > 0;
    }
  }
}
