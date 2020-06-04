/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.log;

import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.flagsOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;

import io.atomix.raft.zeebe.ZeebeEntry;
import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.zeebe.logstreams.spi.LogStorage.AppendListener;
import io.zeebe.util.TriConsumer;
import java.util.NoSuchElementException;
import java.util.Queue;
import org.agrona.concurrent.UnsafeBuffer;

final class Listener implements AppendListener {

  private final LogStorageAppender logStorageAppender;
  private final Queue<TriConsumer<ZeebeEntry, Long, Integer>> handlers;
  private final int blockLength;
  private final long entryNum;

  Listener(
      final LogStorageAppender logStorageAppender,
      final Queue<TriConsumer<ZeebeEntry, Long, Integer>> handlers,
      final int blockLength,
      final long entryNum) {
    this.logStorageAppender = logStorageAppender;
    this.handlers = handlers;
    this.blockLength = blockLength;
    this.entryNum = entryNum;
  }

  @Override
  public void onWrite(final long address) {}

  @Override
  public void onWriteError(final Throwable error) {
    LogStorageAppender.LOG.error("Failed to append block.", error);
    if (error instanceof NoSuchElementException) {
      // Not a failure. It is probably during transition to follower.
      return;
    }

    logStorageAppender.runInActor(() -> logStorageAppender.onFailure(error));
  }

  @Override
  public void onCommit(final long address) {
    releaseBackPressure();
  }

  @Override
  public void onCommitError(final long address, final Throwable error) {
    LogStorageAppender.LOG.error("Failed to commit block.", error);
    releaseBackPressure();
    logStorageAppender.runInActor(() -> logStorageAppender.onFailure(error));
  }

  @Override
  public void updateRecords(final ZeebeEntry entry, final long index) {
    final UnsafeBuffer block = new UnsafeBuffer(entry.data());
    int fragOffset = 0;
    int recordIndex = 0;
    boolean inBatch = false;

    while (fragOffset < blockLength) {
      final long position = (index << 8) + recordIndex;
      final byte flags = block.getByte(flagsOffset(fragOffset));

      if (inBatch) {
        if (DataFrameDescriptor.flagBatchEnd(flags)) {
          inBatch = false;
        }
      } else {
        inBatch = DataFrameDescriptor.flagBatchBegin(flags);
      }

      if (inBatch) {
        updateRecords(handlers.peek(), entry, position, fragOffset);
      } else {
        updateRecords(handlers.poll(), entry, position, fragOffset);
      }
      recordIndex++;
      fragOffset += DataFrameDescriptor.alignedLength(block.getInt(lengthOffset(fragOffset)));

      if (recordIndex > 0xFF) {
        throw new IllegalStateException(
            String.format(
                "The number of records in the entry with index %d exceeds the supported amount of %d",
                index, 0xFF));
      }
    }

    entry.setLowestPosition(index << 8);
    entry.setHighestPosition((index << 8) + (recordIndex - 1));
  }

  private void updateRecords(
      final TriConsumer<ZeebeEntry, Long, Integer> handler,
      final ZeebeEntry entry,
      final long position,
      final int fragOffset) {
    if (handler == null) {
      throw new IllegalStateException("Expected to have handler for entry but none was found.");
    }

    handler.accept(entry, position, fragOffset);
  }

  private void releaseBackPressure() {
    logStorageAppender.runInActor(() -> logStorageAppender.getAppendLimiter().onCommit(entryNum));
  }
}
