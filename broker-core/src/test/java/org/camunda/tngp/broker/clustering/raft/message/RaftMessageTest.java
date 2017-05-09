package org.camunda.tngp.broker.clustering.raft.message;

import static org.camunda.tngp.test.util.BufferWriterUtil.*;
import static org.camunda.tngp.util.buffer.BufferUtil.*;

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
