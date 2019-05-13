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

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.VariableRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.VariableIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.agrona.DirectBuffer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WorkflowInstanceVariableTypeTest {

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done();

  public static EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public static ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);

  @ClassRule public static RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Rule
  public RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter(0)
  public String variables;

  @Parameter(1)
  public String expectedValue;

  @Parameters(name = "with variables: {0}")
  public static Object[][] parameters() {
    return new Object[][] {
      {"{'x':'foo'}", "\"foo\""},
      {"{'x':123}", "123"},
      {"{'x':true}", "true"},
      {"{'x':false}", "false"},
      {"{'x':null}", "null"},
      {"{'x':[1,2,3]}", "[1,2,3]"},
      {"{'x':{'y':123}}", "{\"y\":123}"},
    };
  }

  @BeforeClass
  public static void deployWorkflow() {
    apiRule.deployWorkflow(WORKFLOW).getValue().getDeployedWorkflows().get(0).getWorkflowKey();
  }

  @Test
  public void shouldWriteVariableCreatedEvent() {
    // when
    final DirectBuffer variables = MsgPackUtil.asMsgPack(this.variables);
    final long workflowInstanceKey =
        apiRule
            .partitionClient()
            .createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID).setVariables(variables))
            .getInstanceKey();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(workflowInstanceKey)
        .hasName("x")
        .hasValue(expectedValue);
  }
}
