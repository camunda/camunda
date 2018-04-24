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
package io.zeebe.broker.workflow.data;

import org.agrona.DirectBuffer;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.msgpack.value.ValueArray;

public class DeploymentEvent extends UnpackedObject
{
    private final StringProperty topicNameProp = new StringProperty("topicName");

    private final ArrayProperty<DeploymentResource> resourcesProp = new ArrayProperty<>(
            "resources",
            new DeploymentResource());

    private final ArrayProperty<DeployedWorkflow> deployedWorkflowsProp = new ArrayProperty<>(
            "deployedWorkflows",
            new DeployedWorkflow());

    private final StringProperty errorMessageProp = new StringProperty("errorMessage", "");

    public DeploymentEvent()
    {
        this
            .declareProperty(topicNameProp)
            .declareProperty(resourcesProp)
            .declareProperty(deployedWorkflowsProp)
            .declareProperty(errorMessageProp);
    }

    public ValueArray<DeployedWorkflow> deployedWorkflows()
    {
        return deployedWorkflowsProp;
    }

    public ValueArray<DeploymentResource> resources()
    {
        return resourcesProp;
    }

    public DirectBuffer getErrorMessage()
    {
        return errorMessageProp.getValue();
    }

    public DeploymentEvent setErrorMessage(String errorMessage)
    {
        this.errorMessageProp.setValue(errorMessage);
        return this;
    }

    public DeploymentEvent setErrorMessage(DirectBuffer errorMessage, int offset, int length)
    {
        this.errorMessageProp.setValue(errorMessage, offset, length);
        return this;
    }

    public DirectBuffer getTopicName()
    {
        return topicNameProp.getValue();
    }

    public DeploymentEvent setTopicName(String topicName)
    {
        this.topicNameProp.setValue(topicName);
        return this;
    }
}
