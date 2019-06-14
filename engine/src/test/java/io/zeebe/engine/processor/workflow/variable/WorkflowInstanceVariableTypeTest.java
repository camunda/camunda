/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow.variable;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.value.VariableRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.intent.VariableIntent;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WorkflowInstanceVariableTypeTest {

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done();

  @ClassRule public static final EngineRule ENGINE_RULE = new EngineRule();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
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
    ENGINE_RULE.deployment().withXmlResource(WORKFLOW).deploy();
  }

  @Test
  public void shouldWriteVariableCreatedEvent() {
    // when
    final long workflowInstanceKey =
        ENGINE_RULE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(this.variables)
            .create();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    final VariableRecordValue value = variableRecord.getValue();
    assertThat(value.getScopeKey()).isEqualTo(workflowInstanceKey);
    assertThat(value.getName()).isEqualTo("x");
    assertThat(value.getValue()).isEqualTo(expectedValue);
  }
}
