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
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.VariableRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.zeebe.model.bpmn.builder.ZeebeVariablesMappingBuilder;
import io.zeebe.protocol.intent.VariableIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JobOutputMappingTest {

  private static final String PROCESS_ID = "process";

  public static EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public static ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);

  @ClassRule public static RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Rule
  public RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter(0)
  public String jobVariables;

  @Parameter(1)
  public Consumer<ServiceTaskBuilder> mappings;

  @Parameter(2)
  public List<Tuple> expectedActivityVariables;

  @Parameter(3)
  public List<Tuple> expectedScopeVariables;

  @Parameters(name = "from {0} to activity: {2} and scope: {3}")
  public static Object[][] parameters() {
    return new Object[][] {
      // create variable
      {"{'x': 1}", mapping(b -> {}), activityVariables(), scopeVariables(tuple("x", "1"))},
      {
        "{'x': 1}",
        mapping(b -> b.zeebeOutput("x", "x")),
        activityVariables(tuple("x", "1")),
        scopeVariables(tuple("x", "1"))
      },
      {
        "{'x': 1}",
        mapping(b -> b.zeebeOutput("x", "y")),
        activityVariables(tuple("x", "1")),
        scopeVariables(tuple("y", "1"))
      },
      {
        "{'x': 1, 'y': 2}",
        mapping(b -> b.zeebeOutput("y", "z")),
        activityVariables(tuple("x", "1"), tuple("y", "2")),
        scopeVariables(tuple("z", "2"))
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> {}),
        activityVariables(),
        scopeVariables(tuple("x", "{\"y\":2}"))
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> b.zeebeOutput("x", "y")),
        activityVariables(tuple("x", "{\"y\":2}")),
        scopeVariables(tuple("y", "{\"y\":2}"))
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> b.zeebeOutput("x.y", "y")),
        activityVariables(tuple("x", "{\"y\":2}")),
        scopeVariables(tuple("y", "2"))
      },
      // update variable
      {"{'i': 1}", mapping(b -> {}), activityVariables(), scopeVariables(tuple("i", "1"))},
      {
        "{'x': 1}",
        mapping(b -> b.zeebeOutput("x", "i")),
        activityVariables(tuple("x", "1")),
        scopeVariables(tuple("i", "1"))
      },
      // combine input and output mapping
      {
        "{'x': 1}",
        mapping(b -> b.zeebeInput("i", "y").zeebeOutput("y", "z")),
        activityVariables(tuple("x", "1"), tuple("y", "0")),
        scopeVariables(tuple("z", "0"))
      },
      {
        "{'x': 1}",
        mapping(b -> b.zeebeInput("i", "x").zeebeOutput("x", "y")),
        activityVariables(tuple("x", "0"), tuple("x", "1")),
        scopeVariables(tuple("y", "1"))
      },
      {
        "{'x': 1, 'y': 2}",
        mapping(b -> b.zeebeInput("i", "x").zeebeInput("i", "y").zeebeOutput("y", "z")),
        activityVariables(tuple("x", "0"), tuple("y", "0"), tuple("x", "1"), tuple("y", "2")),
        scopeVariables(tuple("z", "2"))
      },
      {
        "{'x': 1}",
        mapping(b -> b.zeebeInput("i", "y")),
        activityVariables(tuple("y", "0")),
        scopeVariables(tuple("x", "1"))
      },
      {
        "{'z': 1, 'j': 1}",
        mapping(b -> b.zeebeInput("i", "z")),
        activityVariables(tuple("z", "0")),
        scopeVariables(tuple("j", "1"))
      },
    };
  }

  private String jobType;

  @Before
  public void init() {
    jobType = UUID.randomUUID().toString();
  }

  @Test
  public void shouldApplyOutputMappings() {
    // given
    final long workflowKey =
        apiRule
            .deployWorkflow(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .serviceTask(
                        "task",
                        builder -> {
                          builder.zeebeTaskType(jobType);
                          mappings.accept(builder);
                        })
                    .endEvent()
                    .done())
            .getValue()
            .getDeployedWorkflows()
            .get(0)
            .getWorkflowKey();

    // when
    final long workflowInstanceKey =
        apiRule
            .partitionClient()
            .createWorkflowInstance(
                r -> r.setKey(workflowKey).setVariables(MsgPackUtil.asMsgPack("i", 0)))
            .getInstanceKey();
    apiRule.completeJob(jobType, MsgPackUtil.asMsgPack(jobVariables));

    // then
    final long elementInstanceKey =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("task")
            .getFirst()
            .getKey();

    final Record<VariableRecordValue> initialVariable =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withName("i")
            .getFirst();

    assertThat(
            RecordingExporter.variableRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .skipUntil(r -> r.getPosition() > initialVariable.getPosition())
                .withScopeKey(elementInstanceKey)
                .limit(expectedActivityVariables.size()))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getName(), v.getValue()))
        .hasSize(expectedActivityVariables.size())
        .containsAll(expectedActivityVariables);

    assertThat(
            RecordingExporter.variableRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .skipUntil(r -> r.getPosition() > initialVariable.getPosition())
                .withScopeKey(workflowInstanceKey)
                .limit(expectedScopeVariables.size()))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getName(), v.getValue()))
        .hasSize(expectedScopeVariables.size())
        .containsAll(expectedScopeVariables);
  }

  private static Consumer<ZeebeVariablesMappingBuilder<ServiceTaskBuilder>> mapping(
      Consumer<ZeebeVariablesMappingBuilder<ServiceTaskBuilder>> mappingBuilder) {
    return mappingBuilder;
  }

  private static List<Tuple> activityVariables(Tuple... variables) {
    return Arrays.asList(variables);
  }

  private static List<Tuple> scopeVariables(Tuple... variables) {
    return Arrays.asList(variables);
  }
}
