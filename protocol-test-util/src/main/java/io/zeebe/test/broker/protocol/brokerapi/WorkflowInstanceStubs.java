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
package io.zeebe.test.broker.protocol.brokerapi;

import java.util.function.Consumer;

import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class WorkflowInstanceStubs
{

    private StubBrokerRule broker;

    public WorkflowInstanceStubs(StubBrokerRule broker)
    {
        this.broker = broker;
    }

    public void registerCreateCommand()
    {
        registerCreateCommand(b ->
        { });
    }

    public void registerCreateCommand(Consumer<ExecuteCommandResponseBuilder> modifier)
    {
        final ExecuteCommandResponseBuilder builder =
            broker.onExecuteCommandRequest(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
                .respondWith()
                .event()
                .intent(WorkflowInstanceIntent.CREATED)
                .value()
                  .allOf(r -> r.getCommand())
                  .done();

        modifier.accept(builder);

        builder.register();
    }

    public void registerCancelCommand()
    {
        registerCancelCommand(b ->
        { });
    }

    public void registerCancelCommand(Consumer<ExecuteCommandResponseBuilder> modifier)
    {
        final ExecuteCommandResponseBuilder builder =
            broker.onExecuteCommandRequest(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CANCEL)
                .respondWith()
                .event()
                .intent(WorkflowInstanceIntent.CANCELED)
                .value()
                  .allOf(r -> r.getCommand())
                  .done();

        modifier.accept(builder);

        builder.register();
    }

    public void registerUpdatedPayloadCommand()
    {
        registerUpdatedPayloadCommand(b ->
        { });
    }

    public void registerUpdatedPayloadCommand(Consumer<ExecuteCommandResponseBuilder> modifier)
    {
        final ExecuteCommandResponseBuilder builder =
            broker.onExecuteCommandRequest(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.UPDATE_PAYLOAD)
                .respondWith()
                .event()
                .intent(WorkflowInstanceIntent.PAYLOAD_UPDATED)
                .value()
                  .allOf(r -> r.getCommand())
                  .done();

        modifier.accept(builder);

        builder.register();
    }
}
