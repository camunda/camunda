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

import io.zeebe.UnstableTest;
import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;
import org.agrona.MutableDirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.RuleChain;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static io.zeebe.broker.test.MsgPackUtil.*;
import static io.zeebe.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_TOPIC_NAME;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
        apiRule.waitForTopic(DEFAULT_TOPIC_NAME, 1);
    }

    @Test
    public void shouldCreateIncidentForInputMappingFailure()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // then
        final SubscribedRecord failureEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_READY);
        final SubscribedRecord createIncidentEvent = testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(createIncidentEvent.sourceRecordPosition()).isEqualTo(failureEvent.position());
        assertThat(incidentEvent.sourceRecordPosition()).isEqualTo(createIncidentEvent.position());
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("failureEventPosition", failureEvent.position())
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", failureEvent.key())
            .containsEntry("jobKey", -1);
    }

    @Test
    public void shouldCreateIncidentForOutputMappingFailure()
    {
        // given
        testClient.deploy(WORKFLOW_OUTPUT_MAPPING);

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        testClient.completeJobOfType("test", MSGPACK_PAYLOAD);

        // then
        final SubscribedRecord failureEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETING);
        final SubscribedRecord createIncidentEvent = testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(createIncidentEvent.sourceRecordPosition()).isEqualTo(failureEvent.position());
        assertThat(incidentEvent.sourceRecordPosition()).isEqualTo(createIncidentEvent.position());
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("failureEventPosition", failureEvent.position())
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", failureEvent.key())
            .containsEntry("jobKey", -1);
    }

    @Test
    public void shouldResolveIncidentForInputMappingFailure() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedRecord failureEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_READY);
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // then
        final SubscribedRecord followUpEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_ACTIVATED);
        assertThat(followUpEvent.value()).containsEntry("payload", PAYLOAD);

        final SubscribedRecord incidentResolvedEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.RESOLVED);
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.sourceRecordPosition()).isEqualTo(followUpEvent.position());
        assertThat(incidentResolvedEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", followUpEvent.key())
            .containsEntry("jobKey", -1);
    }

    @Test
    public void shouldResolveIncidentForOutputMappingFailure() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW_OUTPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        testClient.completeJobOfType("test", MSGPACK_PAYLOAD);

        final SubscribedRecord failureEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETING);
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // then
        final SubscribedRecord followUpEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);
        assertThat(followUpEvent.value()).containsEntry("payload", PAYLOAD);

        final SubscribedRecord incidentResolvedEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.RESOLVED);
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.sourceRecordPosition()).isEqualTo(followUpEvent.position());
        assertThat(incidentResolvedEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", followUpEvent.key())
            .containsEntry("jobKey", -1);
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
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
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
        final SubscribedRecord failureEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_READY);
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

        // when
        updatePayload(workflowInstanceKey, failureEvent, "{'string':{'obj':'test'}}");

        // then
        final SubscribedRecord followUpEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_ACTIVATED);

        final byte[] result = (byte[]) followUpEvent.value()
                                                    .get(PROP_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result)).isEqualTo(JSON_MAPPER.readTree("{'obj':'test'}"));

        final SubscribedRecord incidentResolvedEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.RESOLVED);
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.value()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                                 .containsEntry("errorMessage",
                                                                "Processing failed, since mapping will result in a non map object (json object).")
                                                 .containsEntry("bpmnProcessId", "process")
                                                 .containsEntry("workflowInstanceKey", workflowInstanceKey)
                                                 .containsEntry("activityId", "service")
                                                 .containsEntry("activityInstanceKey", followUpEvent.key())
                                                 .containsEntry("jobKey", -1);
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
        testClient.completeJobOfType("external", MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'testAttr':'test'}")));
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_ACTIVATED);

        // then incident is created
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
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
        testClient.completeJobOfType("external", MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'testAttr':'test'}")));
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_ACTIVATED);

        // then incident is created
        final SubscribedRecord failureEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETING);
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

        // when
        updatePayload(workflowInstanceKey, failureEvent, "{'testAttr':{'obj':'test'}}");

        // then
        final SubscribedRecord followUpEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        final byte[] result = (byte[]) followUpEvent.value()
                                                    .get(PROP_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result)).isEqualTo(JSON_MAPPER.readTree("{'obj':'test'}"));

        final SubscribedRecord incidentResolvedEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.RESOLVED);
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.value()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                                 .containsEntry("errorMessage",
                                                                "Processing failed, since mapping will result in a non map object (json object).")
                                                 .containsEntry("bpmnProcessId", "process")
                                                 .containsEntry("workflowInstanceKey", workflowInstanceKey)
                                                 .containsEntry("activityId", "service")
                                                 .containsEntry("activityInstanceKey", followUpEvent.key())
                                                 .containsEntry("jobKey", -1);
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
        testClient.completeJobOfType("external");

        // then incident is created
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                         .containsEntry("errorMessage", "No data found for query $.testAttr.");
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
        testClient.completeJobOfType("external");

        // then incident is created
        final SubscribedRecord failureEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETING);
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

        // when
        updatePayload(workflowInstanceKey, failureEvent, "{'testAttr':{'obj':'test'}}");

        // then
        final SubscribedRecord followUpEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        final byte[] result = (byte[]) followUpEvent.value()
                                                    .get(PROP_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result)).isEqualTo(JSON_MAPPER.readTree("{'obj':'test'}"));

        final SubscribedRecord incidentResolvedEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.RESOLVED);
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.value()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                                 .containsEntry("errorMessage", "No data found for query $.testAttr.")
                                                 .containsEntry("bpmnProcessId", "process")
                                                 .containsEntry("workflowInstanceKey", workflowInstanceKey)
                                                 .containsEntry("activityId", "service")
                                                 .containsEntry("activityInstanceKey", followUpEvent.key())
                                                 .containsEntry("jobKey", -1);
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
        testClient.completeJobOfType("external");

        // then incident is created
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                         .containsEntry("errorMessage", "No data found for query $.testAttr.");
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
        testClient.completeJobOfType("external");

        // then incident is created
        final SubscribedRecord failureEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETING);
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

        // when
        updatePayload(workflowInstanceKey, failureEvent, "{'testAttr':{'obj':'test'}}");

        // then
        final SubscribedRecord followUpEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        final byte[] result = (byte[]) followUpEvent.value()
                                                    .get(PROP_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result)).isEqualTo(JSON_MAPPER.readTree("{'obj':'test'}"));

        final SubscribedRecord incidentResolvedEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.RESOLVED);
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.value()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                                 .containsEntry("errorMessage", "No data found for query $.testAttr.")
                                                 .containsEntry("bpmnProcessId", "process")
                                                 .containsEntry("workflowInstanceKey", workflowInstanceKey)
                                                 .containsEntry("activityId", "service")
                                                 .containsEntry("activityInstanceKey", followUpEvent.key())
                                                 .containsEntry("jobKey", -1);
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
        final SubscribedRecord failingEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.GATEWAY_ACTIVATED);
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.sourceRecordPosition()).isEqualTo(failingEvent.position());
        assertThat(incidentEvent.value()).containsEntry("errorType", ErrorType.CONDITION_ERROR.name())
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
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value()).containsEntry("errorType", ErrorType.CONDITION_ERROR.name())
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
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

        final SubscribedRecord failureEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.GATEWAY_ACTIVATED);

        // when correct payload is used
        updatePayload(workflowInstanceKey, failureEvent.key(), asMsgPack("foo", 7).byteArray());

        // then
        final List<SubscribedRecord> incidentRecords = testClient
            .receiveRecords()
            .limit(r -> r.valueType() == ValueType.INCIDENT && r.intent() == IncidentIntent.RESOLVED)
            .collect(Collectors.toList());

        final List<SubscribedRecord> workflowInstanceRecords = testClient
                .receiveRecords()
                .limit(r -> r.valueType() == ValueType.WORKFLOW_INSTANCE && r.intent() == WorkflowInstanceIntent.COMPLETED)
                .collect(Collectors.toList());

        // RESOLVE triggers RESOLVED
        assertThat(incidentRecords).extracting(SubscribedRecord::recordType, SubscribedRecord::valueType, SubscribedRecord::intent)
            .containsSubsequence(
                tuple(RecordType.COMMAND, ValueType.INCIDENT, IncidentIntent.RESOLVE),
                tuple(RecordType.EVENT, ValueType.INCIDENT, IncidentIntent.RESOLVED));

        // GATEWAY_ACTIVATED triggers SEQUENCE_FLOW_TAKEN, END_EVENT_OCCURED and COMPLETED
        assertThat(workflowInstanceRecords).extracting(SubscribedRecord::recordType, SubscribedRecord::valueType, SubscribedRecord::intent)
            .containsSubsequence(
                tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.GATEWAY_ACTIVATED),
                tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
                tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.END_EVENT_OCCURRED),
                tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.COMPLETED));
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
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

        final SubscribedRecord failureEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.GATEWAY_ACTIVATED);

        // when not correct payload is used
        updatePayload(workflowInstanceKey, failureEvent.key(), asMsgPack("foo", 10).byteArray());

        // then
        List<SubscribedRecord> incidentRecords = testClient
            .receiveRecords()
            .limit(r -> r.valueType() == ValueType.INCIDENT && r.intent() == IncidentIntent.RESOLVE_FAILED)
            .collect(Collectors.toList());

        List<SubscribedRecord> workflowInstanceRecords = testClient
            .receiveRecords()
            .limit(r -> r.valueType() == ValueType.WORKFLOW_INSTANCE && r.intent() == WorkflowInstanceIntent.GATEWAY_ACTIVATED)
            .collect(Collectors.toList());

        assertThat(incidentRecords).extracting(SubscribedRecord::recordType, SubscribedRecord::valueType, SubscribedRecord::intent)
            .containsSubsequence(
                tuple(RecordType.COMMAND, ValueType.INCIDENT, IncidentIntent.RESOLVE),
                tuple(RecordType.EVENT, ValueType.INCIDENT, IncidentIntent.RESOLVE_FAILED));
        assertThat(workflowInstanceRecords).extracting(SubscribedRecord::recordType, SubscribedRecord::valueType, SubscribedRecord::intent)
            .containsSubsequence(
                tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.GATEWAY_ACTIVATED));

        // when correct payload is used
        updatePayload(workflowInstanceKey, failureEvent.key(), asMsgPack("foo", 7).byteArray());

        // then
        incidentRecords = testClient
            .receiveRecords()
            .skipUntil(r -> r.valueType() == ValueType.INCIDENT && r.intent() == IncidentIntent.RESOLVE_FAILED)
            .limit(r -> r.valueType() == ValueType.INCIDENT && r.intent() == IncidentIntent.RESOLVED)
            .collect(Collectors.toList());

        workflowInstanceRecords = testClient
            .receiveRecords()
            .skipUntil(r -> r.valueType() == ValueType.WORKFLOW_INSTANCE && r.intent() == WorkflowInstanceIntent.GATEWAY_ACTIVATED)
            .limit(r -> r.valueType() == ValueType.WORKFLOW_INSTANCE && r.intent() == WorkflowInstanceIntent.COMPLETED)
            .collect(Collectors.toList());

        // RESOLVE triggers  RESOLVED
        assertThat(incidentRecords).extracting(SubscribedRecord::recordType, SubscribedRecord::valueType, SubscribedRecord::intent)
            .containsSubsequence(
                tuple(RecordType.COMMAND, ValueType.INCIDENT, IncidentIntent.RESOLVE),
                tuple(RecordType.EVENT, ValueType.INCIDENT, IncidentIntent.RESOLVED));

        // SEQUENCE_FLOW_TAKEN triggers the rest of the process
        assertThat(workflowInstanceRecords).extracting(SubscribedRecord::recordType, SubscribedRecord::valueType, SubscribedRecord::intent)
            .containsSubsequence(
                tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
                tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.END_EVENT_OCCURRED),
                tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.COMPLETED));
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
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

        final SubscribedRecord failureEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.GATEWAY_ACTIVATED);

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), asMsgPack("foo", 7).byteArray());

        // then
        testClient.receiveFirstIncidentEvent(IncidentIntent.RESOLVED);
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.COMPLETED);
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

        final  SubscribedRecord failureEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_READY);
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);
        assertThat(incidentEvent.value()).containsEntry("errorMessage", "No data found for query $.foo.");

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // then
        final SubscribedRecord resolveFailedEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.RESOLVE_FAILED);
        assertThat(resolveFailedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(resolveFailedEvent.value())
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

        final SubscribedRecord failureEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_READY);
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

        updatePayload(workflowInstanceKey, failureEvent.key(), MsgPackHelper.EMTPY_OBJECT);

        testClient.receiveFirstIncidentEvent(IncidentIntent.RESOLVE_FAILED);

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // then
        final SubscribedRecord incidentResolvedEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.RESOLVED);
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.value())
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
        SubscribedRecord failureEvent = testClient.receiveFirstWorkflowInstanceEvent(workflowInstanceKey, WorkflowInstanceIntent.ACTIVITY_READY);
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // create a second incident
        workflowInstanceKey = testClient.createWorkflowInstance("process");
        failureEvent = testClient.receiveFirstWorkflowInstanceEvent(workflowInstanceKey, WorkflowInstanceIntent.ACTIVITY_READY);
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentEvent(workflowInstanceKey, IncidentIntent.CREATED);

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // then
        final SubscribedRecord incidentResolvedEvent = testClient.receiveFirstIncidentEvent(workflowInstanceKey, IncidentIntent.RESOLVED);
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
    }

    @Test
    public void shouldDeleteIncidentIfActivityTerminated()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedRecord incidentCreatedEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

        // when
        cancelWorkflowInstance(workflowInstanceKey);

        // then
        final SubscribedRecord activityTerminated = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_TERMINATED);
        final SubscribedRecord deleteIncidentCommand = testClient.receiveFirstIncidentCommand(IncidentIntent.DELETE);
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.DELETED);

        assertThat(incidentEvent.key()).isEqualTo(incidentCreatedEvent.key());
        assertThat(deleteIncidentCommand.sourceRecordPosition()).isEqualTo(activityTerminated.position());
        assertThat(incidentEvent.sourceRecordPosition()).isEqualTo(deleteIncidentCommand.position());
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", incidentEvent.value().get("activityInstanceKey"));
    }

    @Test
    @Category(UnstableTest.class)
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
        final SubscribedRecord incidentCreatedEvent = testClient.receiveFirstIncidentEvent(workflowInstanceKey, IncidentIntent.CREATED);

        // when
        cancelWorkflowInstance(workflowInstanceKey);

        // then
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentEvent(workflowInstanceKey, IncidentIntent.DELETED);

        assertThat(incidentEvent.key()).isEqualTo(incidentCreatedEvent.key());
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", incidentEvent.value().get("activityInstanceKey"));
    }

    @Test
    public void shouldCreateIncidentIfJobHasNoRetriesLeft()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);

        // when
        failJobWithNoRetriesLeft();

        // then
        final SubscribedRecord activityEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_ACTIVATED);
        final SubscribedRecord failedEvent = testClient.receiveFirstJobEvent(JobIntent.FAILED);
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.sourceRecordPosition()).isEqualTo(failedEvent.position());
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.JOB_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("failureEventPosition", failedEvent.position())
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", activityEvent.key())
            .containsEntry("jobKey", failedEvent.key());
    }

    @Test
    public void shouldDeleteIncidentIfJobRetriesIncreased()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);

        failJobWithNoRetriesLeft();

        // when
        updateJobRetries();

        // then
        final SubscribedRecord jobEvent = testClient.receiveFirstJobEvent(JobIntent.FAILED);
        final SubscribedRecord jobUpdated = testClient.receiveFirstJobEvent(JobIntent.RETRIES_UPDATED);
        final SubscribedRecord activityEvent = testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_ACTIVATED);
        SubscribedRecord incidentEvent = testClient.receiveFirstIncidentCommand(IncidentIntent.DELETE);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.sourceRecordPosition()).isEqualTo(jobUpdated.position());
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.JOB_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", activityEvent.key())
            .containsEntry("jobKey", jobEvent.key());

        final long lastPos = incidentEvent.position();
        incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.DELETED);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.sourceRecordPosition()).isEqualTo(lastPos);
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.JOB_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", activityEvent.key())
            .containsEntry("jobKey", jobEvent.key());
    }

    @Test
    public void shouldDeleteIncidentIfJobIsCanceled()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);

        failJobWithNoRetriesLeft();

        final SubscribedRecord incidentCreatedEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

        // when
        cancelWorkflowInstance(workflowInstanceKey);

        // then
        final SubscribedRecord jobEvent = testClient.receiveFirstJobEvent(JobIntent.CANCELED);
        SubscribedRecord incidentEvent = testClient.receiveFirstIncidentCommand(IncidentIntent.DELETE);

        assertThat(incidentEvent.key()).isEqualTo(incidentCreatedEvent.key());
        assertThat(incidentEvent.sourceRecordPosition()).isEqualTo(jobEvent.position());
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.JOB_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", incidentEvent.value().get("activityInstanceKey"))
            .containsEntry("jobKey", jobEvent.key());

        incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.DELETED);

        assertThat(incidentEvent.key()).isEqualTo(incidentCreatedEvent.key());
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.JOB_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", incidentEvent.value().get("activityInstanceKey"))
            .containsEntry("jobKey", jobEvent.key());
    }

    @Test
    public void shouldCreateIncidentIfStandaloneJobHasNoRetriesLeft()
    {
        // given
        createStandaloneJob();

        // when
        failJobWithNoRetriesLeft();

        // then
        final SubscribedRecord failedEvent = testClient.receiveFirstJobEvent(JobIntent.FAILED);
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.JOB_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("failureEventPosition", failedEvent.position())
            .containsEntry("bpmnProcessId", "")
            .containsEntry("workflowInstanceKey", -1)
            .containsEntry("activityId", "")
            .containsEntry("activityInstanceKey", -1)
            .containsEntry("jobKey", failedEvent.key());
    }

    @Test
    public void shouldDeleteStandaloneIncidentIfJobRetriesIncreased()
    {
        // given
        createStandaloneJob();

        failJobWithNoRetriesLeft();

        // when
        updateJobRetries();

        // then
        final SubscribedRecord jobEvent = testClient.receiveFirstJobEvent(JobIntent.FAILED);
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.DELETED);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.JOB_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("bpmnProcessId", "")
            .containsEntry("workflowInstanceKey", -1)
            .containsEntry("activityId", "")
            .containsEntry("activityInstanceKey", -1)
            .containsEntry("jobKey", jobEvent.key());
    }

    private void failJobWithNoRetriesLeft()
    {
        apiRule.openJobSubscription("test").await();

        final SubscribedRecord jobEvent = testClient.receiveFirstJobEvent(JobIntent.ACTIVATED);

        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .key(jobEvent.key())
            .type(ValueType.JOB, JobIntent.FAIL)
            .command()
                .put("retries", 0)
                .put("type", "failingTask")
                .put("worker", jobEvent.value().get("worker"))
                .put("headers", jobEvent.value().get("headers"))
                .done()
            .sendAndAwait();

        assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(response.intent()).isEqualTo(JobIntent.FAILED);
    }

    private void createStandaloneJob()
    {
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .type(ValueType.JOB, JobIntent.CREATE)
            .command()
                .put("type", "test")
                .put("retries", 3)
                .done()
            .sendAndAwait();

        assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(response.intent()).isEqualTo(JobIntent.CREATED);
    }

    private void updateJobRetries()
    {
        final SubscribedRecord jobEvent = testClient.receiveFirstJobEvent(JobIntent.FAILED);

        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .key(jobEvent.key())
            .type(ValueType.JOB, JobIntent.UPDATE_RETRIES)
            .command()
                .put("retries", 1)
                .put("type", "test")
                .put("worker", jobEvent.value().get("worker"))
                .put("headers", jobEvent.value().get("headers"))
                .done()
            .sendAndAwait();

        assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(response.intent()).isEqualTo(JobIntent.RETRIES_UPDATED);
    }

    private void updatePayload(final long workflowInstanceKey, final long activityInstanceKey, byte[] payload)
    {
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.UPDATE_PAYLOAD)
            .key(activityInstanceKey)
            .command()
                .put("workflowInstanceKey", workflowInstanceKey)
                .put("payload", payload)
                .done()
            .sendAndAwait();

        assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(response.intent()).isEqualTo(WorkflowInstanceIntent.PAYLOAD_UPDATED);
    }

    private void updatePayload(long workflowInstanceKey, SubscribedRecord activityInstanceEvent, String payload) throws IOException
    {
        updatePayload(workflowInstanceKey, activityInstanceEvent.key(), MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree(payload)));
    }

    private void cancelWorkflowInstance(final long workflowInstanceKey)
    {
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .key(workflowInstanceKey)
            .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CANCEL)
            .command()
                .done()
            .sendAndAwait();

        assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(response.intent()).isEqualTo(WorkflowInstanceIntent.CANCELED);
    }
}
