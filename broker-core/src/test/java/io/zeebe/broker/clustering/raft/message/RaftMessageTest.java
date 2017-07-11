/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.raft.message;

import static io.zeebe.test.util.BufferWriterUtil.*;
import static io.zeebe.util.buffer.BufferUtil.*;

import org.agrona.DirectBuffer;
import org.junit.Test;


public class RaftMessageTest
{

    public static final DirectBuffer TOPIC_NAME = wrapString("test-topic");

    @Test
    public void testAppendRequest()
    {

        final AppendRequest appendRequest = new AppendRequest()
            .topicName(TOPIC_NAME)
            .partitionId(111)
            .term(222)
            .previousEntryPosition(333)
            .previousEntryTerm(444)
            .commitPosition(555);

        assertEqualFieldsAfterWriteAndRead(appendRequest,
            "topicName",
            "partitionId",
            "term",
            "previousEntryPosition",
            "previousEntryTerm",
            "commitPosition"
        );
    }

    @Test
    public void testAppendResponse()
    {
        final AppendResponse appendResponse = new AppendResponse()
            .topicName(TOPIC_NAME)
            .partitionId(111)
            .term(222)
            .succeeded(true)
            .entryPosition(333);

        assertEqualFieldsAfterWriteAndRead(appendResponse,
            "topicName",
            "partitionId",
            "term",
            "succeeded",
            "entryPosition"
        );
    }


    @Test
    public void testConfigureRequest()
    {
        final ConfigureRequest configureRequest = new ConfigureRequest()
            .topicName(TOPIC_NAME)
            .partitionId(111)
            .term(222)
            .configurationEntryPosition(333)
            .configurationEntryTerm(444);


        assertEqualFieldsAfterWriteAndRead(configureRequest,
            "topicName",
            "partitionId",
            "term",
            "configurationEntryPosition",
            "configurationEntryTerm");
    }

    @Test
    public void testConfigureResponse()
    {
        final ConfigureResponse configureResponse = new ConfigureResponse()
            .term(111);

        assertEqualFieldsAfterWriteAndRead(configureResponse,
            "term"
        );
    }

    @Test
    public void testJoinRequest()
    {
        final JoinRequest joinRequest = new JoinRequest()
            .topicName(TOPIC_NAME)
            .partitionId(111);

        assertEqualFieldsAfterWriteAndRead(joinRequest,
            "topicName",
            "partitionId"
        );
    }

    @Test
    public void testJoinResponse()
    {
        final JoinResponse joinResponse = new JoinResponse()
            .term(111)
            .configurationEntryPosition(222)
            .configurationEntryTerm(333)
            .succeeded(true);

        assertEqualFieldsAfterWriteAndRead(joinResponse,
            "term",
            "configurationEntryPosition",
            "configurationEntryTerm",
            "succeeded"
        );
    }

    @Test
    public void testLeaveRequest()
    {
        final LeaveRequest leaveRequest = new LeaveRequest()
            .topicName(TOPIC_NAME)
            .partitionId(111);

        assertEqualFieldsAfterWriteAndRead(leaveRequest,
            "topicName",
            "partitionId"
        );
    }

    @Test
    public void testLeaveResponse()
    {
        final LeaveResponse leaveResponse = new LeaveResponse()
            .term(111)
            .configurationEntryPosition(222)
            .configurationEntryTerm(333)
            .succeeded(true);

        assertEqualFieldsAfterWriteAndRead(leaveResponse,
            "term",
            "configurationEntryPosition",
            "configurationEntryTerm",
            "succeeded"
        );
    }

    @Test
    public void testPollRequest()
    {
        final PollRequest pollRequest = new PollRequest()
            .topicName(TOPIC_NAME)
            .partitionId(111)
            .term(222)
            .lastEntryPosition(333)
            .lastEntryTerm(444);

        assertEqualFieldsAfterWriteAndRead(pollRequest,
            "topicName",
            "partitionId",
            "term",
            "lastEntryPosition",
            "lastEntryTerm"
        );
    }

    @Test
    public void testPollResponse()
    {
        final PollResponse pollResponse = new PollResponse()
            .term(111)
            .granted(true);

        assertEqualFieldsAfterWriteAndRead(pollResponse,
            "term",
            "granted"
        );
    }

    @Test
    public void testVoteRequest()
    {
        final VoteRequest voteRequest = new VoteRequest()
            .topicName(TOPIC_NAME)
            .partitionId(111)
            .term(222)
            .lastEntryPosition(333)
            .lastEntryTerm(444);

        assertEqualFieldsAfterWriteAndRead(voteRequest,
            "topicName",
            "partitionId",
            "term",
            "lastEntryPosition",
            "lastEntryTerm"
        );
    }

    @Test
    public void testVoteResponse()
    {
        final VoteResponse voteResponse = new VoteResponse()
            .term(111)
            .granted(true);

        assertEqualFieldsAfterWriteAndRead(voteResponse,
            "term",
            "granted"
        );
    }

}
