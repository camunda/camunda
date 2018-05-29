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
package io.zeebe.client.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.ByteArrayInputStream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.CreateWorkflowInstanceCommandStep1;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.events.WorkflowInstanceState;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;


public class CreateWorkflowInstanceTest
{
    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule brokerRule = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private final MsgPackConverter msgPackConverter = new MsgPackConverter();

    protected ZeebeClient client;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();
    }

    @Test
    public void shouldCreateWorkflowInstanceByBpmnProcessId()
    {
        // given
        brokerRule.workflowInstances().registerCreateCommand(b ->
            b.value()
                .allOf(r -> r.getCommand())
                .put("version", 1)
                .put("workflowInstanceKey", 1)
            .done());

        // when
        final WorkflowInstanceEvent workflowInstance = clientRule.workflowClient().newCreateInstanceCommand()
            .bpmnProcessId("foo")
            .latestVersion()
            .send()
            .join();

        // then
        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.valueType()).isEqualTo(ValueType.WORKFLOW_INSTANCE);
        assertThat(commandRequest.intent()).isEqualTo(WorkflowInstanceIntent.CREATE);

        assertThat(commandRequest.getCommand()).containsOnly(
              entry("bpmnProcessId", "foo"),
              entry("version", CreateWorkflowInstanceCommandStep1.LATEST_VERSION),
              entry("workflowKey", -1),
              entry("workflowInstanceKey", -1));

        assertThat(workflowInstance.getState()).isEqualTo(WorkflowInstanceState.CREATED);
        assertThat(workflowInstance.getBpmnProcessId()).isEqualTo("foo");
        assertThat(workflowInstance.getVersion()).isEqualTo(1);
        assertThat(workflowInstance.getWorkflowInstanceKey()).isEqualTo(1);
        assertThat(workflowInstance.getPayload()).isNull();
    }

    @Test
    public void shouldCreateWorkflowInstanceWithPayload() throws Exception
    {
        // given
        final String payload = "{\"bar\":4}";

        brokerRule.workflowInstances().registerCreateCommand(b ->
                b.value()
                    .allOf(r -> r.getCommand())
                    .put("version", 1)
                    .put("workflowInstanceKey", 1)
                .done());

        // when
        final WorkflowInstanceEvent workflowInstance = clientRule.workflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("foo")
            .latestVersion()
            .payload(new ByteArrayInputStream(payload.getBytes()))
            .send()
            .join();

        // then
        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.getCommand()).containsOnly(
              entry("bpmnProcessId", "foo"),
              entry("version", CreateWorkflowInstanceCommandStep1.LATEST_VERSION),
              entry("workflowKey", -1),
              entry("workflowInstanceKey", -1),
              entry("payload", msgPackConverter.convertToMsgPack(payload)));

        assertThat(workflowInstance.getBpmnProcessId()).isEqualTo("foo");
        assertThat(workflowInstance.getVersion()).isEqualTo(1);
        assertThat(workflowInstance.getWorkflowInstanceKey()).isEqualTo(1);
        assertThat(workflowInstance.getPayload()).isEqualTo(payload);
    }

    @Test
    public void shouldCreateWorkflowInstanceByBpmnProcessIdAndVersion()
    {
        // given
        brokerRule.workflowInstances().registerCreateCommand(b ->
                b.value()
                    .allOf(r -> r.getCommand())
                    .put("workflowInstanceKey", 1)
                .done());

        // when
        final WorkflowInstanceEvent workflowInstance = clientRule.workflowClient()
                .newCreateInstanceCommand()
                .bpmnProcessId("foo")
                .version(2)
                .send()
                .join();

        // then
        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.getCommand()).containsOnly(
              entry("bpmnProcessId", "foo"),
              entry("version", 2),
              entry("workflowKey", -1),
              entry("workflowInstanceKey", -1));

        assertThat(workflowInstance.getBpmnProcessId()).isEqualTo("foo");
        assertThat(workflowInstance.getVersion()).isEqualTo(2);
        assertThat(workflowInstance.getWorkflowInstanceKey()).isEqualTo(1);
    }

    @Test
    public void shouldCreateWorkflowInstanceByWorkflowKey()
    {
        // given
        brokerRule.workflowInstances().registerCreateCommand(b ->
                b.value()
                    .allOf(r -> r.getCommand())
                    .put("workflowInstanceKey", 1)
                    .put("payload", msgPackConverter.convertToMsgPack("{ \"bar\" : 4 }"))
                .done());

        // when
        final WorkflowInstanceEvent workflowInstance = clientRule.workflowClient()
                .newCreateInstanceCommand()
                .workflowKey(2L)
                .send()
                .join();

        // then
        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.getCommand()).containsOnly(
              entry("version", -1),
              entry("workflowKey", 2),
              entry("workflowInstanceKey", -1));

        assertThat(workflowInstance.getWorkflowKey()).isEqualTo(2);
        assertThat(workflowInstance.getWorkflowInstanceKey()).isEqualTo(1);
    }

    @Test
    public void shouldThrowExceptionOnRejection()
    {
        // given
        brokerRule.workflowInstances().registerCreateCommand(r -> r.rejection());

        // expect exception
        expectedException.expect(ClientCommandRejectedException.class);
        expectedException.expectMessage("Command (CREATE) was rejected");

        // when
        clientRule.workflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("foo")
            .latestVersion()
            .send()
            .join();
    }

}
