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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.VariableDocumentUpdateSemantic;
import io.zeebe.protocol.intent.VariableDocumentIntent;
import io.zeebe.protocol.intent.VariableIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.collection.Maps;
import io.zeebe.test.util.record.RecordStream;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class UpdateVariableDocumentTest {

  public static EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public static ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);
  @ClassRule public static RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Rule
  public RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldProduceCorrectSequenceOfEvents() {
    // given
    final String processId = "process";
    final String taskId = "task";
    final String type = UUID.randomUUID().toString();
    final BpmnModelInstance process = newWorkflow(processId, taskId, type);
    final Map<String, Object> document = Maps.of(entry("x", 2), entry("foo", "bar"));

    // when
    testClient.deploy(process);
    final long workflowInstanceKey =
        testClient
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(processId).setVariables(MsgPackUtil.asMsgPack("{'x': 1}")))
            .getInstanceKey();
    final Record<WorkflowInstanceRecordValue> activatedEvent = waitForActivityActivatedEvent();
    testClient.updateVariables(
        activatedEvent.getKey(), VariableDocumentUpdateSemantic.PROPAGATE, document);
    testClient.completeJobOfType(type);

    // then
    final long completedPosition =
        getWorkflowInstanceCompletedPosition(processId, workflowInstanceKey);
    final Supplier<RecordStream> recordsSupplier =
        () -> RecordingExporter.records().between(activatedEvent.getPosition(), completedPosition);

    assertVariableRecordsProduced(workflowInstanceKey, recordsSupplier);
    assertVariableDocumentEventProduced(document, activatedEvent, recordsSupplier);
  }

  private void assertVariableDocumentEventProduced(
      Map<String, Object> document,
      Record<WorkflowInstanceRecordValue> activatedEvent,
      Supplier<RecordStream> records) {
    assertThat(
            records
                .get()
                .variableDocumentRecords()
                .withIntent(VariableDocumentIntent.UPDATED)
                .withScopeKey(activatedEvent.getKey())
                .withUpdateSemantics(VariableDocumentUpdateSemantic.PROPAGATE)
                .withDocument(document)
                .getFirst())
        .isNotNull();
  }

  private void assertVariableRecordsProduced(
      long workflowInstanceKey, Supplier<RecordStream> records) {
    assertThat(records.get().variableRecords().withWorkflowInstanceKey(workflowInstanceKey))
        .hasSize(2)
        .extracting(
            r ->
                tuple(
                    r.getMetadata().getIntent(),
                    r.getValue().getScopeKey(),
                    r.getValue().getName(),
                    r.getValue().getValue()))
        .contains(
            tuple(VariableIntent.CREATED, workflowInstanceKey, "foo", "\"bar\""),
            tuple(VariableIntent.UPDATED, workflowInstanceKey, "x", "2"));
  }

  private BpmnModelInstance newWorkflow(String processId, String taskId, String type) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .serviceTask(taskId, b -> b.zeebeTaskType(type))
        .endEvent()
        .done();
  }

  private long getWorkflowInstanceCompletedPosition(String processId, long workflowInstanceKey) {
    return testClient
        .receiveElementInState(
            workflowInstanceKey, processId, WorkflowInstanceIntent.ELEMENT_COMPLETED)
        .getPosition();
  }

  private Record<WorkflowInstanceRecordValue> waitForActivityActivatedEvent() {
    return testClient.receiveFirstWorkflowInstanceEvent(
        WorkflowInstanceIntent.ELEMENT_ACTIVATED, BpmnElementType.SERVICE_TASK);
  }
}
