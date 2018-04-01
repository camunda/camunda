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
package io.zeebe.broker.clustering.management.message;

import static io.zeebe.test.util.BufferWriterUtil.*;
import static io.zeebe.util.buffer.BufferUtil.*;

import java.util.Arrays;

import io.zeebe.broker.clustering.api.InvitationRequest;
import io.zeebe.broker.clustering.api.InvitationResponse;
import io.zeebe.transport.SocketAddress;
import org.agrona.DirectBuffer;
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
                new SocketAddress("localhost", 8001),
                new SocketAddress("localhost", 8002)
            ));

        assertEqualFieldsAfterWriteAndRead(invitationRequest,
            "topicName",
            "partitionId",
            "replicationFactor",
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
