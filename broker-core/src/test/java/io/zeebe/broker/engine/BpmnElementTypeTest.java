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
package io.zeebe.broker.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.intent.MessageStartEventSubscriptionIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.Strings;
import io.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BpmnElementTypeTest {

  public static EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public static ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);

  @ClassRule public static RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private static PartitionTestClient testClient;

  @BeforeClass
  public static void init() {
    testClient = apiRule.partitionClient();
  }

  private static List<BpmnElementTypeScenario> scenarios =
      Arrays.asList(
          new BpmnElementTypeScenario("Process", BpmnElementType.PROCESS) {
            @Override
            String elementId() {
              return processId();
            }

            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId()).startEvent().done();
            }
          },
          new BpmnElementTypeScenario("Sub Process", BpmnElementType.SUB_PROCESS) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .subProcess(elementId())
                  .embeddedSubProcess()
                  .startEvent()
                  .subProcessDone()
                  .done();
            }
          },
          new BpmnElementTypeScenario("None Start Event", BpmnElementType.START_EVENT) {
            @Override
            public BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId()).startEvent(elementId()).done();
            }
          },
          new BpmnElementTypeScenario("Message Start Event", BpmnElementType.START_EVENT) {
            @Override
            public BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent(elementId())
                  .message(messageName())
                  .done();
            }

            @Override
            public void executeInstance() {
              // wait for message subscription for the start event to be opened
              RecordingExporter.messageStartEventSubscriptionRecords(
                      MessageStartEventSubscriptionIntent.OPENED)
                  .getFirst();

              testClient.publishMessage(messageName(), "");
            }
          },
          new BpmnElementTypeScenario("Timer Start Event", BpmnElementType.START_EVENT) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent(elementId())
                  .timerWithCycle("R1/PT0.01S")
                  .done();
            }

            @Override
            void executeInstance() {
              brokerRule.getClock().addTime(Duration.ofMinutes(1));
            }
          },
          new BpmnElementTypeScenario(
              "Intermediate Message Catch Event", BpmnElementType.INTERMEDIATE_CATCH_EVENT) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .intermediateCatchEvent(elementId())
                  .message(b -> b.name(messageName()).zeebeCorrelationKey("id"))
                  .done();
            }

            @Override
            void executeInstance() {
              super.executeInstance(Collections.singletonMap("id", "test"));
              testClient.publishMessage(messageName(), "test");
            }
          },
          new BpmnElementTypeScenario(
              "Intermediate Timer Catch Event", BpmnElementType.INTERMEDIATE_CATCH_EVENT) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .intermediateCatchEvent(elementId())
                  .timerWithDuration("PT0.01S")
                  .done();
            }
          },
          new BpmnElementTypeScenario(
              "Intermediate Catch Event After Event Based Gateway",
              BpmnElementType.INTERMEDIATE_CATCH_EVENT) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .eventBasedGateway()
                  .intermediateCatchEvent(elementId())
                  .timerWithDuration("PT0.01S")
                  .endEvent()
                  .moveToLastGateway()
                  .intermediateCatchEvent()
                  .timerWithDuration("PT1H")
                  .endEvent()
                  .done();
            }
          },
          new BpmnElementTypeScenario("Message Boundary Event", BpmnElementType.BOUNDARY_EVENT) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .serviceTask("task", b -> b.zeebeTaskType(taskType()))
                  .boundaryEvent(elementId())
                  .message(b -> b.name(messageName()).zeebeCorrelationKey("id"))
                  .endEvent()
                  .done();
            }

            @Override
            void executeInstance() {
              super.executeInstance(Collections.singletonMap("id", "test"));
              testClient.publishMessage(messageName(), "test");
            }
          },
          new BpmnElementTypeScenario("Timer Boundary Event", BpmnElementType.BOUNDARY_EVENT) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .serviceTask("task", b -> b.zeebeTaskType(taskType()))
                  .boundaryEvent(elementId())
                  .timerWithDuration("PT0.01S")
                  .endEvent()
                  .done();
            }
          },
          new BpmnElementTypeScenario("End Event", BpmnElementType.END_EVENT) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .endEvent(elementId())
                  .done();
            }
          },
          new BpmnElementTypeScenario("Service Task", BpmnElementType.SERVICE_TASK) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .serviceTask(elementId(), b -> b.zeebeTaskType(taskType()))
                  .done();
            }

            @Override
            void executeInstance() {
              super.executeInstance();
              testClient.completeJobOfType(taskType());
            }
          },
          new BpmnElementTypeScenario("Receive Task", BpmnElementType.RECEIVE_TASK) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .receiveTask(elementId())
                  .message(b -> b.name(messageName()).zeebeCorrelationKey("id"))
                  .done();
            }

            @Override
            void executeInstance() {
              executeInstance(Collections.singletonMap("id", "test"));
              testClient.publishMessage(messageName(), "test");
            }
          },
          new BpmnElementTypeScenario("Exclusive Gateway", BpmnElementType.EXCLUSIVE_GATEWAY) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .exclusiveGateway(elementId())
                  .defaultFlow()
                  .endEvent()
                  .done();
            }
          },
          new BpmnElementTypeScenario(
              "Sequence Flow After Exclusive Gateway", BpmnElementType.SEQUENCE_FLOW) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .exclusiveGateway()
                  .condition("5 > 1")
                  .sequenceFlowId(elementId())
                  .endEvent()
                  .moveToLastExclusiveGateway()
                  .defaultFlow()
                  .endEvent()
                  .done();
            }
          },
          new BpmnElementTypeScenario("Event Based Gateway", BpmnElementType.EVENT_BASED_GATEWAY) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .eventBasedGateway(elementId())
                  .intermediateCatchEvent()
                  .message(b -> b.name(messageName()).zeebeCorrelationKey("id"))
                  .moveToLastGateway()
                  .intermediateCatchEvent()
                  .timerWithDuration("PT0.01S")
                  .done();
            }

            @Override
            void executeInstance() {
              executeInstance(Collections.singletonMap("id", "test"));
              testClient.publishMessage(messageName(), "test");
            }
          },
          new BpmnElementTypeScenario("Parallel Gateway", BpmnElementType.PARALLEL_GATEWAY) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .parallelGateway(elementId())
                  .endEvent()
                  .done();
            }
          },
          new BpmnElementTypeScenario("Sequence Flow", BpmnElementType.SEQUENCE_FLOW) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .sequenceFlowId(elementId())
                  .endEvent()
                  .done();
            }
          });

  @Parameters(name = "{0}")
  public static Collection<Object[]> scenarios() {
    return scenarios.stream().map(s -> new Object[] {s}).collect(Collectors.toList());
  }

  private final BpmnElementTypeScenario scenario;

  public BpmnElementTypeTest(BpmnElementTypeScenario scenario) {
    this.scenario = scenario;
  }

  @Test
  public void test() {
    // given
    testClient.deploy(scenario.modelInstance());

    // when
    scenario.executeInstance();

    // then
    final List<Record<WorkflowInstanceRecordValue>> records =
        RecordingExporter.workflowInstanceRecords()
            .withBpmnProcessId(scenario.processId())
            .limitToWorkflowInstanceCompleted()
            .withElementId(scenario.elementId())
            .asList();

    assertThat(records)
        .extracting(r -> r.getValue().getBpmnElementType())
        .isNotEmpty()
        .containsOnly(scenario.elementType());
  }

  abstract static class BpmnElementTypeScenario {
    private final String name;
    private final BpmnElementType elementType;

    private final String processId = Strings.newRandomValidBpmnId();
    private final String elementId = Strings.newRandomValidBpmnId();
    private final String taskType = Strings.newRandomValidBpmnId();
    private final String messageName = Strings.newRandomValidBpmnId();

    BpmnElementTypeScenario(String name, BpmnElementType elementType) {
      this.name = name;
      this.elementType = elementType;
    }

    String name() {
      return name;
    }

    abstract BpmnModelInstance modelInstance();

    String processId() {
      return processId;
    }

    String elementId() {
      return elementId;
    }

    String taskType() {
      return taskType;
    }

    String messageName() {
      return messageName;
    }

    BpmnElementType elementType() {
      return elementType;
    }

    void executeInstance() {
      testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId()));
    }

    void executeInstance(Map<String, String> variables) {
      final String json =
          "{ "
              + variables.entrySet().stream()
                  .map(e -> String.format("\"%s\":\"%s\"", e.getKey(), e.getValue()))
                  .collect(Collectors.joining(","))
              + " }";
      testClient.createWorkflowInstance(
          r -> r.setBpmnProcessId(processId()).setVariables(MsgPackUtil.asMsgPack(json)));
    }

    @Override
    public String toString() {
      return name();
    }
  }
}
