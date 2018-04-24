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
package io.zeebe.client.impl.workflow;

import io.zeebe.client.api.commands.CancelWorkflowInstanceCommandStep1;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.impl.CommandImpl;
import io.zeebe.client.impl.RequestManager;
import io.zeebe.client.impl.command.WorkflowInstanceCommandImpl;
import io.zeebe.client.impl.event.WorkflowInstanceEventImpl;
import io.zeebe.client.impl.record.RecordImpl;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.util.EnsureUtil;

public class CancelWorkflowInstanceCommandImpl extends CommandImpl<WorkflowInstanceEvent> implements CancelWorkflowInstanceCommandStep1
{
    private final WorkflowInstanceCommandImpl command;

    public CancelWorkflowInstanceCommandImpl(final RequestManager commandManager, WorkflowInstanceEvent event)
    {
        super(commandManager);

        EnsureUtil.ensureNotNull("base event", event);

        command = new WorkflowInstanceCommandImpl((WorkflowInstanceEventImpl) event, WorkflowInstanceIntent.CANCEL);
    }

    @Override
    public RecordImpl getCommand()
    {
        return command;
    }

}
