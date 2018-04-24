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
import io.zeebe.client.api.commands.IncidentCommand;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.impl.record.IncidentRecordImpl;
import io.zeebe.protocol.clientapi.RecordType;

public class IncidentCommandImpl extends IncidentRecordImpl implements IncidentCommand
{
    @JsonCreator
    public IncidentCommandImpl(@JacksonInject ZeebeObjectMapper objectMapper)
    {
        super(objectMapper, RecordType.COMMAND);
    }

    @JsonIgnore
    @Override
    public IncidentCommandName getName()
    {
        return IncidentCommandName.valueOf(getMetadata().getIntent());
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("IncidentCommand [command=");
        builder.append(getName());
        builder.append(", errorType=");
        builder.append(getErrorType());
        builder.append(", errorMessage=");
        builder.append(getErrorMessage());
        builder.append(", bpmnProcessId=");
        builder.append(getBpmnProcessId());
        builder.append(", workflowInstanceKey=");
        builder.append(getWorkflowInstanceKey());
        builder.append(", activityId=");
        builder.append(getActivityId());
        builder.append(", activityInstanceKey=");
        builder.append(getActivityInstanceKey());
        builder.append(", jobKey=");
        builder.append(getJobKey());
        builder.append("]");
        return builder.toString();
    }

}
