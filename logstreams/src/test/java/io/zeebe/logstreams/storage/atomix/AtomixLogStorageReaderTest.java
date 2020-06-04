/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.storage.atomix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.atomix.raft.storage.log.entry.ConfigurationEntry;
import io.atomix.raft.zeebe.ZeebeEntry;
import io.atomix.raft.zeebe.ZeebeLogAppender.AppendListener;
import io.atomix.storage.journal.Indexed;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.spi.LogStorageReader;
import io.zeebe.logstreams.util.AtomixLogStorageRule;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.groups.Tuple;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public final class AtomixLogStorageReaderTest {
  private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;

  private final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final AtomixLogStorageRule storageRule = new AtomixLogStorageRule(temporaryFolder);
  @Rule public final RuleChain chain = RuleChain.outerRule(temporaryFolder).around(storageRule);
  private final DirectBuffer buffer = new UnsafeBuffer();

  @Test
  public void shouldLookUpAddress() {
    // given
    final var reader = storageRule.get().newReader();
    final var firstEntry = append(1, 4, allocateData(1));
    final var secondEntry = append(5, 8, allocateData(2));

    // when
    final var firstEntryAddresses =
        IntStream.range(1, 4).mapToLong(reader::lookUpApproximateAddress);
    final var secondEntryAddresses =
        IntStream.range(5, 8).mapToLong(reader::lookUpApproximateAddress);

    // then
    assertThat(firstEntryAddresses).allMatch(address -> address == firstEntry.index());
    assertThat(secondEntryAddresses).allMatch(address -> address == secondEntry.index());
  }

  @Test
  public void shouldReturnInvalidOnEmptyLookUp() {
    // given
    final var reader = storageRule.get().newReader();

    // when
    final var address = reader.lookUpApproximateAddress(1);

    // then
    assertThat(address).isEqualTo(LogStorage.OP_RESULT_INVALID_ADDR);
  }

  @Test
  public void shouldReturnNoDataOnEmptyRead() {
    // given
    final var reader = storageRule.get().newReader();

    // when
    final var result = reader.read(buffer, 1);

    // then
    assertThat(result).isEqualTo(LogStorage.OP_RESULT_NO_DATA);
  }

  @Test
  public void shouldReadEntry() {
    // given
    final var reader = storageRule.get().newReader();
    final var firstEntry = append(1, 4, allocateData(1));
    final var secondEntry = append(5, 8, allocateData(2));

    // when
    final var firstEntryResults = read(reader, firstEntry.index());
    final var secondEntryResults = read(reader, secondEntry.index());

    // then
    assertThat(firstEntryResults).isEqualTo(tuple(1, secondEntry.index()));
    assertThat(secondEntryResults).isEqualTo(tuple(2, secondEntry.index() + 1));
  }

  @Test
  public void shouldLookUpEntryWithGaps() {
    // given
    final var reader = storageRule.get().newReader();

    // when
    final var first = append(1, 4, allocateData(1));
    final var second =
        storageRule
            .getRaftLog()
            .writer()
            .append(new ConfigurationEntry(1, System.currentTimeMillis(), Collections.emptyList()));
    final var third = append(5, 8, allocateData(2));

    // then
    assertThat(reader.lookUpApproximateAddress(3)).isEqualTo(first.index());
    assertThat(reader.lookUpApproximateAddress(6)).isEqualTo(third.index());

    // this does not point to the next real ZeebeEntry, but is just an approximate
    assertThat(read(reader, first.index())).isEqualTo(tuple(1, second.index()));

    // reading the second entry (a non ZeebeEntry) should find the next correct Zeebe entry
    assertThat(read(reader, second.index())).isEqualTo(tuple(2, third.index() + 1));
    assertThat(read(reader, third.index())).isEqualTo(tuple(2, third.index() + 1));
  }

  @Test
  public void shouldReadLastZeebeEntry() {
    // given
    final var reader = storageRule.get().newReader();
    final var expected = append(1, 4, allocateData(1));
    storageRule
        .getRaftLog()
        .writer()
        .append(new ConfigurationEntry(1, System.currentTimeMillis(), Collections.emptyList()));

    // when
    final var address = reader.readLastBlock(buffer);

    // then
    assertThat(address).isEqualTo(expected.index() + 1);
    assertThat(buffer.getInt(0, BYTE_ORDER)).isEqualTo(1);
  }

  @Test
  public void shouldReturnEmptyIfLogIsEmpty() {
    // given
    final var reader = storageRule.get().newReader();

    // when
    final var isEmpty = reader.isEmpty();

    // then
    assertThat(isEmpty).isTrue();
  }

  @Test
  public void shouldReturnEmptyIfLogContainsNonZeebeEntries() {
    // given
    final var reader = storageRule.get().newReader();

    // when
    final var entry =
        storageRule
            .getRaftLog()
            .writer()
            .append(new ConfigurationEntry(1, System.currentTimeMillis(), Collections.emptyList()));
    final var isEmpty = reader.isEmpty();

    // then
    assertThat(entry).isNotNull();
    assertThat(isEmpty).isTrue();
  }

  @Test
  public void shouldNotReturnEmptyIfAtLeastOneZeebeEntryPresent() {
    // given
    final var reader = storageRule.get().newReader();

    // when
    final var entry =
        storageRule
            .getRaftLog()
            .writer()
            .append(new ConfigurationEntry(1, System.currentTimeMillis(), Collections.emptyList()));
    final var expected = append(1, 4, allocateData(1));

    final var isEmpty = reader.isEmpty();

    // then
    assertThat(entry).isNotNull();
    assertThat(expected).isNotNull();
    assertThat(isEmpty).isFalse();
  }

  private Indexed<ZeebeEntry> append(
      final long lowestPosition, final long highestPosition, final ByteBuffer data) {
    final var future = new CompletableFuture<Indexed<ZeebeEntry>>();
    final var listener = new Listener(future, lowestPosition, highestPosition);
    storageRule.appendEntry(data, listener);
    return future.join();
  }

  private Tuple read(final LogStorageReader reader, final long address) {
    final var result = reader.read(buffer, address);
    return tuple(buffer.getInt(0, BYTE_ORDER), result);
  }

  private ByteBuffer allocateData(final int value) {
    return ByteBuffer.allocate(4).order(BYTE_ORDER).putInt(0, value);
  }

  private static final class Listener implements AppendListener {
    private final CompletableFuture<Indexed<ZeebeEntry>> future;
    private final long lowestPosition;
    private final long highestPosition;

    private Listener(
        final CompletableFuture<Indexed<ZeebeEntry>> future,
        long lowestPosition,
        long highestPosition) {
      this.future = future;
      this.lowestPosition = lowestPosition;
      this.highestPosition = highestPosition;
    }

    @Override
    public void onWrite(final Indexed<ZeebeEntry> indexed) {
      future.complete(indexed);
    }

    @Override
    public void onWriteError(final Throwable throwable) {
      future.completeExceptionally(throwable);
    }

    @Override
    public void updateRecords(ZeebeEntry entry, long index) throws IllegalStateException {
      entry.setLowestPosition(lowestPosition);
      entry.setHighestPosition(highestPosition);
    }

    @Override
    public void onCommit(final Indexed<ZeebeEntry> indexed) {
      // do nothing
    }

    @Override
    public void onCommitError(final Indexed<ZeebeEntry> indexed, final Throwable throwable) {
      // do nothing
    }
  }
}
