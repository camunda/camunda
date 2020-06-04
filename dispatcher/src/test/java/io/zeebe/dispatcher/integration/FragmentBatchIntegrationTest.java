/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.dispatcher.integration;

import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.alignedFramedLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.streamIdOffset;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.dispatcher.BlockPeek;
import io.zeebe.dispatcher.ClaimedFragmentBatch;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class FragmentBatchIntegrationTest {
  private static final byte[] MSG1 = "msg1".getBytes();
  private static final byte[] MSG2 = "msg2".getBytes();
  private static final byte[] MSG3 = "msg3".getBytes();

  @Rule public final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(1);

  private Dispatcher dispatcher;
  private Subscription subscription;

  private BlockPeek blockPeek;
  private ClaimedFragmentBatch batch;

  @Before
  public void init() {
    dispatcher =
        Dispatchers.create("default")
            .bufferSize((int) ByteValue.ofKilobytes(32))
            .actorScheduler(actorSchedulerRule.get())
            .build();

    subscription = dispatcher.openSubscription("test");

    blockPeek = new BlockPeek();
    batch = new ClaimedFragmentBatch();
  }

  @After
  public void cleanUp() {
    dispatcher.close();
  }

  @Test
  public void shouldReadCommittedBatch() {
    claimAndWriteFragments();

    int readBytes = subscription.peekBlock(blockPeek, 1024, false);
    assertThat(readBytes).isEqualTo(0);

    batch.commit((a, b, c) -> {});

    readBytes = subscription.peekBlock(blockPeek, 1024, false);
    assertThat(readBytes).isGreaterThan(0);

    final int fragmentLength = assertThatBufferContains(blockPeek.getBuffer(), 0, MSG1, 1);
    assertThatBufferContains(blockPeek.getBuffer(), alignedFramedLength(fragmentLength), MSG2, 2);
  }

  @Test
  public void shouldNotReadAbortedBatch() {
    claimAndWriteFragments();

    batch.abort();

    final int readBytes = subscription.peekBlock(blockPeek, 1024, false);
    assertThat(readBytes).isEqualTo(0);
  }

  @Test
  public void shouldReadFragmentAfterCommittedBatch() {
    claimAndWriteFragments();
    batch.commit((a, b, c) -> {});

    // read batch fragments
    final int readBytes = subscription.peekBlock(blockPeek, 1024, false);
    assertThat(readBytes).isGreaterThan(0);
    blockPeek.markCompleted();

    claimAndWriteMsgThree();

    while (subscription.peekBlock(blockPeek, 1024, false) == 0) {
      // skip padding
    }
    assertThatBufferContains(blockPeek.getBuffer(), 0, MSG3, 3);
  }

  @Test
  public void shouldReadFragmentAfterAbortedBatch() {
    claimAndWriteFragments();
    batch.abort();

    claimAndWriteMsgThree();

    while (subscription.peekBlock(blockPeek, 1024, false) == 0) {
      // skip padding
    }

    assertThatBufferContains(blockPeek.getBuffer(), 0, MSG3, 3);
  }

  @Test
  public void shouldOnlyReadCompleteBatch() {
    final int batchSize = alignedFramedLength(MSG1.length) + alignedFramedLength(MSG2.length);

    claimAndWriteFragments();
    batch.commit((a, b, c) -> {});

    int readBytes = subscription.peekBlock(blockPeek, batchSize - 1, false);
    assertThat(readBytes).isEqualTo(0);

    readBytes = subscription.peekBlock(blockPeek, batchSize, false);
    assertThat(readBytes).isEqualTo(batchSize);
  }

  private void claimAndWriteFragments() {
    while (dispatcher.claim(batch, 2, MSG1.length + MSG2.length) <= 0) {
      // spin
    }

    final MutableDirectBuffer writeBuffer = batch.getBuffer();

    batch.nextFragment(MSG1.length, 1);
    writeBuffer.putBytes(batch.getFragmentOffset(), MSG1);

    batch.nextFragment(MSG2.length, 2);
    writeBuffer.putBytes(batch.getFragmentOffset(), MSG2);
  }

  private void claimAndWriteMsgThree() {
    while (dispatcher.claim(batch, 1, MSG3.length) <= 0) {
      // spin
    }

    final MutableDirectBuffer writeBuffer = batch.getBuffer();

    batch.nextFragment(MSG3.length, 3);
    writeBuffer.putBytes(batch.getFragmentOffset(), MSG3);

    batch.commit((a, b, c) -> {});
  }

  private int assertThatBufferContains(
      final DirectBuffer buffer,
      final int bufferOffset,
      final byte[] expectedMessage,
      final int expectedStreamId) {
    final int framedLength = buffer.getInt(lengthOffset(bufferOffset));
    final int fragmentLength = framedLength - DataFrameDescriptor.HEADER_LENGTH;
    assertThat(fragmentLength).isEqualTo(expectedMessage.length);

    final int streamId = buffer.getInt(streamIdOffset(bufferOffset));
    assertThat(streamId).isEqualTo(expectedStreamId);

    final byte[] message = new byte[fragmentLength];
    buffer.getBytes(messageOffset(bufferOffset), message);
    assertThat(message).isEqualTo(expectedMessage);

    return fragmentLength;
  }
}
