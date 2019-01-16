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
import io.zeebe.exporter.record.value.VariableRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.zeebe.model.bpmn.builder.ZeebePayloadMappingBuilder;
import io.zeebe.protocol.intent.VariableIntent;
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
public class JobOutputMappingTest {

  private static final String PROCESS_ID = "process";

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Parameter(0)
  public String jobPayload;

  @Parameter(1)
  public Consumer<ServiceTaskBuilder> mappings;

  @Parameter(2)
  public List<Tuple> expectedActivtyVariables;

  @Parameter(3)
  public List<Tuple> expectedScopeVariables;

  @Parameters(name = "from {0} to activity: {2} and scope: {3}")
  public static Object[][] parameters() {
    return new Object[][] {
      // create variable
      {"{'x': 1}", mapping(b -> {}), activityVariables(), scopeVariables(tuple("x", "1"))},
      {
        "{'x': 1}",
        mapping(b -> b.zeebeOutput("$.x", "$.x")),
        activityVariables(tuple("x", "1")),
        scopeVariables(tuple("x", "1"))
      },
      {
        "{'x': 1}",
        mapping(b -> b.zeebeOutput("$.x", "$.y")),
        activityVariables(tuple("x", "1")),
        scopeVariables(tuple("y", "1"))
      },
      {
        "{'x': 1, 'y': 2}",
        mapping(b -> b.zeebeOutput("$.y", "$.z")),
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
        mapping(b -> b.zeebeOutput("$.x", "$.y")),
        activityVariables(tuple("x", "{\"y\":2}")),
        scopeVariables(tuple("y", "{\"y\":2}"))
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> b.zeebeOutput("$.x.y", "$.y")),
        activityVariables(tuple("x", "{\"y\":2}")),
        scopeVariables(tuple("y", "2"))
      },
      // update variable
      {"{'i': 1}", mapping(b -> {}), activityVariables(), scopeVariables(tuple("i", "1"))},
      {
        "{'x': 1}",
        mapping(b -> b.zeebeOutput("$.x", "$.i")),
        activityVariables(tuple("x", "1")),
        scopeVariables(tuple("i", "1"))
      },
      // combine input and output mapping
      {
        "{'x': 1}",
        mapping(b -> b.zeebeInput("$.i", "$.y").zeebeOutput("$.y", "$.z")),
        activityVariables(tuple("x", "1"), tuple("y", "0")),
        scopeVariables(tuple("z", "0"))
      },
      {
        "{'x': 1}",
        mapping(b -> b.zeebeInput("$.i", "$.x").zeebeOutput("$.x", "$.y")),
        activityVariables(tuple("x", "0"), tuple("x", "1")),
        scopeVariables(tuple("y", "1"))
      },
      {
        "{'x': 1, 'y': 2}",
        mapping(b -> b.zeebeInput("$.i", "$.x").zeebeInput("$.i", "$.y").zeebeOutput("$.y", "$.z")),
        activityVariables(tuple("x", "0"), tuple("y", "0"), tuple("x", "1"), tuple("y", "2")),
        scopeVariables(tuple("z", "2"))
      },
      {
        "{'x': 1}",
        mapping(b -> b.zeebeInput("$.i", "$.y")),
        activityVariables(tuple("y", "0")),
        scopeVariables(tuple("x", "1"))
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
            .serviceTask(
                "task",
                builder -> {
                  builder.zeebeTaskType("test");
                  mappings.accept(builder);
                })
            .endEvent()
            .done());

    // when
    final long scopeInstanceKey = testClient.createWorkflowInstance(PROCESS_ID, "{'i': 0}");
    testClient.completeJobOfType("test", jobPayload);

    // then
    final long elementInstanceKey =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("task")
            .getFirst()
            .getKey();

    final Record<VariableRecordValue> initialVariabl =
        RecordingExporter.variableRecords(VariableIntent.CREATED).withName("i").getFirst();

    assertThat(
            RecordingExporter.variableRecords()
                .skipUntil(r -> r.getPosition() > initialVariabl.getPosition())
                .withScopeInstanceKey(elementInstanceKey)
                .limit(expectedActivtyVariables.size()))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getName(), v.getValue()))
        .hasSize(expectedActivtyVariables.size())
        .containsAll(expectedActivtyVariables);

    assertThat(
            RecordingExporter.variableRecords()
                .skipUntil(r -> r.getPosition() > initialVariabl.getPosition())
                .withScopeInstanceKey(scopeInstanceKey)
                .limit(expectedScopeVariables.size()))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getName(), v.getValue()))
        .hasSize(expectedScopeVariables.size())
        .containsAll(expectedScopeVariables);
  }

  private static Consumer<ZeebePayloadMappingBuilder<ServiceTaskBuilder>> mapping(
      Consumer<ZeebePayloadMappingBuilder<ServiceTaskBuilder>> mappingBuilder) {
    return mappingBuilder;
  }

  private static List<Tuple> activityVariables(Tuple... variables) {
    return Arrays.asList(variables);
  }

  private static List<Tuple> scopeVariables(Tuple... variables) {
    return Arrays.asList(variables);
  }
}
