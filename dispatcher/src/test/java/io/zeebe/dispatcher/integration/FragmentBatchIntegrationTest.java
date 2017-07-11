/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.dispatcher.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.streamIdOffset;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.dispatcher.*;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FragmentBatchIntegrationTest
{
    private static final byte[] MSG1 = "msg1".getBytes();
    private static final byte[] MSG2 = "msg2".getBytes();
    private static final byte[] MSG3 = "msg3".getBytes();

    private Dispatcher dispatcher;
    private Subscription subscription;
    private ActorScheduler actorScheduler;

    private BlockPeek blockPeek;
    private ClaimedFragmentBatch batch;

    @Before
    public void init()
    {
        actorScheduler = ActorSchedulerBuilder.createDefaultScheduler("test");

        dispatcher = Dispatchers.create("default")
                .bufferSize(1024 * 32)
                .actorScheduler(actorScheduler)
                .build();

        subscription = dispatcher.openSubscription("test");

        blockPeek = new BlockPeek();
        batch = new ClaimedFragmentBatch();
    }

    @After
    public void cleanUp() throws Exception
    {
        dispatcher.close();
        actorScheduler.close();
    }

    @Test
    public void shouldReadCommittedBatch()
    {
        claimAndWriteFragments();

        int readBytes = subscription.peekBlock(blockPeek, 1024, false);
        assertThat(readBytes).isEqualTo(0);

        batch.commit();

        readBytes = subscription.peekBlock(blockPeek, 1024, false);
        assertThat(readBytes).isGreaterThan(0);

        final int fragmentLength = assertThatBufferContains(blockPeek.getBuffer(), 0, MSG1, 1);
        assertThatBufferContains(blockPeek.getBuffer(), alignedLength(fragmentLength), MSG2, 2);
    }

    @Test
    public void shouldNotReadAbortedBatch()
    {
        claimAndWriteFragments();

        batch.abort();

        final int readBytes = subscription.peekBlock(blockPeek, 1024, false);
        assertThat(readBytes).isEqualTo(0);
    }

    @Test
    public void shouldReadFragmentAfterCommittedBatch()
    {
        claimAndWriteFragments();
        batch.commit();

        // read batch fragments
        final int readBytes = subscription.peekBlock(blockPeek, 1024, false);
        assertThat(readBytes).isGreaterThan(0);
        blockPeek.markCompleted();

        while (dispatcher.offer(new UnsafeBuffer(MSG3), 3) <= 0)
        {
            // spin
        }

        while (subscription.peekBlock(blockPeek, 1024, false) == 0)
        {
            // skip padding
        }
        assertThatBufferContains(blockPeek.getBuffer(), 0, MSG3, 3);
    }

    @Test
    public void shouldReadFragmentAfterAbortedBatch()
    {
        claimAndWriteFragments();
        batch.abort();

        while (dispatcher.offer(new UnsafeBuffer(MSG3), 3) <= 0)
        {
            // spin
        }

        while (subscription.peekBlock(blockPeek, 1024, false) == 0)
        {
            // skip padding
        }

        assertThatBufferContains(blockPeek.getBuffer(), 0, MSG3, 3);
    }

    @Test
    public void shouldOnlyReadCompleteBatch()
    {
        final int batchSize = alignedLength(MSG1.length) + alignedLength(MSG2.length);

        claimAndWriteFragments();
        batch.commit();

        int readBytes = subscription.peekBlock(blockPeek, batchSize - 1, false);
        assertThat(readBytes).isEqualTo(0);

        readBytes = subscription.peekBlock(blockPeek, batchSize, false);
        assertThat(readBytes).isEqualTo(batchSize);
    }

    private void claimAndWriteFragments()
    {
        while (dispatcher.claim(batch, 2, MSG1.length + MSG2.length) <= 0)
        {
            // spin
        }

        final MutableDirectBuffer writeBuffer = batch.getBuffer();

        batch.nextFragment(MSG1.length, 1);
        writeBuffer.putBytes(batch.getFragmentOffset(), MSG1);

        batch.nextFragment(MSG2.length, 2);
        writeBuffer.putBytes(batch.getFragmentOffset(), MSG2);
    }

    private int assertThatBufferContains(DirectBuffer buffer, int bufferOffset, byte[] expectedMessage, int expectedStreamId)
    {
        final int length = buffer.getInt(lengthOffset(bufferOffset));
        assertThat(length).isEqualTo(expectedMessage.length);

        final int streamId = buffer.getInt(streamIdOffset(bufferOffset));
        assertThat(streamId).isEqualTo(expectedStreamId);

        final byte[] message = new byte[length];
        buffer.getBytes(messageOffset(bufferOffset), message);
        assertThat(message).isEqualTo(expectedMessage);

        return length;
    }

}
