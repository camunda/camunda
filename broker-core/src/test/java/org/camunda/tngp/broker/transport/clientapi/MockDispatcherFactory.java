package org.camunda.tngp.broker.transport.clientapi;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static org.camunda.tngp.dispatcher.impl.log.LogBufferAppender.RESULT_PADDING_AT_END_OF_PARTITION;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class MockDispatcherFactory
{
    protected Answer<Long> claimAnswer;
    protected Answer<Long> paddingAnswer = (invocation) -> (long) RESULT_PADDING_AT_END_OF_PARTITION;
    protected Answer<Long> failAnswer = (invocation) -> -1L;

    protected int streamId;

    public MockDispatcherFactory(int streamId, UnsafeBuffer sendBuffer)
    {
        this.streamId = streamId;
        this.claimAnswer = new ClaimAnswer(sendBuffer);
    }

    List<Answer<Long>> answers = new ArrayList<>();

    public MockDispatcherFactory padding()
    {
        this.answers.add(paddingAnswer);
        return this;
    }

    public MockDispatcherFactory claim()
    {
        this.answers.add(claimAnswer);
        return this;
    }

    public MockDispatcherFactory fail()
    {
        this.answers.add(failAnswer);
        return this;
    }

    public MockDispatcherFactory then()
    {
        return this;
    }

    public MockDispatcherFactory thatDoes()
    {
        return this;
    }

    public Dispatcher done()
    {
        final Iterator<Answer<Long>> answersIt = answers.iterator();

        final Dispatcher dispatcher = mock(Dispatcher.class);
        when(dispatcher.claim(any(ClaimedFragment.class), anyInt(), eq(streamId))).thenAnswer((invocation) -> answersIt.next().answer(invocation));
        return dispatcher;
    }

    public static MockDispatcherFactory dispatcherOn(int streamId, UnsafeBuffer sendBuffer)
    {
        return new MockDispatcherFactory(streamId, sendBuffer);
    }

    protected static class ClaimAnswer implements Answer<Long>
    {
        protected UnsafeBuffer sendBuffer;
        long offset = 0;

        public ClaimAnswer(UnsafeBuffer sendBuffer)
        {
            this.sendBuffer = sendBuffer;
        }

        @Override
        public Long answer(InvocationOnMock invocation) throws Throwable
        {
            final ClaimedFragment claimedFragment = (ClaimedFragment) invocation.getArguments()[0];
            final int length = (int) invocation.getArguments()[1];

            claimedFragment.wrap(sendBuffer, (int) offset, alignedLength(length));

            offset += alignedLength(length);
            return offset;
        }
    }
}
