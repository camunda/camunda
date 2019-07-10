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
package io.zeebe.engine.processor.workflow.variable.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.test.util.JsonUtil;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JobInputMappingTest {

  private static final String PROCESS_ID = "process";

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter(0)
  public String initialVariables;

  @Parameter(1)
  public Consumer<ServiceTaskBuilder> mappings;

  @Parameter(2)
  public String expectedVariables;

  @Parameters(name = "from {0} to {2}")
  public static Object[][] parameters() {
    return new Object[][] {
      {"{}", mapping(b -> {}), "{}"},
      {"{'x': 1, 'y': 2}", mapping(b -> {}), "{'x': 1, 'y': 2}"},
      {"{'x': {'y': 2}}", mapping(b -> {}), "{'x': {'y': 2}}"},
      {"{'x': 1}", mapping(b -> b.zeebeInput("x", "y")), "{'x': 1, 'y': 1}"},
      {
        "{'x': 1}",
        mapping(b -> b.zeebeInput("x", "y").zeebeInput("x", "z")),
        "{'x': 1, 'y': 1, 'z': 1}"
      },
      {"{'x': {'y': 2}}", mapping(b -> b.zeebeInput("x.y", "y")), "{'x': {'y': 2}, 'y': 2}"},
    };
  }

  @Test
  public void shouldApplyInputMappings() {
    // given
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    "service",
                    builder -> {
                      builder.zeebeTaskType("test");
                      mappings.accept(builder);
                    })
                .endEvent()
                .done())
        .deploy()
        .getValue()
        .getDeployedWorkflows()
        .get(0)
        .getWorkflowKey();

    // when
    final long workflowInstanceKey =
        ENGINE_RULE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(initialVariables)
            .create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .await();
    ENGINE_RULE.jobs().withType("test").activate();

    // then
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    assertThat(jobCreated.getValue().getVariables())
        .isEqualTo(JsonUtil.fromJsonAsMap(expectedVariables));
  }

  private static Consumer<ServiceTaskBuilder> mapping(Consumer<ServiceTaskBuilder> mappingBuilder) {
    return mappingBuilder;
  }
}
