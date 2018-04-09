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
package io.zeebe.raft.protocol;

import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.zeebe.logstreams.impl.LoggedEventImpl;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.raft.Raft;
import io.zeebe.raft.util.RaftClusterRule;
import io.zeebe.raft.util.RaftRule;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.agrona.BitUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static io.zeebe.test.util.BufferWriterUtil.writeAndRead;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

public class RaftProtocolMessageTest
{
    public static ActorSchedulerRule actorScheduler = new ActorSchedulerRule();
    public static ServiceContainerRule serviceContainerRule = new ServiceContainerRule(actorScheduler);

    public static RaftRule raft1 = new RaftRule(actorScheduler, serviceContainerRule, "localhost", 8001, "test", 123);
    public static RaftRule raft2 = new RaftRule(actorScheduler, serviceContainerRule, "localhost", 8002, "test", 123, raft1);

    @ClassRule
    public static RaftClusterRule cluster = new RaftClusterRule(actorScheduler, serviceContainerRule, raft1, raft2);

    public static Raft raft;
    public static LogStream logStream;

    @BeforeClass
    public static void setUp()
    {
        raft = raft2.getRaft();
        logStream = raft2.getLogStream();

        // wait for raft 2 to join raft group in term 1 as follower
        waitUntil(() -> raft.getTerm() == 1);

        // freeze state by not calling doWork() on raft nodes anymore
        cluster.removeRafts(raft1, raft2);
    }

    @Test
    public void shouldReadWriteJoinRequest()
    {
        // given
        ConfigurationRequest configurationRequest = new ConfigurationRequest().setRaft(raft);

        // when
        configurationRequest = writeAndRead(configurationRequest);

        // then
        assertPartition(configurationRequest);
        assertTerm(configurationRequest);
    }

    @Test
    public void shouldReadWriteJoinResponse()
    {
        // given
        ConfigurationResponse configurationResponse = new ConfigurationResponse()
            .setRaft(raft)
            .setSucceeded(true);

        // when
        configurationResponse = writeAndRead(configurationResponse);

        // then
        assertTerm(configurationResponse);
        assertThat(configurationResponse.getMembers())
            .containsOnly(
                raft1.getSocketAddress(),
                raft2.getSocketAddress()
            );
    }

    @Test
    public void shouldReadWritePollRequest()
    {
        // given
        PollRequest pollRequest = new PollRequest()
            .setRaft(raft)
            .setLastEventPosition(111)
            .setLastEventTerm(222);

        // when
        pollRequest = writeAndRead(pollRequest);

        // then
        assertPartition(pollRequest);
        assertTerm(pollRequest);
        assertSocketAddress(pollRequest);
        assertThat(pollRequest.getLastEventPosition()).isEqualTo(111);
        assertThat(pollRequest.getLastEventTerm()).isEqualTo(222);
    }

    @Test
    public void shouldReadWritePollResponse()
    {
        // given
        PollResponse pollResponse = new PollResponse()
            .setTerm(111)
            .setGranted(true);

        // when
        pollResponse = writeAndRead(pollResponse);

        // then
        assertThat(pollResponse.getTerm()).isEqualTo(111);
        assertThat(pollResponse.isGranted()).isTrue();
    }

    @Test
    public void shouldReadWriteVoteRequest()
    {
        // given
        VoteRequest voteRequest = new VoteRequest()
            .setRaft(raft)
            .setLastEventPosition(111)
            .setLastEventTerm(222);

        // when
        voteRequest = writeAndRead(voteRequest);

        // then
        assertPartition(voteRequest);
        assertTerm(voteRequest);
        assertSocketAddress(voteRequest);
        assertThat(voteRequest.getLastEventPosition()).isEqualTo(111);
        assertThat(voteRequest.getLastEventTerm()).isEqualTo(222);
    }

    @Test
    public void shouldReadWriteVoteResponse()
    {
        // given
        VoteResponse voteResponse = new VoteResponse()
            .setTerm(111)
            .setGranted(true);

        // when
        voteResponse = writeAndRead(voteResponse);

        // then
        assertThat(voteResponse.getTerm()).isEqualTo(111);
        assertThat(voteResponse.isGranted()).isTrue();
    }

    @Test
    public void shouldReadAndWriteAppendRequestWithoutEvent()
    {
        // given
        AppendRequest appendRequest = new AppendRequest()
            .setRaft(raft)
            .setPreviousEventPosition(111)
            .setPreviousEventTerm(222);

        // when
        appendRequest = writeAndRead(appendRequest);

        // then
        assertPartition(appendRequest);
        assertTerm(appendRequest);
        assertSocketAddress(appendRequest);
        assertThat(appendRequest.getPreviousEventPosition()).isEqualTo(111);
        assertThat(appendRequest.getPreviousEventTerm()).isEqualTo(222);
        assertThat(appendRequest.getEvent()).isNull();
    }

    @Test
    public void shouldReadAndWriteAppendRequestWithEvent()
    {
        // given
        final int msgLength = BitUtil.SIZE_OF_LONG;

        final MutableDirectBuffer data = new UnsafeBuffer(new byte[DataFrameDescriptor.alignedFramedLength(msgLength)]);
        data.putInt(DataFrameDescriptor.lengthOffset(0), DataFrameDescriptor.framedLength(msgLength));
        data.putLong(DataFrameDescriptor.messageOffset(0), 123L);

        final LoggedEventImpl event = new LoggedEventImpl();
        event.wrap(data, 0);

        AppendRequest appendRequest = new AppendRequest()
            .setRaft(raft)
            .setPreviousEventPosition(111)
            .setPreviousEventTerm(222)
            .setEvent(event);

        // when
        appendRequest = writeAndRead(appendRequest);

        // then
        assertPartition(appendRequest);
        assertTerm(appendRequest);
        assertSocketAddress(appendRequest);
        assertThat(appendRequest.getPreviousEventPosition()).isEqualTo(111);
        assertThat(appendRequest.getPreviousEventTerm()).isEqualTo(222);

        final LoggedEventImpl actual = appendRequest.getEvent();
        assertThat(actual).isNotNull();
        assertThat(actual.getBuffer()).isEqualTo(data);
    }

    @Test
    public void shouldReadWriteAppendResponse()
    {
        // given
        AppendResponse appendResponse = new AppendResponse()
            .setRaft(raft)
            .setPreviousEventPosition(111)
            .setSucceeded(true);

        // when
        appendResponse = writeAndRead(appendResponse);

        // then
        assertPartition(appendResponse);
        assertTerm(appendResponse);
        assertSocketAddress(appendResponse);
        assertThat(appendResponse.getPreviousEventPosition()).isEqualTo(111);
        assertThat(appendResponse.isSucceeded()).isTrue();
    }


    protected void assertPartition(final HasPartition hasPartition)
    {
        assertThat(hasPartition.getPartitionId()).isEqualTo(logStream.getPartitionId());
    }

    protected void assertTerm(final HasTerm hasTerm)
    {
        assertThat(hasTerm.getTerm()).isEqualTo(raft.getTerm());
    }

    protected void assertSocketAddress(final HasSocketAddress hasSocketAddress)
    {
        assertThat(hasSocketAddress.getSocketAddress()).isEqualTo(raft.getSocketAddress());
    }

}
