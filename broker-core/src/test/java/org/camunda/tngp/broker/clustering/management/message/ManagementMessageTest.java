package org.camunda.tngp.broker.clustering.management.message;

import static org.camunda.tngp.test.util.BufferWriterUtil.*;
import static org.camunda.tngp.util.buffer.BufferUtil.*;

import java.util.Arrays;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.raft.Member;
import org.junit.Test;


public class ManagementMessageTest
{

    public static final DirectBuffer TOPIC_NAME = wrapString("test-topic");

    @Test
    public void testInvitationRequest()
    {
        final InvitationRequest invitationRequest = new InvitationRequest()
            .topicName(TOPIC_NAME)
            .partitionId(111)
            .term(222)
            .members(Arrays.asList(
                new Member(),
                new Member()
            ));

        assertEqualFieldsAfterWriteAndRead(invitationRequest,
            "topicName",
            "partitionId",
            "term",
            "members"
        );
    }

    @Test
    public void testInvitationResponse()
    {
        final InvitationResponse invitationResponse = new InvitationResponse()
            .term(111);

        assertEqualFieldsAfterWriteAndRead(invitationResponse,
            "term"
        );
    }


}
