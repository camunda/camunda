/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.storage.atomix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import io.atomix.raft.storage.log.entry.InitializeEntry;
import io.atomix.storage.journal.Indexed;
import io.atomix.storage.journal.index.JournalIndex;
import io.atomix.storage.journal.index.Position;
import org.junit.Test;

public class AtomixIndexTest {

  @Test
  public void shouldNotFindIndexWhenNotReachedDensity() {
    // given - every 5 index is added
    final JournalIndex index = ZeebeIndexAdapter.ofDensity(5);

    // when
    final Position position = index.lookup(1);

    // then
    assertNull(position);
  }

  @Test
  public void shouldFindIndexWhenReachedDensity() {
    // given - every 5 index is added
    final JournalIndex index = ZeebeIndexAdapter.ofDensity(5);

    // when
    index.index(asIndexedEntry(1), 2);
    index.index(asIndexedEntry(2), 4);
    index.index(asIndexedEntry(3), 6);
    index.index(asIndexedEntry(4), 8);
    index.index(asIndexedEntry(5), 10);

    // then
    assertEquals(5, index.lookup(5).index());
    assertEquals(10, index.lookup(5).position());
  }

  @Test
  public void shouldFindLowerIndexWhenNotReachedDensity() {
    // given - every 5 index is added
    final JournalIndex index = ZeebeIndexAdapter.ofDensity(5);
    // index entries
    index.index(asIndexedEntry(1), 2);
    index.index(asIndexedEntry(2), 4);
    index.index(asIndexedEntry(3), 6);
    index.index(asIndexedEntry(4), 8);
    index.index(asIndexedEntry(5), 10);

    // when
    index.index(asIndexedEntry(6), 12);
    index.index(asIndexedEntry(7), 14);
    index.index(asIndexedEntry(8), 16);

    // then
    assertEquals(5, index.lookup(8).index());
    assertEquals(10, index.lookup(8).position());
  }

  @Test
  public void shouldFindNextIndexWhenReachedDensity() {
    // given - every 5 index is added
    final JournalIndex index = ZeebeIndexAdapter.ofDensity(5);
    // index entries
    index.index(asIndexedEntry(1), 2);
    index.index(asIndexedEntry(2), 4);
    index.index(asIndexedEntry(3), 6);
    index.index(asIndexedEntry(4), 8);
    index.index(asIndexedEntry(5), 10);
    index.index(asIndexedEntry(6), 12);
    index.index(asIndexedEntry(7), 14);
    index.index(asIndexedEntry(8), 16);

    // when
    index.index(asIndexedEntry(9), 18);
    index.index(asIndexedEntry(10), 20);

    // then
    assertEquals(10, index.lookup(10).index());
    assertEquals(20, index.lookup(10).position());
  }

  @Test
  public void shouldTruncateIndex() {
    // given - every 5 index is added
    final JournalIndex index = ZeebeIndexAdapter.ofDensity(5);
    // index entries
    index.index(asIndexedEntry(1), 2);
    index.index(asIndexedEntry(2), 4);
    index.index(asIndexedEntry(3), 6);
    index.index(asIndexedEntry(4), 8);
    index.index(asIndexedEntry(5), 10);
    index.index(asIndexedEntry(6), 12);
    index.index(asIndexedEntry(7), 14);
    index.index(asIndexedEntry(8), 16);
    index.index(asIndexedEntry(9), 18);
    index.index(asIndexedEntry(10), 20);

    // when
    index.truncate(8);

    // then
    assertEquals(5, index.lookup(8).index());
    assertEquals(10, index.lookup(8).position());
    assertEquals(5, index.lookup(10).index());
    assertEquals(10, index.lookup(10).position());
  }

  @Test
  public void shouldTruncateCompleteIndex() {
    // given - every 5 index is added
    final JournalIndex index = ZeebeIndexAdapter.ofDensity(5);
    // index entries
    index.index(asIndexedEntry(1), 2);
    index.index(asIndexedEntry(2), 4);
    index.index(asIndexedEntry(3), 6);
    index.index(asIndexedEntry(4), 8);
    index.index(asIndexedEntry(5), 10);
    index.index(asIndexedEntry(6), 12);
    index.index(asIndexedEntry(7), 14);
    index.index(asIndexedEntry(8), 16);
    index.index(asIndexedEntry(9), 18);
    index.index(asIndexedEntry(10), 20);
    index.truncate(8);

    // when
    index.truncate(4);

    // then
    assertNull(index.lookup(4));
    assertNull(index.lookup(5));
    assertNull(index.lookup(8));
    assertNull(index.lookup(10));
  }

  @Test
  public void shouldNotCompactIndex() {
    // given - every 5 index is added
    final JournalIndex index = ZeebeIndexAdapter.ofDensity(5);
    // index entries
    index.index(asIndexedEntry(1), 2);
    index.index(asIndexedEntry(2), 4);
    index.index(asIndexedEntry(3), 6);
    index.index(asIndexedEntry(4), 8);
    index.index(asIndexedEntry(5), 10);
    index.index(asIndexedEntry(6), 12);
    index.index(asIndexedEntry(7), 14);
    index.index(asIndexedEntry(8), 16);
    index.index(asIndexedEntry(9), 18);
    index.index(asIndexedEntry(10), 20);

    // when
    index.compact(8);

    // then
    assertEquals(5, index.lookup(8).index());
    assertEquals(10, index.lookup(8).position());
    assertEquals(10, index.lookup(10).index());
    assertEquals(20, index.lookup(10).position());
  }

  @Test
  public void shouldCompactIndex() {
    // given - every 5 index is added
    final JournalIndex index = ZeebeIndexAdapter.ofDensity(5);
    // index entries
    index.index(asIndexedEntry(1), 2);
    index.index(asIndexedEntry(2), 4);
    index.index(asIndexedEntry(3), 6);
    index.index(asIndexedEntry(4), 8);
    index.index(asIndexedEntry(5), 10);
    index.index(asIndexedEntry(6), 12);
    index.index(asIndexedEntry(7), 14);
    index.index(asIndexedEntry(8), 16);
    index.index(asIndexedEntry(9), 18);
    index.index(asIndexedEntry(10), 20);

    // when
    index.compact(11);

    // then
    assertNull(index.lookup(4));
    assertNull(index.lookup(5));
    assertNull(index.lookup(8));

    assertEquals(10, index.lookup(10).index());
    assertEquals(20, index.lookup(10).position());
    assertEquals(10, index.lookup(12).index());
    assertEquals(20, index.lookup(12).position());
  }

  private static Indexed asIndexedEntry(final long index) {
    return new Indexed(index, new InitializeEntry(0, System.currentTimeMillis()), 0, -1);
  }
}
