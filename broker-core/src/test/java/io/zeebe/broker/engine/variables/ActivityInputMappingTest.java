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
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.zeebe.model.bpmn.builder.ZeebeVariablesMappingBuilder;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.assertj.core.groups.Tuple;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ActivityInputMappingTest {

  private static final String PROCESS_ID = "process";

  public static EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public static ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);

  @ClassRule public static RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Rule
  public RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter(0)
  public String initialVariables;

  @Parameter(1)
  public Consumer<SubProcessBuilder> mappings;

  @Parameter(2)
  public List<Tuple> expectedActivityVariables;

  @Parameters(name = "from {0} to {2}")
  public static Object[][] parameters() {
    return new Object[][] {
      {"{'x': 1}", mapping(b -> b.zeebeInput("x", "x")), activityVariables(tuple("x", "1"))},
      {"{'x': 1}", mapping(b -> b.zeebeInput("x", "y")), activityVariables(tuple("y", "1"))},
      {
        "{'x': 1, 'y': 2}", mapping(b -> b.zeebeInput("y", "z")), activityVariables(tuple("z", "2"))
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> b.zeebeInput("x", "x")),
        activityVariables(tuple("x", "{\"y\":2}"))
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> b.zeebeInput("x.y", "y")),
        activityVariables(tuple("y", "2"))
      },
    };
  }

  @Test
  public void shouldApplyInputMappings() {
    // given
    final long workflowKey =
        apiRule
            .deployWorkflow(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .subProcess(
                        "sub",
                        b -> {
                          b.embeddedSubProcess().startEvent().endEvent();

                          mappings.accept(b);
                        })
                    .endEvent()
                    .done())
            .getValue()
            .getDeployedWorkflows()
            .get(0)
            .getWorkflowKey();

    // when
    final DirectBuffer variables = MsgPackUtil.asMsgPack(initialVariables);
    final long workflowInstanceKey =
        apiRule
            .partitionClient()
            .createWorkflowInstance(r -> r.setKey(workflowKey).setVariables(variables))
            .getInstanceKey();

    // then
    final long flowScopeKey =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("sub")
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.variableRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withScopeKey(flowScopeKey)
                .limit(expectedActivityVariables.size()))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getName(), v.getValue()))
        .hasSize(expectedActivityVariables.size())
        .containsAll(expectedActivityVariables);
  }

  private static Consumer<ZeebeVariablesMappingBuilder<SubProcessBuilder>> mapping(
      Consumer<ZeebeVariablesMappingBuilder<SubProcessBuilder>> mappingBuilder) {
    return mappingBuilder;
  }

  private static List<Tuple> activityVariables(Tuple... variables) {
    return Arrays.asList(variables);
  }
}
