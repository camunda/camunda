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
package io.zeebe.broker.clustering.management.handler;

import org.agrona.DirectBuffer;

import io.zeebe.broker.clustering.management.ClusterManager;
import io.zeebe.broker.system.deployment.handler.WorkflowRequestMessageHandler;
import io.zeebe.clustering.management.CreatePartitionRequestDecoder;
import io.zeebe.clustering.management.CreateWorkflowRequestEncoder;
import io.zeebe.clustering.management.DeleteWorkflowMessageDecoder;
import io.zeebe.clustering.management.InvitationRequestEncoder;
import io.zeebe.clustering.management.InvitationResponseDecoder;
import io.zeebe.clustering.management.MessageHeaderDecoder;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerRequestHandler;

public class ClusterManagerFragmentHandler implements ServerMessageHandler, ServerRequestHandler
{
    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();

    private final ClusterManager clusterManager;
    private final WorkflowRequestMessageHandler workflowRequestMessageHandler;

    public ClusterManagerFragmentHandler(final ClusterManager clusterManager, final WorkflowRequestMessageHandler workflowRequestMessageHandler)
    {
        this.clusterManager = clusterManager;
        this.workflowRequestMessageHandler = workflowRequestMessageHandler;
    }

    @Override
    public boolean onRequest(ServerOutput output, RemoteAddress remoteAddress, DirectBuffer buffer, int offset,
            int length, long requestId)
    {
        messageHeaderDecoder.wrap(buffer, offset);

        final int schemaId = messageHeaderDecoder.schemaId();

        if (InvitationResponseDecoder.SCHEMA_ID == schemaId)
        {
            final int templateId = messageHeaderDecoder.templateId();
            switch (templateId)
            {
                case InvitationRequestEncoder.TEMPLATE_ID:
                {
                    return clusterManager.onInvitationRequest(buffer, offset, length, output, remoteAddress, requestId);
                }
                case CreateWorkflowRequestEncoder.TEMPLATE_ID:
                {
                    return workflowRequestMessageHandler.onCreateWorkflowRequest(buffer, offset, length, remoteAddress, requestId);
                }
                case CreatePartitionRequestDecoder.TEMPLATE_ID:
                {
                    return clusterManager.onCreatePartitionRequest(buffer, offset, length, output, remoteAddress, requestId);
                }
                default:
                {
                    // TODO: send error response
                    return true;
                }
            }
        }
        else
        {
            return true;
        }
    }

    @Override
    public boolean onMessage(ServerOutput output, RemoteAddress remoteAddress, DirectBuffer buffer, int offset,
            int length)
    {
        messageHeaderDecoder.wrap(buffer, offset);

        final int schemaId = messageHeaderDecoder.schemaId();

        if (CreatePartitionRequestDecoder.SCHEMA_ID == schemaId)
        {
            final int templateId = messageHeaderDecoder.templateId();
            switch (templateId)
            {
                case DeleteWorkflowMessageDecoder.TEMPLATE_ID:
                {
                    workflowRequestMessageHandler.onDeleteWorkflowMessage(buffer, offset, length);
                    break;
                }
                default:
                {
                    // ignore
                }
            }
        }
        return true;
    }

}
