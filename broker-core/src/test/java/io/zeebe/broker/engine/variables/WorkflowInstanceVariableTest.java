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
package io.zeebe.broker.engine.variables;

import static io.zeebe.broker.engine.JobAssert.PROCESS_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.VariableRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.VariableIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.collection.Maps;
import io.zeebe.test.util.record.RecordingExporter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class WorkflowInstanceVariableTest {

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeTaskType("test"))
          .endEvent()
          .done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldCreateVariableByWorkflowInstanceCreation() {
    // given
    final long workflowKey = testClient.deployWorkflow(WORKFLOW).getKey();

    // when
    final long workflowInstanceKey =
        testClient
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(PROCESS_ID).setVariables(MsgPackUtil.asMsgPack("{'x':1}")))
            .getInstanceKey();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.CREATED).getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(workflowInstanceKey)
        .hasWorkflowKey(workflowKey)
        .hasName("x")
        .hasValue("1");
  }

  @Test
  public void shouldCreateVariableByJobCompletion() {
    // given
    final long workflowKey = testClient.deployWorkflow(WORKFLOW).getKey();

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID)).getInstanceKey();

    // when
    testClient.completeJobOfType("test", "{'x':1}");

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.CREATED).getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(workflowInstanceKey)
        .hasWorkflowKey(workflowKey)
        .hasName("x")
        .hasValue("1");
  }

  @Test
  public void shouldCreateVariableByOutputMapping() {
    // given
    final long workflowKey =
        testClient
            .deployWorkflow(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .serviceTask("task", t -> t.zeebeTaskType("test").zeebeOutput("x", "y"))
                    .endEvent()
                    .done())
            .getKey();

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID)).getInstanceKey();

    // when
    testClient.completeJobOfType("test", "{'x':1}");

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.CREATED).withName("y").getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(workflowInstanceKey)
        .hasWorkflowKey(workflowKey)
        .hasName("y")
        .hasValue("1");
  }

  @Test
  public void shouldCreateVariableByUpdateVariables() {
    // given
    final long workflowKey = testClient.deployWorkflow(WORKFLOW).getKey();

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID)).getInstanceKey();

    // when
    testClient.updateVariables(workflowInstanceKey, Maps.of(entry("x", 1)));

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.CREATED).getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(workflowInstanceKey)
        .hasWorkflowKey(workflowKey)
        .hasName("x")
        .hasValue("1");
  }

  @Test
  public void shouldCreateMultipleVariables() {
    // given
    testClient.deploy(WORKFLOW);

    // when
    testClient
        .createWorkflowInstance(
            r ->
                r.setBpmnProcessId(PROCESS_ID)
                    .setVariables(MsgPackUtil.asMsgPack("{'x':1, 'y':2}")))
        .getInstanceKey();

    // then
    assertThat(RecordingExporter.variableRecords(VariableIntent.CREATED).limit(2))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getName(), v.getValue()))
        .hasSize(2)
        .contains(tuple("x", "1"), tuple("y", "2"));
  }

  @Test
  public void shouldUpdateVariableByJobCompletion() {
    // given
    final long workflowKey = testClient.deployWorkflow(WORKFLOW).getKey();

    final long workflowInstanceKey =
        testClient
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(PROCESS_ID).setVariables(MsgPackUtil.asMsgPack("{'x':1}")))
            .getInstanceKey();

    // when
    testClient.completeJobOfType("test", "{'x':2}");

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.UPDATED).getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(workflowInstanceKey)
        .hasWorkflowKey(workflowKey)
        .hasName("x")
        .hasValue("2");
  }

  @Test
  public void shouldUpdateVariableByOutputMapping() {
    // given
    final long workflowKey =
        testClient
            .deployWorkflow(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .serviceTask("task", t -> t.zeebeTaskType("test").zeebeOutput("x", "y"))
                    .endEvent()
                    .done())
            .getKey();

    final long workflowInstanceKey =
        testClient
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(PROCESS_ID).setVariables(MsgPackUtil.asMsgPack("{'y':1}")))
            .getInstanceKey();

    // when
    testClient.completeJobOfType("test", "{'x':2}");

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.UPDATED).getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(workflowInstanceKey)
        .hasWorkflowKey(workflowKey)
        .hasName("y")
        .hasValue("2");
  }

  @Test
  public void shouldUpdateVariableByUpdateVariables() {
    // given
    final long workflowKey = testClient.deployWorkflow(WORKFLOW).getKey();

    final long workflowInstanceKey =
        testClient
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(PROCESS_ID).setVariables(MsgPackUtil.asMsgPack("{'x':1}")))
            .getInstanceKey();

    // when
    testClient.updateVariables(workflowInstanceKey, Maps.of(entry("x", 2)));

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.UPDATED).getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(workflowInstanceKey)
        .hasWorkflowKey(workflowKey)
        .hasName("x")
        .hasValue("2");
  }

  @Test
  public void shouldUpdateMultipleVariables() {
    // given
    testClient.deployWorkflow(WORKFLOW);
    testClient.createWorkflowInstance(
        r ->
            r.setBpmnProcessId(PROCESS_ID)
                .setVariables(MsgPackUtil.asMsgPack("{'x':1, 'y':2, 'z':3}")));

    // when
    testClient.completeJobOfType("test", "{'x':1, 'y':4, 'z':5}");

    // then
    assertThat(RecordingExporter.variableRecords(VariableIntent.UPDATED).limit(2))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getName(), v.getValue()))
        .hasSize(2)
        .contains(tuple("y", "4"), tuple("z", "5"));
  }

  @Test
  public void shouldCreateAndUpdateVariables() {
    // given
    final long workflowKey = testClient.deployWorkflow(WORKFLOW).getKey();

    final long workflowInstanceKey =
        testClient
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(PROCESS_ID).setVariables(MsgPackUtil.asMsgPack("{'x':1}")))
            .getInstanceKey();

    final Record<VariableRecordValue> variableCreated =
        RecordingExporter.variableRecords(VariableIntent.CREATED).getFirst();

    // when
    testClient.updateVariables(workflowInstanceKey, Maps.of(entry("x", 2), entry("y", 3)));

    // then
    assertThat(
            RecordingExporter.variableRecords()
                .skipUntil(r -> r.getPosition() > variableCreated.getPosition())
                .limit(2))
        .extracting(
            record ->
                tuple(
                    record.getMetadata().getIntent(),
                    record.getValue().getWorkflowKey(),
                    record.getValue().getName(),
                    record.getValue().getValue()))
        .hasSize(2)
        .contains(
            tuple(VariableIntent.UPDATED, workflowKey, "x", "2"),
            tuple(VariableIntent.CREATED, workflowKey, "y", "3"));
  }

  @Test
  public void shouldHaveSameKeyOnVariableUpdate() {
    // given
    testClient.deploy(WORKFLOW);

    final long workflowInstanceKey =
        testClient
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(PROCESS_ID).setVariables(MsgPackUtil.asMsgPack("{'x':1}")))
            .getInstanceKey();

    final Record<VariableRecordValue> variableCreated =
        RecordingExporter.variableRecords(VariableIntent.CREATED).getFirst();

    // when
    testClient.updateVariables(workflowInstanceKey, Maps.of(entry("x", 2)));

    // then
    final Record<VariableRecordValue> variableUpdated =
        RecordingExporter.variableRecords(VariableIntent.UPDATED).getFirst();

    assertThat(variableCreated.getKey()).isEqualTo(variableUpdated.getKey());
  }
}
