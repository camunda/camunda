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
package io.zeebe.broker.workflow;

import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.OutputBehavior;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static io.zeebe.broker.test.MsgPackUtil.*;
import static io.zeebe.msgpack.spec.MsgPackHelper.EMTPY_OBJECT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Represents a test class to test the input and output mappings for
 * tasks inside a workflow.
 */
public class WorkflowTaskIOMappingTest
{
    private static final String PROP_JOB_PAYLOAD = "payload";

    private static final String NODE_STRING_PATH = "$.string";
    private static final String NODE_JSON_OBJECT_PATH = "$.jsonObject";
    private static final String NODE_ROOT_PATH = "$";

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    private TestTopicClient testClient;


    @Before
    public void init()
    {
        testClient = apiRule.topic();
    }

    @Test
    public void shouldUseDefaultInputMappingIfNoMappingIsSpecified() throws IOException
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .serviceTask("service", t -> t.taskType("external"))
                .endEvent()
                .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // then
        final SubscribedRecord event = testClient.receiveFirstJobCommand(JobIntent.CREATE);

        assertThat(event.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        final byte[] result = (byte[]) event.value().get(PROP_JOB_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(JSON_DOCUMENT));
    }

    @Test
    public void shouldCreateTwoNewObjectsViaInputMapping() throws IOException
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .serviceTask("service", t -> t.taskType("external")
                             .input(NODE_STRING_PATH, "$.newFoo")
                             .input(NODE_JSON_OBJECT_PATH, "$.newObj"))
                .endEvent()
                .done());

        // when
        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);
        final SubscribedRecord event = testClient.receiveFirstJobCommand(JobIntent.CREATE);

        // then payload is expected as
        final byte[] result = (byte[]) event.value().get(PROP_JOB_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree("{'newFoo':'value', 'newObj':{'testAttr':'test'}}"));
    }

    @Test
    public void shouldUseEmptyObjectIfCreatedWithNoPayload()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .serviceTask("service", t -> t.taskType("external"))
                .endEvent()
                .done());

        // when
        testClient.createWorkflowInstance("process");
        final SubscribedRecord event = testClient.receiveFirstJobCommand(JobIntent.CREATE);

        // then
        assertThat(event.value()).containsEntry(WorkflowInstanceRecord.PROP_WORKFLOW_PAYLOAD, EMTPY_OBJECT);
    }


    @Test
    public void shouldCreateIncidentForNoMatchOnInputMapping()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .serviceTask("service", t -> t.taskType("external")
                             .input("$.notExisting", NODE_ROOT_PATH))
                .endEvent()
                .done());

        // when
        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);

        // then incident is created
        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.notExisting.");
    }

    @Test
    public void shouldCreateIncidentForNonMatchingAndMatchingValueOnInputMapping()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .serviceTask("service", t -> t.taskType("external")
                             .input("$.notExisting", "$.nullVal")
                             .input(NODE_STRING_PATH, "$.existing"))

                .endEvent()
                .done());

        // when
        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);

        // then incident is created
        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.notExisting.");
    }

    @Test
    public void shouldUseDefaultOutputMapping() throws IOException
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .serviceTask("service", t -> t.taskType("external"))
                .endEvent()
                .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeJobOfType("external", OTHER_PAYLOAD);

        // then
        final SubscribedRecord activityCompletedEvent =
            testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        final byte[] result = (byte[]) activityCompletedEvent.value().get(PROP_JOB_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(MERGED_OTHER_WITH_JSON_DOCUMENT));
    }

    @Test
    public void shouldUseDefaultOutputMappingWithNoWorkflowPayload() throws IOException
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("service", t -> t.taskType("external"))
            .endEvent()
            .done());

        testClient.createWorkflowInstance("process");

        // when
        testClient.completeJobOfType("external", OTHER_PAYLOAD);

        // then
        final SubscribedRecord activityCompletedEvent =
            testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        final byte[] result = (byte[]) activityCompletedEvent.value().get(PROP_JOB_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(OTHER_DOCUMENT));
    }

    @Test
    public void shouldUseOutputMappingWithNoWorkflowPayload() throws IOException
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("service", t -> t.taskType("external").output("$.string", "$.foo"))
            .endEvent()
            .done());

        testClient.createWorkflowInstance("process");

        // when
        testClient.completeJobOfType("external", OTHER_PAYLOAD);

        // then
        final SubscribedRecord activityCompletedEvent =
            testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        final byte[] result = (byte[]) activityCompletedEvent.value().get(PROP_JOB_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree("{'foo':'bar'}"));
    }

    @Test
    public void shouldUseNoneOutputBehaviorWithoutCompletePayload() throws IOException
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("service", t -> t.taskType("external")
                .outputBehavior(OutputBehavior.NONE))
            .endEvent()
            .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeJobOfType("external");

        // then
        final SubscribedRecord activityCompletedEvent =
            testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        final byte[] result = (byte[]) activityCompletedEvent.value().get(PROP_JOB_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(JSON_DOCUMENT));
    }

    @Test
    public void shouldUseNoneOutputBehaviorAndCompletePayload() throws IOException
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("service", t -> t.taskType("external")
                .outputBehavior(OutputBehavior.NONE))
            .endEvent()
            .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeJobOfType("external", OTHER_PAYLOAD);

        // then
        final SubscribedRecord activityCompletedEvent =
            testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        final byte[] result = (byte[]) activityCompletedEvent.value().get(PROP_JOB_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(JSON_DOCUMENT));
    }


    @Test
    public void shouldUseOverwriteOutputBehaviorWithoutCompletePayload() throws IOException
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("service", t -> t.taskType("external")
                .outputBehavior(OutputBehavior.OVERWRITE))
            .endEvent()
            .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeJobOfType("external");

        // then
        final SubscribedRecord activityCompletedEvent =
            testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        final byte[] result = (byte[]) activityCompletedEvent.value().get(PROP_JOB_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree("{}"));
    }

    @Test
    public void shouldUseOverwriteOutputBehaviorAndCompletePayload() throws IOException
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("service", t -> t.taskType("external")
                .outputBehavior(OutputBehavior.OVERWRITE))
            .endEvent()
            .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeJobOfType("external", OTHER_PAYLOAD);

        // then
        final SubscribedRecord activityCompletedEvent =
            testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        final byte[] result = (byte[]) activityCompletedEvent.value().get(PROP_JOB_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(OTHER_DOCUMENT));
    }

    @Test
    public void shouldUseOverwriteOutputBehaviorWithOutputMappingAndCompletePayload() throws IOException
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("service", t -> t.taskType("external")
                .outputBehavior(OutputBehavior.OVERWRITE)
                .output("$.string", "$.foo"))
            .endEvent()
            .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeJobOfType("external", OTHER_PAYLOAD);

        // then
        final SubscribedRecord activityCompletedEvent =
            testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        final byte[] result = (byte[]) activityCompletedEvent.value().get(PROP_JOB_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree("{'foo':'bar'}"));
    }

    @Test
    public void shouldCreateIncidentOnOverwriteOutputBehaviorWithOutputMappingAndWithoutCompletedPayload()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("service", t -> t.taskType("external")
                .outputBehavior(OutputBehavior.OVERWRITE)
                .output("$.string", "$.foo"))
            .endEvent()
            .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeJobOfType("external");

        // then incident is created
        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.string.");
    }

    @Test
    public void shouldUseDefaultOutputMappingWithNoCompletePayload() throws IOException
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("service", t -> t.taskType("external"))
            .endEvent()
            .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeJobOfType("external");

        // then
        final SubscribedRecord activityCompletedEvent =
            testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        final byte[] result = (byte[]) activityCompletedEvent.value().get(PROP_JOB_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(JSON_DOCUMENT));
    }

    @Test
    public void shouldUseDefaultOutputMappingWithNoCreatedPayload() throws IOException
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("service", t -> t.taskType("external"))
            .endEvent()
            .done());

        testClient.createWorkflowInstance("process");

        // when
        testClient.completeJobOfType("external", OTHER_PAYLOAD);

        // then
        final SubscribedRecord activityCompletedEvent =
            testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        final byte[] result = (byte[]) activityCompletedEvent.value().get(PROP_JOB_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(OTHER_DOCUMENT));
    }

    @Test
    public void shouldNotSeePayloadOfWorkflowInstanceBefore() throws IOException
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external"))
                          .endEvent()
                          .done());

        final long firstWFInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);
        final long secondWFInstanceKey = testClient.createWorkflowInstance("process");

        // when
        testClient.completeJobOfWorkflowInstance("external", firstWFInstanceKey, MSGPACK_PAYLOAD);
        testClient.completeJobOfWorkflowInstance("external", secondWFInstanceKey, MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'foo':'bar'}")));

        // then first event payload is expected as
        final SubscribedRecord firstWFActivityCompletedEvent =
            testClient.receiveFirstWorkflowInstanceEvent(firstWFInstanceKey, WorkflowInstanceIntent.ACTIVITY_COMPLETED);
        byte[] payload = (byte[]) firstWFActivityCompletedEvent.value().get(PROP_JOB_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(payload)).isEqualTo(JSON_MAPPER.readTree(JSON_DOCUMENT));

        // and second event payload is expected as
        final SubscribedRecord secondWFActivityCompletedEvent =
            testClient.receiveFirstWorkflowInstanceEvent(secondWFInstanceKey, WorkflowInstanceIntent.ACTIVITY_COMPLETED);
        payload = (byte[]) secondWFActivityCompletedEvent.value().get(PROP_JOB_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(payload)).isEqualTo(JSON_MAPPER.readTree("{'foo':'bar'}"));
    }

    @Test
    public void shouldNotSeePayloadOfWorkflowInstanceBeforeOnOutputMapping() throws IOException
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external")
                                       .output("$", "$.taskPayload"))
                          .endEvent()
                          .done());

        final long firstWFInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);
        final long secondWFInstanceKey = testClient.createWorkflowInstance("process",
                                                                           MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'otherPayload':'value'}")));

        // when
        testClient.completeJobOfWorkflowInstance("external", firstWFInstanceKey, MSGPACK_PAYLOAD);
        testClient.completeJobOfWorkflowInstance("external", secondWFInstanceKey, MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'foo':'bar'}")));

        // then first event payload is expected as
        final SubscribedRecord firstWFActivityCompletedEvent =
            testClient.receiveFirstWorkflowInstanceEvent(firstWFInstanceKey, WorkflowInstanceIntent.ACTIVITY_COMPLETED);
        byte[] payload = (byte[]) firstWFActivityCompletedEvent.value().get(PROP_JOB_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(payload))
            .isEqualTo(JSON_MAPPER.readTree("{'string':'value', 'jsonObject':{'testAttr':'test'},'taskPayload':{'string':'value', 'jsonObject':{'testAttr':'test'}}}"));

        // and second event payload is expected as
        final SubscribedRecord secondWFActivityCompletedEvent =
            testClient.receiveFirstWorkflowInstanceEvent(secondWFInstanceKey, WorkflowInstanceIntent.ACTIVITY_COMPLETED);
        payload = (byte[]) secondWFActivityCompletedEvent.value().get(PROP_JOB_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(payload))
            .isEqualTo(JSON_MAPPER.readTree("{'otherPayload':'value','taskPayload':{'foo':'bar'}}"));
    }

    @Test
    public void shouldUseDefaultOutputMappingIfOnlyInputMappingSpecified() throws IOException
    {
        // given
        final Map<String, String> inputMapping = new HashMap<>();
        inputMapping.put(NODE_ROOT_PATH, NODE_ROOT_PATH);
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external")
                                       .input(NODE_ROOT_PATH, NODE_ROOT_PATH))
                          .endEvent()
                          .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeJobOfType("external", OTHER_PAYLOAD);

        // then
        final SubscribedRecord activityCompletedEvent =
            testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        final byte[] result = (byte[]) activityCompletedEvent.value().get(PROP_JOB_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(MERGED_OTHER_WITH_JSON_DOCUMENT));
    }

    @Test
    public void shouldUseWFPayloadIfCompleteWithNoPayload()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external"))
                          .endEvent()
                          .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeJobOfType("external");

        // then
        final SubscribedRecord activityCompletedEvent =
            testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        assertThat(activityCompletedEvent.value())
            .containsEntry(WorkflowInstanceRecord.PROP_WORKFLOW_PAYLOAD, MSGPACK_PAYLOAD);
    }

    @Test
    public void shouldUseOutputMappingToAddObjectsToWorkflowPayload() throws IOException
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external")
                              .output(NODE_STRING_PATH, "$.newFoo")
                              .output(NODE_JSON_OBJECT_PATH, "$.newObj"))
                          .endEvent()
                          .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeJobOfType("external", MSGPACK_PAYLOAD);
        final SubscribedRecord activityCompletedEvent =
            testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        // then payload contains old objects
        final byte[] result = (byte[]) activityCompletedEvent.value().get(PROP_JOB_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(
                "{'newFoo':'value', 'newObj':{'testAttr':'test'}," +
                       " 'string':'value', 'jsonObject':{'testAttr':'test'}}"));
    }

    @Test
    public void shouldCreateIncidentForNotMatchingOnOutputMapping()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external")
                              .output("$.notExisting", "$.notExist"))
                          .endEvent()
                          .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeJobOfType("external", MSGPACK_PAYLOAD);
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_ACTIVATED);

        final SubscribedRecord incidentEvent = testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);

        // then incident is created
        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.notExisting.");
    }

    @Test
    public void shouldUseInOutMapping() throws IOException
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external")
                              .input(NODE_JSON_OBJECT_PATH, NODE_ROOT_PATH)
                              .output("$.testAttr", "$.result"))
                          .endEvent()
                          .done());

        // when
        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);
        final SubscribedRecord event = testClient.receiveFirstJobCommand(JobIntent.CREATE);

        // then payload is expected as
        byte[] result = (byte[]) event.value().get(PROP_JOB_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(
                "{'testAttr':'test'}"));

        // when
        testClient.completeJobOfType("external", MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'testAttr':123}")));

        // then
        final SubscribedRecord activityCompletedEvent =
                testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        result = (byte[]) activityCompletedEvent.value().get(PROP_JOB_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(
                "{'string':'value', 'jsonObject':{'testAttr':'test'}, 'result':123}"));
    }
}
