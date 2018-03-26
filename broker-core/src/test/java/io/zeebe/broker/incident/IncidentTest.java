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
package io.zeebe.broker.incident;

import static io.zeebe.broker.test.MsgPackUtil.JSON_MAPPER;
import static io.zeebe.broker.test.MsgPackUtil.MSGPACK_MAPPER;
import static io.zeebe.broker.test.MsgPackUtil.MSGPACK_PAYLOAD;
import static io.zeebe.broker.test.MsgPackUtil.encodeMsgPack;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.incidentEvents;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.taskEvents;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.workflowInstanceEvents;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.agrona.MutableDirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.broker.workflow.data.WorkflowInstanceState;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedEvent;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;

public class IncidentTest
{
    private static final String PROP_PAYLOAD = "payload";

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    private TestTopicClient testClient;

    private static final WorkflowDefinition WORKFLOW_INPUT_MAPPING = Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("failingTask", t -> t.taskType("test")
                         .input("$.foo", "$.foo"))
            .done();

    private static final WorkflowDefinition WORKFLOW_OUTPUT_MAPPING = Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("failingTask", t -> t.taskType("test")
                         .output("$.foo", "$.foo"))
            .done();

    private static final byte[] PAYLOAD;

    static
    {
        final MutableDirectBuffer buffer = encodeMsgPack((w) ->
        {
            w.writeMapHeader(1);
            w.writeString(wrapString("foo"));
            w.writeString(wrapString("bar"));
        });
        PAYLOAD = new byte[buffer.capacity()];
        buffer.getBytes(0, PAYLOAD);
    }

    @Before
    public void init() throws Exception
    {
        testClient = apiRule.topic();
    }

    @Test
    public void shouldCreateIncidentForInputMappingFailure()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // then
        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_READY"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("failureEventPosition", failureEvent.position())
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", failureEvent.key())
            .containsEntry("taskKey", -1);
    }

    @Test
    public void shouldCreateIncidentForOutputMappingFailure()
    {
        // given
        testClient.deploy(WORKFLOW_OUTPUT_MAPPING);

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        testClient.completeTaskOfType("test", MSGPACK_PAYLOAD);

        // then
        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETING"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("failureEventPosition", failureEvent.position())
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", failureEvent.key())
            .containsEntry("taskKey", -1);
    }

    @Test
    public void shouldResolveIncidentForInputMappingFailure() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_READY"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // then
        final SubscribedEvent followUpEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));
        assertThat(followUpEvent.event()).containsEntry("payload", PAYLOAD);

        final SubscribedEvent incidentResolvedEvent = testClient.receiveSingleEvent(incidentEvents("RESOLVED"));
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", followUpEvent.key())
            .containsEntry("taskKey", -1);
    }

    @Test
    public void shouldResolveIncidentForOutputMappingFailure() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW_OUTPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        testClient.completeTaskOfType("test", MSGPACK_PAYLOAD);

        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETING"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // then
        final SubscribedEvent followUpEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));
        assertThat(followUpEvent.event()).containsEntry("payload", PAYLOAD);

        final SubscribedEvent incidentResolvedEvent = testClient.receiveSingleEvent(incidentEvents("RESOLVED"));
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", followUpEvent.key())
            .containsEntry("taskKey", -1);
    }


    @Test
    public void shouldCreateIncidentForInvalidResultOnInputMapping() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("failingTask", t -> t.taskType("external")
                                       .input("$.string", "$"))
                          .done());

        // when
        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // then incident is created
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATE"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                         .containsEntry("errorMessage", "Processing failed, since mapping will result in a non map object (json object).");
    }

    @Test
    public void shouldResolveIncidentForInvalidResultOnInputMapping() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external")
                                       .input("$.string", "$"))
                          .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // then incident is created
        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_READY"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        // when
        updatePayload(workflowInstanceKey, failureEvent, "{'string':{'obj':'test'}}");

        // then
        final SubscribedEvent followUpEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));

        final byte[] result = (byte[]) followUpEvent.event()
                                                    .get(PROP_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result)).isEqualTo(JSON_MAPPER.readTree("{'obj':'test'}"));

        final SubscribedEvent incidentResolvedEvent = testClient.receiveSingleEvent(incidentEvents("RESOLVED"));
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.event()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                                 .containsEntry("errorMessage",
                                                                "Processing failed, since mapping will result in a non map object (json object).")
                                                 .containsEntry("bpmnProcessId", "process")
                                                 .containsEntry("workflowInstanceKey", workflowInstanceKey)
                                                 .containsEntry("activityId", "service")
                                                 .containsEntry("activityInstanceKey", followUpEvent.key())
                                                 .containsEntry("taskKey", -1);
    }

    @Test
    public void shouldCreateIncidentForInvalidResultOnOutputMapping() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("failingTask", t -> t.taskType("external")
                                       .input("$.jsonObject", "$")
                                       .output("$.testAttr", "$"))
                          .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external", MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'testAttr':'test'}")));
        testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));

        // then incident is created
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATE"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                         .containsEntry("errorMessage", "Processing failed, since mapping will result in a non map object (json object).");
    }

    @Test
    public void shouldResolveIncidentForInvalidResultOnOutputMapping() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external")
                                       .input("$.jsonObject", "$")
                                       .output("$.testAttr", "$"))
                          .done());

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external", MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'testAttr':'test'}")));
        testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));

        // then incident is created
        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETING"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        // when
        updatePayload(workflowInstanceKey, failureEvent, "{'testAttr':{'obj':'test'}}");

        // then
        final SubscribedEvent followUpEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        final byte[] result = (byte[]) followUpEvent.event()
                                                    .get(PROP_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result)).isEqualTo(JSON_MAPPER.readTree("{'obj':'test'}"));

        final SubscribedEvent incidentResolvedEvent = testClient.receiveSingleEvent(incidentEvents("RESOLVED"));
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.event()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                                 .containsEntry("errorMessage",
                                                                "Processing failed, since mapping will result in a non map object (json object).")
                                                 .containsEntry("bpmnProcessId", "process")
                                                 .containsEntry("workflowInstanceKey", workflowInstanceKey)
                                                 .containsEntry("activityId", "service")
                                                 .containsEntry("activityInstanceKey", followUpEvent.key())
                                                 .containsEntry("taskKey", -1);
    }

    @Test
    public void shouldCreateIncidentForInAndOutputMappingAndNoTaskCompletePayload() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("failingTask", t -> t.taskType("external")
                                       .input("$.jsonObject", "$")
                                       .output("$.testAttr", "$"))
                          .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external");

        // then incident is created
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATE"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                         .containsEntry("errorMessage", "Task was completed without an payload - processing of output mapping failed!");
    }

    @Test
    public void shouldResolveIncidentForInAndOutputMappingAndNoTaskCompletePayload() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external")
                                       .input("$.jsonObject", "$")
                                       .output("$.testAttr", "$"))
                          .done());

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external");

        // then incident is created
        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETING"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        // when
        updatePayload(workflowInstanceKey, failureEvent, "{'testAttr':{'obj':'test'}}");

        // then
        final SubscribedEvent followUpEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        final byte[] result = (byte[]) followUpEvent.event()
                                                    .get(PROP_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result)).isEqualTo(JSON_MAPPER.readTree("{'obj':'test'}"));

        final SubscribedEvent incidentResolvedEvent = testClient.receiveSingleEvent(incidentEvents("RESOLVED"));
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.event()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                                 .containsEntry("errorMessage",
                                                                "Task was completed without an payload - processing of output mapping failed!")
                                                 .containsEntry("bpmnProcessId", "process")
                                                 .containsEntry("workflowInstanceKey", workflowInstanceKey)
                                                 .containsEntry("activityId", "service")
                                                 .containsEntry("activityInstanceKey", followUpEvent.key())
                                                 .containsEntry("taskKey", -1);
    }

    @Test
    public void shouldCreateIncidentForOutputMappingAndNoTaskCompletePayload() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("failingTask", t -> t.taskType("external")
                                       .output("$.testAttr", "$"))
                          .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external");

        // then incident is created
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATE"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                         .containsEntry("errorMessage", "Task was completed without an payload - processing of output mapping failed!");
    }

    @Test
    public void shouldResolveIncidentForOutputMappingAndNoTaskCompletePayload() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external")
                                       .output("$.testAttr", "$"))
                          .done());

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external");

        // then incident is created
        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETING"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        // when
        updatePayload(workflowInstanceKey, failureEvent, "{'testAttr':{'obj':'test'}}");

        // then
        final SubscribedEvent followUpEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        final byte[] result = (byte[]) followUpEvent.event()
                                                    .get(PROP_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result)).isEqualTo(JSON_MAPPER.readTree("{'obj':'test'}"));

        final SubscribedEvent incidentResolvedEvent = testClient.receiveSingleEvent(incidentEvents("RESOLVED"));
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.event()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                                 .containsEntry("errorMessage",
                                                                "Task was completed without an payload - processing of output mapping failed!")
                                                 .containsEntry("bpmnProcessId", "process")
                                                 .containsEntry("workflowInstanceKey", workflowInstanceKey)
                                                 .containsEntry("activityId", "service")
                                                 .containsEntry("activityInstanceKey", followUpEvent.key())
                                                 .containsEntry("taskKey", -1);
    }

    @Test
    public void shouldCreateIncidentIfExclusiveGatewayHasNoMatchingCondition()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("workflow")
                          .startEvent()
                          .exclusiveGateway("xor")
                          .sequenceFlow("s1", s -> s.condition("$.foo < 5"))
                              .endEvent()
                          .sequenceFlow("s2", s -> s.condition("$.foo >= 5 && $.foo < 10"))
                              .endEvent()
                              .done());

        // when
        testClient.createWorkflowInstance("workflow", asMsgPack("foo", 12));

        // then incident is created
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATE"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event()).containsEntry("errorType", ErrorType.CONDITION_ERROR.name())
                                         .containsEntry("errorMessage", "All conditions evaluated to false and no default flow is set.")
                                         .containsEntry("activityId", "xor");
    }

    @Test
    public void shouldCreateIncidentIfConditionFailsToEvaluate()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("workflow")
                          .startEvent()
                          .exclusiveGateway("xor")
                          .sequenceFlow("s1", s -> s.condition("$.foo < 5"))
                              .endEvent()
                          .sequenceFlow("s2", s -> s.condition("$.foo >= 5 && $.foo < 10"))
                              .endEvent()
                              .done());

        // when
        testClient.createWorkflowInstance("workflow", asMsgPack("foo", "bar"));

        // then incident is created
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATE"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event()).containsEntry("errorType", ErrorType.CONDITION_ERROR.name())
                                         .containsEntry("errorMessage", "Cannot compare values of different types: STRING and INTEGER")
                                         .containsEntry("activityId", "xor");
    }

    @Test
    public void shouldResolveIncidentForFailedCondition() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("workflow")
                              .startEvent()
                              .exclusiveGateway("xor")
                              .sequenceFlow("s1", s -> s.condition("$.foo < 5"))
                              .endEvent()
                              .sequenceFlow("s2", s -> s.condition("$.foo >= 5 && $.foo < 10"))
                              .endEvent()
                              .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("workflow", asMsgPack("foo", "bar"));

        // then incident is created
        testClient.receiveSingleEvent(incidentEvents("CREATED"));

        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("GATEWAY_ACTIVATED"));

        // when correct payload is used
        updatePayload(workflowInstanceKey, failureEvent.key(), asMsgPack("foo", 7).byteArray());

        // then
        testClient.receiveSingleEvent(incidentEvents("RESOLVE"));
        testClient.receiveSingleEvent(workflowInstanceEvents("GATEWAY_ACTIVATED"));
        testClient.receiveSingleEvent(workflowInstanceEvents("SEQUENCE_FLOW_TAKEN"));
        testClient.receiveSingleEvent(incidentEvents("RESOLVED"));
        testClient.receiveSingleEvent(workflowInstanceEvents("END_EVENT_OCCURRED"));
        testClient.receiveSingleEvent(workflowInstanceEvents("WORKFLOW_INSTANCE_COMPLETED"));
    }

    @Test
    public void shouldResolveIncidentForFailedConditionAfterUploadingWrongPayload() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("workflow")
                          .startEvent()
                          .exclusiveGateway("xor")
                          .sequenceFlow("s1", s -> s.condition("$.foo < 5"))
                              .endEvent()
                          .sequenceFlow("s2", s -> s.condition("$.foo >= 5 && $.foo < 10"))
                              .endEvent()
                              .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("workflow", asMsgPack("foo", "bar"));

        // then incident is created
        testClient.receiveSingleEvent(incidentEvents("CREATED"));

        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("GATEWAY_ACTIVATED"));

        // when not correct payload is used
        updatePayload(workflowInstanceKey, failureEvent.key(), asMsgPack("foo", 10).byteArray());

        // then
        testClient.receiveSingleEvent(incidentEvents("RESOLVE"));
        testClient.receiveSingleEvent(workflowInstanceEvents("GATEWAY_ACTIVATED"));
        testClient.receiveSingleEvent(incidentEvents("RESOLVE_FAILED"));

        // when correct payload is used
        updatePayload(workflowInstanceKey, failureEvent.key(), asMsgPack("foo", 7).byteArray());

        // then
        testClient.receiveSingleEvent(incidentEvents("RESOLVE"));
        testClient.receiveSingleEvent(workflowInstanceEvents("GATEWAY_ACTIVATED"));
        testClient.receiveSingleEvent(workflowInstanceEvents("SEQUENCE_FLOW_TAKEN"));
        testClient.receiveSingleEvent(incidentEvents("RESOLVED"));
        testClient.receiveSingleEvent(workflowInstanceEvents("END_EVENT_OCCURRED"));
        testClient.receiveSingleEvent(workflowInstanceEvents("WORKFLOW_INSTANCE_COMPLETED"));
    }

    @Test
    public void shouldResolveIncidentForExclusiveGatewayWithoutMatchingCondition() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("workflow")
                          .startEvent()
                          .exclusiveGateway("xor")
                          .sequenceFlow("s1", s -> s.condition("$.foo < 5"))
                              .endEvent()
                          .sequenceFlow("s2", s -> s.condition("$.foo >= 5 && $.foo < 10"))
                              .endEvent()
                              .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("workflow", asMsgPack("foo", 12));

        // then incident is created
        testClient.receiveSingleEvent(incidentEvents("CREATED"));

        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("GATEWAY_ACTIVATED"));

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), asMsgPack("foo", 7).byteArray());

        // then
        testClient.receiveSingleEvent(incidentEvents("RESOLVED"));
        testClient.receiveSingleEvent(workflowInstanceEvents("WORKFLOW_INSTANCE_COMPLETED"));
    }

    @Test
    public void shouldFailToResolveIncident() throws Exception
    {
        // given
        final WorkflowDefinition modelInstance = Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .serviceTask("failingTask", t -> t.taskType("external")
                             .input("$.foo", "$.foo")
                             .input("$.bar", "$.bar"))
               .done();

        testClient.deploy(modelInstance);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final  SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_READY"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));
        assertThat(incidentEvent.event()).containsEntry("errorMessage", "No data found for query $.foo.");

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // then
        final SubscribedEvent resolveFailedEvent = testClient.receiveSingleEvent(incidentEvents("RESOLVE_FAILED"));
        assertThat(resolveFailedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(resolveFailedEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.bar.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask");
    }

    @Test
    public void shouldResolveIncidentAfterPreviousResolvingFailed() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_READY"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        updatePayload(workflowInstanceKey, failureEvent.key(), MsgPackHelper.EMTPY_OBJECT);

        testClient.receiveSingleEvent(incidentEvents("RESOLVE_FAILED"));

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // then
        final SubscribedEvent incidentResolvedEvent = testClient.receiveSingleEvent(incidentEvents("RESOLVED"));
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask");
    }

    @Test
    public void shouldResolveMultipleIncidents() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        // create and resolve an first incident
        long workflowInstanceKey = testClient.createWorkflowInstance("process");
        SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_READY", workflowInstanceKey));
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // create a second incident
        workflowInstanceKey = testClient.createWorkflowInstance("process");
        failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_READY", workflowInstanceKey));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED", workflowInstanceKey));

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // then
        final SubscribedEvent incidentResolvedEvent = testClient.receiveSingleEvent(incidentEvents("RESOLVED", workflowInstanceKey));
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
    }

    @Test
    public void shouldDeleteIncidentIfActivityTerminated()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedEvent incidentCreatedEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        // when
        cancelWorkflowInstance(workflowInstanceKey);

        // then
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("DELETED"));

        assertThat(incidentEvent.key()).isEqualTo(incidentCreatedEvent.key());
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", incidentEvent.event().get("activityInstanceKey"));
    }

    @Test
    public void shouldProcessIncidentsAfterMultipleTerminations()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        // create and cancel instance with incident
        long workflowInstanceKey = testClient.createWorkflowInstance("process");
        cancelWorkflowInstance(workflowInstanceKey);

        // create and cancel instance without incident
        workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);
        cancelWorkflowInstance(workflowInstanceKey);

        // create another instance which creates an incident
        workflowInstanceKey = testClient.createWorkflowInstance("process");
        final SubscribedEvent incidentCreatedEvent = testClient.receiveSingleEvent(incidentEvents("CREATED", workflowInstanceKey));

        // when
        cancelWorkflowInstance(workflowInstanceKey);

        // then
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("DELETED", workflowInstanceKey));

        assertThat(incidentEvent.key()).isEqualTo(incidentCreatedEvent.key());
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", incidentEvent.event().get("activityInstanceKey"));
    }

    @Test
    public void shouldCreateIncidentIfTaskHasNoRetriesLeft()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);

        // when
        failTaskWithNoRetriesLeft();

        // then
        final SubscribedEvent activityEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));
        final SubscribedEvent failedEvent = testClient.receiveSingleEvent(taskEvents("FAILED"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.TASK_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("failureEventPosition", failedEvent.position())
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", activityEvent.key())
            .containsEntry("taskKey", failedEvent.key());
    }

    @Test
    public void shouldDeleteIncidentIfTaskRetriesIncreased()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);

        failTaskWithNoRetriesLeft();

        // when
        updateTaskRetries();

        // then
        final SubscribedEvent taskEvent = testClient.receiveSingleEvent(taskEvents("FAILED"));
        final SubscribedEvent activityEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));
        SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("DELETE"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.TASK_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", activityEvent.key())
            .containsEntry("taskKey", taskEvent.key());

        incidentEvent = testClient.receiveSingleEvent(incidentEvents("DELETED"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.TASK_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", activityEvent.key())
            .containsEntry("taskKey", taskEvent.key());
    }

    @Test
    public void shouldDeleteIncidentIfTaskIsCanceled()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);

        failTaskWithNoRetriesLeft();

        final SubscribedEvent incidentCreatedEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        // when
        cancelWorkflowInstance(workflowInstanceKey);

        // then
        final SubscribedEvent taskEvent = testClient.receiveSingleEvent(taskEvents("FAILED"));
        SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("DELETE"));

        assertThat(incidentEvent.key()).isEqualTo(incidentCreatedEvent.key());
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.TASK_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", incidentEvent.event().get("activityInstanceKey"))
            .containsEntry("taskKey", taskEvent.key());

        incidentEvent = testClient.receiveSingleEvent(incidentEvents("DELETED"));

        assertThat(incidentEvent.key()).isEqualTo(incidentCreatedEvent.key());
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.TASK_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", incidentEvent.event().get("activityInstanceKey"))
            .containsEntry("taskKey", taskEvent.key());
    }

    @Test
    public void shouldCreateIncidentIfStandaloneTaskHasNoRetriesLeft()
    {
        // given
        createStandaloneTask();

        // when
        failTaskWithNoRetriesLeft();

        // then
        final SubscribedEvent failedEvent = testClient.receiveSingleEvent(taskEvents("FAILED"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.TASK_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("failureEventPosition", failedEvent.position())
            .containsEntry("bpmnProcessId", "")
            .containsEntry("workflowInstanceKey", -1)
            .containsEntry("activityId", "")
            .containsEntry("activityInstanceKey", -1)
            .containsEntry("taskKey", failedEvent.key());
    }

    @Test
    public void shouldDeleteStandaloneIncidentIfTaskRetriesIncreased()
    {
        // given
        createStandaloneTask();

        failTaskWithNoRetriesLeft();

        // when
        updateTaskRetries();

        // then
        final SubscribedEvent taskEvent = testClient.receiveSingleEvent(taskEvents("FAILED"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("DELETED"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.TASK_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("bpmnProcessId", "")
            .containsEntry("workflowInstanceKey", -1)
            .containsEntry("activityId", "")
            .containsEntry("activityInstanceKey", -1)
            .containsEntry("taskKey", taskEvent.key());
    }



    private void failTaskWithNoRetriesLeft()
    {
        apiRule.openTaskSubscription("test").await();

        final SubscribedEvent taskEvent = testClient.receiveSingleEvent(taskEvents("LOCKED"));

        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .key(taskEvent.key())
            .eventTypeTask()
            .command()
                .put("state", "FAIL")
                .put("retries", 0)
                .put("type", "failingTask")
                .put("lockOwner", taskEvent.event().get("lockOwner"))
                .put("headers", taskEvent.event().get("headers"))
                .done()
            .sendAndAwait();

        assertThat(response.getEvent()).containsEntry("state", "FAILED");
    }

    private void createStandaloneTask()
    {
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .eventTypeTask()
            .command()
                .put("state", "CREATE")
                .put("type", "test")
                .put("retries", 3)
                .done()
            .sendAndAwait();

        assertThat(response.getEvent()).containsEntry("state", "CREATED");
    }

    private void updateTaskRetries()
    {
        final SubscribedEvent taskEvent = testClient.receiveSingleEvent(taskEvents("FAILED"));

        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .key(taskEvent.key())
            .eventTypeTask()
            .command()
                .put("state", "UPDATE_RETRIES")
                .put("retries", 1)
                .put("type", "test")
                .put("lockOwner", taskEvent.event().get("lockOwner"))
                .put("headers", taskEvent.event().get("headers"))
                .done()
            .sendAndAwait();

        assertThat(response.getEvent()).containsEntry("state", "RETRIES_UPDATED");
    }

    private void updatePayload(final long workflowInstanceKey, final long activityInstanceKey, byte[] payload)
    {
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .eventType(EventType.WORKFLOW_INSTANCE_EVENT)
            .key(activityInstanceKey)
            .command()
                .put("state", WorkflowInstanceState.UPDATE_PAYLOAD)
                .put("workflowInstanceKey", workflowInstanceKey)
                .put("payload", payload)
                .done()
            .sendAndAwait();

        assertThat(response.getEvent()).containsEntry("state", WorkflowInstanceState.PAYLOAD_UPDATED.name());
    }


    private void updatePayload(long workflowInstanceKey, SubscribedEvent activityInstanceEvent, String payload) throws IOException
    {
        updatePayload(workflowInstanceKey, activityInstanceEvent.key(), MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree(payload)));
    }

    private void cancelWorkflowInstance(final long workflowInstanceKey)
    {
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .key(workflowInstanceKey)
            .eventTypeWorkflow()
            .command()
                .put("state", "CANCEL_WORKFLOW_INSTANCE")
                .done()
            .sendAndAwait();

        assertThat(response.getEvent()).containsEntry("state", "WORKFLOW_INSTANCE_CANCELED");
    }

}
