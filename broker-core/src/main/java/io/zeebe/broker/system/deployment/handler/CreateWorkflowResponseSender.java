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
package io.zeebe.broker.system.deployment.handler;

import io.zeebe.broker.system.deployment.message.CreateWorkflowResponse;
import io.zeebe.transport.*;

public class CreateWorkflowResponseSender
{
    private final CreateWorkflowResponse response = new CreateWorkflowResponse();

    private final ServerResponse serverResponse = new ServerResponse();

    private final ServerOutput output;

    public CreateWorkflowResponseSender(ServerTransport managementServer)
    {
        this.output = managementServer.getOutput();
    }

    public boolean sendCreateWorkflowResponse(
            int partitionId,
            long workflowKey,
            long deploymentKey,
            long requestId,
            int requestStreamId)
    {
        response
            .partitionId(partitionId)
            .workflowKey(workflowKey)
            .deploymentKey(deploymentKey);

        serverResponse.reset()
                .requestId(requestId)
                .remoteStreamId(requestStreamId)
                .writer(response);

        return output.sendResponse(serverResponse);
    }

}
