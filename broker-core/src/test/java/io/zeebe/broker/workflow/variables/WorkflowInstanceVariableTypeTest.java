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
package io.zeebe.broker.workflow.variables;

import static io.zeebe.broker.workflow.gateway.ParallelGatewayStreamProcessorTest.PROCESS_ID;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Assertions;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.VariableRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.VariableIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.record.RecordingExporter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WorkflowInstanceVariableTypeTest {

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomixAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Parameter(0)
  public String payload;

  @Parameter(1)
  public String expectedValue;

  @Parameters(name = "with payload: {0}")
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

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldWriteVariableCreatedEvent() {
    // given
    testClient.deploy(WORKFLOW);

    // when
    final long workflowInstanceKey = testClient.createWorkflowInstance(PROCESS_ID, payload);

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.CREATED).getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(workflowInstanceKey)
        .hasName("x")
        .hasValue(expectedValue);
  }
}
