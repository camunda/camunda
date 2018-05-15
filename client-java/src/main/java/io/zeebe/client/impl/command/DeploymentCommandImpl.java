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
package io.zeebe.client.impl.command;

import com.fasterxml.jackson.annotation.*;
import io.zeebe.client.api.commands.DeploymentCommand;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.impl.record.DeploymentRecordImpl;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.intent.DeploymentIntent;

public class DeploymentCommandImpl extends DeploymentRecordImpl implements DeploymentCommand
{
    private DeploymentCommandName name;

    private String errorMessage;

    @JsonCreator
    public DeploymentCommandImpl(@JacksonInject ZeebeObjectMapper objectMapper)
    {
        super(objectMapper, RecordType.COMMAND);
    }

    public DeploymentCommandImpl(DeploymentIntent intent)
    {
        super(null, RecordType.COMMAND);
        setIntent(intent);
    }

    @JsonIgnore
    @Override
    public DeploymentCommandName getName()
    {
        return name;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("DeploymentCommand [command=");
        builder.append(name);
        builder.append(", topic=");
        builder.append(getDeploymentTopic());
        builder.append(", resource=");
        builder.append(getResources());
        builder.append("]");
        return builder.toString();
    }

}
