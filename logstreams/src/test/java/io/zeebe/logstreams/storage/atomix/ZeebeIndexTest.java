/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.storage.atomix;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.storage.log.entry.InitializeEntry;
import io.atomix.raft.zeebe.ZeebeEntry;
import io.atomix.storage.journal.Indexed;
import java.nio.ByteBuffer;
import org.junit.Test;

public class ZeebeIndexTest {

  @Test
  public void shouldNotFindIndexWhenNotReachedDensity() {
    // given - every 5 index is added
    final ZeebeIndexAdapter zeebeIndexAdapter = ZeebeIndexAdapter.ofDensity(5);

    // when
    final var index = zeebeIndexAdapter.lookupPosition(1L);

    // then
    assertThat(index).isEqualTo(-1);
  }

  @Test
  public void shouldFindIndexWhenReachedDensity() {
    // given - every 5 index is added
    final ZeebeIndexAdapter zeebeIndexAdapter = ZeebeIndexAdapter.ofDensity(5);

    // when
    zeebeIndexAdapter.index(asZeebeEntry(1, 1), 2);
    zeebeIndexAdapter.index(asZeebeEntry(2, 5), 4);
    zeebeIndexAdapter.index(asZeebeEntry(3, 10), 6);
    zeebeIndexAdapter.index(asZeebeEntry(4, 15), 8);
    zeebeIndexAdapter.index(asZeebeEntry(5, 20), 10);

    // then
    assertThat(zeebeIndexAdapter.lookupPosition(1)).isEqualTo(-1);
    assertThat(zeebeIndexAdapter.lookupPosition(16)).isEqualTo(-1);
    assertThat(zeebeIndexAdapter.lookupPosition(20)).isEqualTo(5);
    assertThat(zeebeIndexAdapter.lookupPosition(26)).isEqualTo(5);
  }

  @Test
  public void shouldNotAddToIndexWhenNotCorrectType() {
    // given - every 5 index is added
    final ZeebeIndexAdapter zeebeIndexAdapter = ZeebeIndexAdapter.ofDensity(5);

    // when
    zeebeIndexAdapter.index(
        new Indexed<>(5, new InitializeEntry(0, System.currentTimeMillis()), 10), 10);

    // then
    assertThat(zeebeIndexAdapter.lookupPosition(1)).isEqualTo(-1);
    assertThat(zeebeIndexAdapter.lookupPosition(16)).isEqualTo(-1);
    assertThat(zeebeIndexAdapter.lookupPosition(20)).isEqualTo(-1);
    assertThat(zeebeIndexAdapter.lookupPosition(21)).isEqualTo(-1);
  }

  @Test
  public void shouldFindLowerIndexWhenNotReachedDensity() {
    // given - every 5 index is added
    final ZeebeIndexAdapter zeebeIndexAdapter = ZeebeIndexAdapter.ofDensity(5);
    // index entries
    zeebeIndexAdapter.index(asZeebeEntry(1, 1), 2);
    zeebeIndexAdapter.index(asZeebeEntry(2, 5), 4);
    zeebeIndexAdapter.index(asZeebeEntry(3, 10), 6);
    zeebeIndexAdapter.index(asZeebeEntry(4, 15), 8);
    zeebeIndexAdapter.index(asZeebeEntry(5, 20), 10);

    // when
    zeebeIndexAdapter.index(asZeebeEntry(6, 25), 12);
    zeebeIndexAdapter.index(asZeebeEntry(7, 30), 14);
    zeebeIndexAdapter.index(asZeebeEntry(8, 35), 16);

    // then
    assertThat(zeebeIndexAdapter.lookupPosition(20)).isEqualTo(5);
    assertThat(zeebeIndexAdapter.lookupPosition(21)).isEqualTo(5);
    assertThat(zeebeIndexAdapter.lookupPosition(31)).isEqualTo(5);
    assertThat(zeebeIndexAdapter.lookupPosition(35)).isEqualTo(5);
  }

  @Test
  public void shouldFindNextIndexWhenReachedDensity() {
    // given - every 5 index is added
    final ZeebeIndexAdapter zeebeIndexAdapter = ZeebeIndexAdapter.ofDensity(5);
    // index entries
    zeebeIndexAdapter.index(asZeebeEntry(1, 1), 2);
    zeebeIndexAdapter.index(asZeebeEntry(2, 5), 4);
    zeebeIndexAdapter.index(asZeebeEntry(3, 10), 6);
    zeebeIndexAdapter.index(asZeebeEntry(4, 15), 8);
    zeebeIndexAdapter.index(asZeebeEntry(5, 20), 10);
    zeebeIndexAdapter.index(asZeebeEntry(6, 25), 12);
    zeebeIndexAdapter.index(asZeebeEntry(7, 30), 14);
    zeebeIndexAdapter.index(asZeebeEntry(8, 35), 16);

    // when
    zeebeIndexAdapter.index(asZeebeEntry(9, 40), 18);
    zeebeIndexAdapter.index(asZeebeEntry(10, 45), 20);

    // then
    assertThat(zeebeIndexAdapter.lookupPosition(20)).isEqualTo(5);
    assertThat(zeebeIndexAdapter.lookupPosition(31)).isEqualTo(5);
    assertThat(zeebeIndexAdapter.lookupPosition(45)).isEqualTo(10);
    assertThat(zeebeIndexAdapter.lookupPosition(46)).isEqualTo(10);
  }

  @Test
  public void shouldTruncateIndex() {
    // given - every 5 index is added
    final ZeebeIndexAdapter zeebeIndexAdapter = ZeebeIndexAdapter.ofDensity(5);
    // index entries
    zeebeIndexAdapter.index(asZeebeEntry(1, 1), 2);
    zeebeIndexAdapter.index(asZeebeEntry(2, 5), 4);
    zeebeIndexAdapter.index(asZeebeEntry(3, 10), 6);
    zeebeIndexAdapter.index(asZeebeEntry(4, 15), 8);
    zeebeIndexAdapter.index(asZeebeEntry(5, 20), 10);
    zeebeIndexAdapter.index(asZeebeEntry(6, 25), 12);
    zeebeIndexAdapter.index(asZeebeEntry(7, 30), 14);
    zeebeIndexAdapter.index(asZeebeEntry(8, 35), 16);
    zeebeIndexAdapter.index(asZeebeEntry(9, 40), 18);
    zeebeIndexAdapter.index(asZeebeEntry(10, 45), 20);

    // when
    zeebeIndexAdapter.truncate(8);

    // then
    assertThat(zeebeIndexAdapter.lookupPosition(20)).isEqualTo(5);
    assertThat(zeebeIndexAdapter.lookupPosition(31)).isEqualTo(5);
    assertThat(zeebeIndexAdapter.lookupPosition(35)).isEqualTo(5);
    assertThat(zeebeIndexAdapter.lookupPosition(45)).isEqualTo(5);
    assertThat(zeebeIndexAdapter.lookupPosition(46)).isEqualTo(5);
  }

  @Test
  public void shouldTruncateCompleteIndex() {
    // given - every 5 index is added
    final ZeebeIndexAdapter zeebeIndexAdapter = ZeebeIndexAdapter.ofDensity(5);
    // index entries
    zeebeIndexAdapter.index(asZeebeEntry(1, 1), 2);
    zeebeIndexAdapter.index(asZeebeEntry(2, 5), 4);
    zeebeIndexAdapter.index(asZeebeEntry(3, 10), 6);
    zeebeIndexAdapter.index(asZeebeEntry(4, 15), 8);
    zeebeIndexAdapter.index(asZeebeEntry(5, 20), 10);
    zeebeIndexAdapter.index(asZeebeEntry(6, 25), 12);
    zeebeIndexAdapter.index(asZeebeEntry(7, 30), 14);
    zeebeIndexAdapter.index(asZeebeEntry(8, 35), 16);
    zeebeIndexAdapter.index(asZeebeEntry(9, 40), 18);
    zeebeIndexAdapter.index(asZeebeEntry(10, 45), 20);
    zeebeIndexAdapter.truncate(8);

    // when
    zeebeIndexAdapter.truncate(4);

    // then
    assertThat(zeebeIndexAdapter.lookupPosition(5)).isEqualTo(-1);
    assertThat(zeebeIndexAdapter.lookupPosition(20)).isEqualTo(-1);
    assertThat(zeebeIndexAdapter.lookupPosition(35)).isEqualTo(-1);
    assertThat(zeebeIndexAdapter.lookupPosition(50)).isEqualTo(-1);
  }

  @Test
  public void shouldNotCompactIndex() {
    // given - every 5 index is added
    final ZeebeIndexAdapter zeebeIndexAdapter = ZeebeIndexAdapter.ofDensity(5);
    // index entries
    zeebeIndexAdapter.index(asZeebeEntry(1, 1), 2);
    zeebeIndexAdapter.index(asZeebeEntry(2, 5), 4);
    zeebeIndexAdapter.index(asZeebeEntry(3, 10), 6);
    zeebeIndexAdapter.index(asZeebeEntry(4, 15), 8);
    zeebeIndexAdapter.index(asZeebeEntry(5, 20), 10);
    zeebeIndexAdapter.index(asZeebeEntry(6, 25), 12);
    zeebeIndexAdapter.index(asZeebeEntry(7, 30), 14);
    zeebeIndexAdapter.index(asZeebeEntry(8, 35), 16);
    zeebeIndexAdapter.index(asZeebeEntry(9, 40), 18);
    zeebeIndexAdapter.index(asZeebeEntry(10, 45), 20);

    // when
    zeebeIndexAdapter.compact(8);

    // then
    assertThat(zeebeIndexAdapter.lookupPosition(21)).isEqualTo(5);
    assertThat(zeebeIndexAdapter.lookupPosition(31)).isEqualTo(5);
    assertThat(zeebeIndexAdapter.lookupPosition(35)).isEqualTo(5);
    assertThat(zeebeIndexAdapter.lookupPosition(45)).isEqualTo(10);
    assertThat(zeebeIndexAdapter.lookupPosition(46)).isEqualTo(10);
  }

  @Test
  public void shouldCompactIndex() {
    // given - every 5 index is added
    final ZeebeIndexAdapter zeebeIndexAdapter = ZeebeIndexAdapter.ofDensity(5);
    // index entries
    zeebeIndexAdapter.index(asZeebeEntry(1, 1), 2);
    zeebeIndexAdapter.index(asZeebeEntry(2, 5), 4);
    zeebeIndexAdapter.index(asZeebeEntry(3, 10), 6);
    zeebeIndexAdapter.index(asZeebeEntry(4, 15), 8);
    zeebeIndexAdapter.index(asZeebeEntry(5, 20), 10);
    zeebeIndexAdapter.index(asZeebeEntry(6, 25), 12);
    zeebeIndexAdapter.index(asZeebeEntry(7, 30), 14);
    zeebeIndexAdapter.index(asZeebeEntry(8, 35), 16);
    zeebeIndexAdapter.index(asZeebeEntry(9, 40), 18);
    zeebeIndexAdapter.index(asZeebeEntry(10, 45), 20);

    // when
    zeebeIndexAdapter.compact(11);

    // then
    assertThat(zeebeIndexAdapter.lookupPosition(21)).isEqualTo(-1);
    assertThat(zeebeIndexAdapter.lookupPosition(31)).isEqualTo(-1);
    assertThat(zeebeIndexAdapter.lookupPosition(35)).isEqualTo(-1);
    assertThat(zeebeIndexAdapter.lookupPosition(45)).isEqualTo(10);
    assertThat(zeebeIndexAdapter.lookupPosition(46)).isEqualTo(10);
  }

  private static Indexed asZeebeEntry(final long index, final long lowestPos) {
    final ZeebeEntry zeebeEntry =
        new ZeebeEntry(0, System.currentTimeMillis(), ByteBuffer.allocate(0));
    zeebeEntry.setLowestPosition(lowestPos);
    return new Indexed(index, zeebeEntry, 0);
  }
}
