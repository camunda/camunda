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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.zeebe.model.bpmn.builder.ZeebePayloadMappingBuilder;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ActivityOutputMappingTest {

  private static final String PROCESS_ID = "process";

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Parameter(0)
  public String initialPayload;

  @Parameter(1)
  public Consumer<SubProcessBuilder> mappings;

  @Parameter(2)
  public List<Tuple> expectedScopeVariables;

  @Parameters(name = "from {0} to {2}")
  public static Object[][] parameters() {
    return new Object[][] {
      {"{'x': 1}", mapping(b -> b.zeebeOutput("$.x", "$.y")), scopeVariables(tuple("y", "1"))},
      {
        "{'x': 1, 'y': 2}",
        mapping(b -> b.zeebeOutput("$.y", "$.z")),
        scopeVariables(tuple("z", "2"))
      },
      {
        "{'x': 1}",
        mapping(b -> b.zeebeInput("$.x", "$.y").zeebeOutput("$.y", "$.z")),
        scopeVariables(tuple("z", "1"))
      },
      {
        "{'x': 1}",
        mapping(b -> b.zeebeInput("$.x", "$.y").zeebeOutput("$.x", "$.z")),
        scopeVariables(tuple("z", "1"))
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> b.zeebeOutput("$.x", "$.z")),
        scopeVariables(tuple("z", "{\"y\":2}"))
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> b.zeebeOutput("$.x.y", "$.z")),
        scopeVariables(tuple("z", "2"))
      },
    };
  }

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldApplyOutputMappings() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "sub",
                b -> {
                  b.embeddedSubProcess()
                      .startEvent()
                      .serviceTask("task", t -> t.zeebeTaskType("test"))
                      .endEvent();

                  mappings.accept(b);
                })
            .endEvent()
            .done());

    // when
    final long scopeInstanceKey = testClient.createWorkflowInstance(PROCESS_ID, initialPayload);

    testClient.completeJobOfType("test");

    // then
    final Record<WorkflowInstanceRecordValue> taskCompleted =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementId("task")
            .getFirst();

    assertThat(
            RecordingExporter.variableRecords()
                .skipUntil(r -> r.getPosition() > taskCompleted.getPosition())
                .withScopeInstanceKey(scopeInstanceKey)
                .limit(expectedScopeVariables.size()))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getName(), v.getValue()))
        .hasSize(expectedScopeVariables.size())
        .containsAll(expectedScopeVariables);
  }

  private static Consumer<ZeebePayloadMappingBuilder<SubProcessBuilder>> mapping(
      Consumer<ZeebePayloadMappingBuilder<SubProcessBuilder>> mappingBuilder) {
    return mappingBuilder;
  }

  private static List<Tuple> scopeVariables(Tuple... variables) {
    return Arrays.asList(variables);
  }
}
