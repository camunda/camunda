/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class RdbmsLogStorageReaderTest {

  private static final long TERM = 1;
  private static final int PAGE_SIZE = 2;

  private final InMemoryLogBlockRepository repository = new InMemoryLogBlockRepository();
  private RdbmsLogStorageReader reader;

  @BeforeEach
  void setUp() {
    repository.claimTerm(TERM);
    reader = new RdbmsLogStorageReader(repository, PAGE_SIZE);
  }

  @Test
  void shouldReadAllBlocksInOrderAcrossPages() {
    // given
    appendBlocksOfThree(1, 5);

    // when
    final var blocks = readRemaining();

    // then
    assertThat(blocks).containsExactly("1..3", "4..6", "7..9", "10..12", "13..15");
  }

  @Test
  void shouldSeekToBlockContainingPosition() {
    // given
    appendBlocksOfThree(1, 3);

    // when
    reader.seek(5);

    // then
    assertThat(readRemaining()).containsExactly("4..6", "7..9");
  }

  @Test
  void shouldSeekToBlockStartingAtPosition() {
    // given
    appendBlocksOfThree(1, 3);

    // when
    reader.seek(4);

    // then
    assertThat(readRemaining()).containsExactly("4..6", "7..9");
  }

  @Test
  void shouldSeekToFirstBlockForNegativePosition() {
    // given
    appendBlocksOfThree(1, 3);
    reader.seek(7);

    // when
    reader.seek(-1);

    // then
    assertThat(readRemaining()).containsExactly("1..3", "4..6", "7..9");
  }

  @Test
  void shouldSeekToFirstBlockForPositionBeforeTheLog() {
    // given
    appendBlocksOfThree(10, 2);

    // when
    reader.seek(5);

    // then
    assertThat(readRemaining()).containsExactly("10..12", "13..15");
  }

  @Test
  void shouldSeekToLastBlockForPositionPastTheEnd() {
    // given
    appendBlocksOfThree(1, 3);

    // when
    reader.seek(Long.MAX_VALUE);

    // then
    assertThat(readRemaining()).containsExactly("7..9");
  }

  @Test
  void shouldReadNothingFromEmptyLog() {
    // when
    reader.seek(Long.MAX_VALUE);

    // then
    assertThat(reader.hasNext()).isFalse();
    assertThatThrownBy(reader::next).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void shouldSeeBlocksAppendedAfterReachingTheEnd() {
    // given
    appendBlocksOfThree(1, 2);
    readRemaining();

    // when
    appendBlocksOfThree(7, 1);

    // then
    assertThat(readRemaining()).containsExactly("7..9");
  }

  @Test
  void shouldHaveNothingToReadWhileReadsFail() {
    // given
    appendBlocksOfThree(1, 3);
    repository.failReads(true);

    // when
    final var hasNext = reader.hasNext();

    // then
    assertThat(hasNext).isFalse();
  }

  @Test
  void shouldContinueWhereItWasOnceReadsWorkAgain() {
    // given
    appendBlocksOfThree(1, 3);
    final var firstPage = List.of(readBlock(), readBlock());
    repository.failReads(true);
    assertThat(reader.hasNext()).isFalse();

    // when
    repository.failReads(false);

    // then
    assertThat(firstPage).containsExactly("1..3", "4..6");
    assertThat(readRemaining()).containsExactly("7..9");
  }

  @Test
  void shouldRedoSeekThatFailed() {
    // given
    appendBlocksOfThree(1, 3);
    repository.failReads(true);
    reader.seek(5);
    assertThat(reader.hasNext()).isFalse();

    // when
    repository.failReads(false);

    // then
    assertThat(readRemaining()).containsExactly("4..6", "7..9");
  }

  private void appendBlocksOfThree(final long firstPosition, final int count) {
    final var blocks = new ArrayList<LogBlock>();
    for (int i = 0; i < count; i++) {
      final long low = firstPosition + 3L * i;
      final long high = low + 2;
      blocks.add(
          new LogBlock(low, high, TERM, (low + ".." + high).getBytes(StandardCharsets.UTF_8)));
    }
    repository.append(TERM, blocks);
  }

  private String readBlock() {
    final DirectBuffer block = reader.next();
    return block.getStringWithoutLengthUtf8(0, block.capacity());
  }

  private List<String> readRemaining() {
    final var blocks = new ArrayList<String>();
    while (reader.hasNext()) {
      final DirectBuffer block = reader.next();
      blocks.add(block.getStringWithoutLengthUtf8(0, block.capacity()));
    }
    return blocks;
  }
}
