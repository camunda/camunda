/**
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
package io.zeebe.dispatcher;

import static org.agrona.BitUtil.align;
import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.dispatcher.impl.PositionUtil.position;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.FRAME_ALIGNMENT;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.HEADER_LENGTH;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.Charset;

import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.Position;
import io.zeebe.dispatcher.impl.DispatcherContext;
import io.zeebe.dispatcher.impl.log.LogBuffer;
import io.zeebe.dispatcher.impl.log.LogBufferAppender;
import io.zeebe.dispatcher.impl.log.LogBufferPartition;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class DispatcherTest
{

    static final byte[] A_MSG_PAYLOAD = "some bytes".getBytes(Charset.forName("utf-8"));
    static final int A_MSG_PAYLOAD_LENGTH = A_MSG_PAYLOAD.length;
    static final int A_FRAGMENT_LENGTH = align(A_MSG_PAYLOAD_LENGTH + HEADER_LENGTH, FRAME_ALIGNMENT);
    static final UnsafeBuffer A_MSG = new UnsafeBuffer(A_MSG_PAYLOAD);
    static final int AN_INITIAL_PARTITION_ID = 0;
    static final int A_LOG_WINDOW_LENGTH = 128;
    static final int A_PARITION_SIZE = 1024;
    static final int A_STREAM_ID = 20;

    Dispatcher dispatcher;
    LogBuffer logBuffer;
    LogBufferPartition logBufferPartition0;
    LogBufferPartition logBufferPartition1;
    LogBufferPartition logBufferPartition2;
    LogBufferAppender logAppender;
    Position publisherLimit;
    Position publisherPosition;
    Subscription subscriptionSpy;
    FragmentHandler fragmentHandler;
    ClaimedFragment claimedFragment;
    Position subscriberPosition;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setup()
    {
        logBuffer = mock(LogBuffer.class);
        logBufferPartition0 = mock(LogBufferPartition.class);
        logBufferPartition1 = mock(LogBufferPartition.class);
        logBufferPartition2 = mock(LogBufferPartition.class);

        when(logBuffer.getInitialPartitionId()).thenReturn(AN_INITIAL_PARTITION_ID);
        when(logBuffer.getPartitionCount()).thenReturn(3);
        when(logBuffer.getPartitionSize()).thenReturn(A_PARITION_SIZE);
        when(logBuffer.getPartition(0)).thenReturn(logBufferPartition0);
        when(logBuffer.getPartition(1)).thenReturn(logBufferPartition1);
        when(logBuffer.getPartition(2)).thenReturn(logBufferPartition2);

        logAppender = mock(LogBufferAppender.class);
        publisherLimit = mock(Position.class);
        publisherPosition = mock(Position.class);
        fragmentHandler = mock(FragmentHandler.class);
        claimedFragment = mock(ClaimedFragment.class);
        subscriberPosition = mock(Position.class);

        dispatcher = new Dispatcher(logBuffer,
                logAppender,
                publisherLimit,
                publisherPosition,
                A_LOG_WINDOW_LENGTH,
                new String[0],
                Dispatcher.MODE_PUB_SUB,
                mock(DispatcherContext.class),
                "test")
        {
            @Override
            protected Subscription newSubscription(int subscriberId, String subscriberName)
            {
                subscriptionSpy = spy(new Subscription(subscriberPosition, subscriberId, subscriberName, dispatcher));
                return subscriptionSpy;
            }
        };
    }

    @Test
    public void shouldNotWriteBeyondPublisherLimit()
    {
        // given
        // position of 0,0
        when(logBuffer.getActivePartitionIdVolatile()).thenReturn(0);
        when(logBufferPartition0.getTailCounterVolatile()).thenReturn(0);
        // publisher limit of 0
        when(publisherLimit.getVolatile()).thenReturn(position(0, 0));

        // if
        final long newPosition = dispatcher.offer(A_MSG, 0, A_MSG_PAYLOAD_LENGTH);

        // then
        assertThat(newPosition).isEqualTo(-1);

        verify(publisherLimit).getVolatile();
        verifyNoMoreInteractions(publisherLimit);
        verifyNoMoreInteractions(logAppender);
        verify(logBuffer).getActivePartitionIdVolatile();
        verify(logBuffer).getPartition(0);
        verify(logBufferPartition0).getTailCounterVolatile();
    }

    @Test
    public void shouldNotClaimBeyondPublisherLimit()
    {
        // given
        // position of 0,0
        when(logBuffer.getActivePartitionIdVolatile()).thenReturn(0);
        when(logBufferPartition0.getTailCounterVolatile()).thenReturn(0);
        // publisher limit of 0
        when(publisherLimit.getVolatile()).thenReturn(position(0, 0));

        // if
        final long newPosition = dispatcher.claim(claimedFragment, A_MSG_PAYLOAD_LENGTH);

        // then
        assertThat(newPosition).isEqualTo(-1);

        verify(publisherLimit).getVolatile();
        verifyNoMoreInteractions(publisherLimit);
        verifyNoMoreInteractions(logAppender);
        verifyNoMoreInteractions(claimedFragment);
        verify(logBuffer).getActivePartitionIdVolatile();
        verify(logBuffer).getPartition(0);
        verify(logBufferPartition0).getTailCounterVolatile();
    }

    @Test
    public void shouldWriteUnfragmented()
    {
        // given
        // position is 0,0
        when(logBuffer.getActivePartitionIdVolatile()).thenReturn(0);
        when(logBufferPartition0.getTailCounterVolatile()).thenReturn(0);
        when(publisherLimit.getVolatile()).thenReturn(position(0, A_FRAGMENT_LENGTH));

        when(logAppender.appendFrame(logBufferPartition0, 0, A_MSG, 0, A_MSG_PAYLOAD_LENGTH, A_STREAM_ID)).thenReturn(A_FRAGMENT_LENGTH);

        // if
        final long newPosition = dispatcher.offer(A_MSG, 0, A_MSG_PAYLOAD_LENGTH, A_STREAM_ID);

        // then
        assertThat(newPosition).isEqualTo(position(0, A_FRAGMENT_LENGTH));

        verify(logAppender).appendFrame(logBufferPartition0, 0, A_MSG, 0, A_MSG_PAYLOAD_LENGTH, A_STREAM_ID);

        verify(publisherLimit).getVolatile();
        verify(publisherPosition).proposeMaxOrdered(position(0, A_FRAGMENT_LENGTH));

        verify(logBuffer).getActivePartitionIdVolatile();
        verify(logBuffer).getPartition(0);
        verify(logBufferPartition0).getTailCounterVolatile();

    }

    @Test
    public void shouldClaimFragment()
    {
        // given
        // position is 0,0
        when(logBuffer.getActivePartitionIdVolatile()).thenReturn(0);
        when(logBufferPartition0.getTailCounterVolatile()).thenReturn(0);
        when(publisherLimit.getVolatile()).thenReturn(position(0, A_FRAGMENT_LENGTH));

        when(logAppender.claim(logBufferPartition0, 0, claimedFragment, A_MSG_PAYLOAD_LENGTH, A_STREAM_ID)).thenReturn(A_FRAGMENT_LENGTH);

        // if
        final long newPosition = dispatcher.claim(claimedFragment, A_MSG_PAYLOAD_LENGTH, A_STREAM_ID);

        // then
        assertThat(newPosition).isEqualTo(position(0, A_FRAGMENT_LENGTH));

        verify(logAppender).claim(logBufferPartition0, 0, claimedFragment, A_MSG_PAYLOAD_LENGTH, A_STREAM_ID);

        verify(publisherLimit).getVolatile();
        verify(publisherPosition).proposeMaxOrdered(position(0, A_FRAGMENT_LENGTH));

        verify(logBuffer).getActivePartitionIdVolatile();
        verify(logBuffer).getPartition(0);
        verify(logBufferPartition0).getTailCounterVolatile();

    }

    @Test
    public void shouldRollPartitionOnPartitionFilled()
    {
        // given
        // position is 0,0
        when(logBuffer.getActivePartitionIdVolatile()).thenReturn(0);
        when(logBufferPartition0.getTailCounterVolatile()).thenReturn(0);
        when(publisherLimit.getVolatile()).thenReturn(position(0, A_FRAGMENT_LENGTH));

        when(logAppender.appendFrame(logBufferPartition0, 0, A_MSG, 0, A_MSG_PAYLOAD_LENGTH, A_STREAM_ID)).thenReturn(-2);

        // if
        final long newPosition = dispatcher.offer(A_MSG, 0, A_MSG_PAYLOAD_LENGTH, A_STREAM_ID);

        // then
        assertThat(newPosition).isEqualTo(-2);

        verify(publisherLimit).getVolatile();
        verify(publisherPosition).proposeMaxOrdered(-2);

        verify(logBuffer).getActivePartitionIdVolatile();
        verify(logBuffer).getPartition(0);
        verify(logBuffer).onActiveParitionFilled(0);

        verify(logBufferPartition0).getTailCounterVolatile();

    }

    @Test
    public void shouldIgnoreWriteIfPastPartitionEnd()
    {
        // given
        // position is 0,0
        when(logBuffer.getActivePartitionIdVolatile()).thenReturn(0);
        when(logBufferPartition0.getTailCounterVolatile()).thenReturn(0);
        when(publisherLimit.getVolatile()).thenReturn(position(0, A_FRAGMENT_LENGTH));

        when(logAppender.appendFrame(logBufferPartition0, 0, A_MSG, 0, A_MSG_PAYLOAD_LENGTH, A_STREAM_ID)).thenReturn(-1);

        // if
        final long newPosition = dispatcher.offer(A_MSG, 0, A_MSG_PAYLOAD_LENGTH);

        // then
        assertThat(newPosition).isEqualTo(-1);

        verify(publisherLimit).getVolatile();
        verify(publisherPosition).proposeMaxOrdered(-1);

        verify(logBuffer).getActivePartitionIdVolatile();
        verify(logBuffer).getPartition(0);
        verify(logBuffer, times(0)).onActiveParitionFilled(anyInt());

        verify(logBufferPartition0).getTailCounterVolatile();
    }

    @Test
    public void shouldReadFragmentsFromPartition()
    {
        // given
        dispatcher.doOpenSubscription("test");
        when(subscriberPosition.get()).thenReturn(0L);
        when(publisherPosition.get()).thenReturn(position(0, A_FRAGMENT_LENGTH));

        doReturn(1).when(subscriptionSpy).pollFragments(logBufferPartition0, fragmentHandler, 0, 0, 2, position(0, A_FRAGMENT_LENGTH), false);

        // if
        final int fragmentsRead = subscriptionSpy.poll(fragmentHandler, 2);

        // then
        assertThat(fragmentsRead).isEqualTo(1);
        verify(subscriberPosition).get();
        verify(subscriptionSpy).pollFragments(logBufferPartition0, fragmentHandler, 0, 0, 2, position(0, A_FRAGMENT_LENGTH), false);
    }

    @Test
    public void shouldNotReadBeyondPublisherPosition()
    {
        // given
        dispatcher.doOpenSubscription("test");
        when(subscriptionSpy.getPosition()).thenReturn(0L);
        when(publisherPosition.get()).thenReturn(0L);

        // if
        final int fragmentsRead = subscriptionSpy.poll(fragmentHandler, 1);

        // then
        assertThat(fragmentsRead).isEqualTo(0);
    }

    @Test
    public void shouldUpdatePublisherLimit()
    {
        when(subscriberPosition.get()).thenReturn(position(10, 100));

        dispatcher.doOpenSubscription("test");
        dispatcher.updatePublisherLimit();

        verify(publisherLimit).proposeMaxOrdered(position(10, 100 + A_LOG_WINDOW_LENGTH));
    }

    @Test
    public void shouldUpdatePublisherLimitToNextPartition()
    {
        when(subscriberPosition.get()).thenReturn(position(10, A_PARITION_SIZE - A_LOG_WINDOW_LENGTH));

        dispatcher.doOpenSubscription("test");
        dispatcher.updatePublisherLimit();

        verify(publisherLimit).proposeMaxOrdered(position(11, A_LOG_WINDOW_LENGTH));
    }

    @Test
    public void shouldReadFragmentsFromPartitionOnPeekAndConsume()
    {
        // given
        dispatcher.doOpenSubscription("test");
        when(subscriberPosition.get()).thenReturn(0L);
        when(publisherPosition.get()).thenReturn(position(0, A_FRAGMENT_LENGTH));

        doReturn(1).when(subscriptionSpy).pollFragments(logBufferPartition0, fragmentHandler, 0, 0, 2, position(0, A_FRAGMENT_LENGTH), true);

        // if
        final int fragmentsRead = subscriptionSpy.peekAndConsume(fragmentHandler, 2);

        // then
        assertThat(fragmentsRead).isEqualTo(1);
        verify(subscriberPosition).get();
        verify(subscriptionSpy).pollFragments(logBufferPartition0, fragmentHandler, 0, 0, 2, position(0, A_FRAGMENT_LENGTH), true);
    }

    @Test
    public void shouldNotOpenSubscriptionWithSameName()
    {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("subscription with name 's1' already exists");

        dispatcher.doOpenSubscription("s1");
        dispatcher.doOpenSubscription("s1");
    }

}
