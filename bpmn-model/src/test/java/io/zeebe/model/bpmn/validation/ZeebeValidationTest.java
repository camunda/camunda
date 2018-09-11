/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.model.bpmn.validation;

import static io.zeebe.model.bpmn.validation.ExpectedValidationResult.expect;
import static org.junit.Assert.fail;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.ReceiveTask;
import io.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutputBehavior;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import io.zeebe.model.bpmn.traversal.ModelWalker;
import io.zeebe.model.bpmn.validation.zeebe.ZeebeDesignTimeValidators;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.validation.ValidationResult;
import org.camunda.bpm.model.xml.validation.ValidationResults;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ZeebeValidationTest {

  public BpmnModelInstance modelInstance;

  @Parameter(0)
  public Object modelSource;

  @Parameter(1)
  public List<ExpectedValidationResult> expectedResults;

  @Parameters(name = "{index}: {1}")
  public static final Object[][] parameters() {
    return new Object[][] {
      {
        Bpmn.createExecutableProcess("process").done(),
        Arrays.asList(expect("process", "Must have exactly one start event"))
      },
      {
        Bpmn.createExecutableProcess("process").startEvent().serviceTask("task").endEvent().done(),
        Arrays.asList(
            expect("task", "Must have exactly one zeebe:taskDefinition extension element"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task")
            .zeebeTaskType("")
            .endEvent()
            .done(),
        Arrays.asList(expect(ZeebeTaskDefinition.class, "Task type must be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task")
            .zeebeTaskType("foo")
            .zeebeOutputBehavior(ZeebeOutputBehavior.none)
            .zeebeOutput("source", "target")
            .endEvent()
            .done(),
        Arrays.asList(
            expect(
                ZeebeIoMapping.class,
                "Output behavior 'none' cannot be used in combination without zeebe:output elements"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .exclusiveGateway("gateway")
            .sequenceFlowId("flow1")
            .endEvent()
            .moveToLastExclusiveGateway()
            .sequenceFlowId("flow2")
            .condition("condition")
            .endEvent()
            .done(),
        Arrays.asList(expect("flow1", "Must have a condition or be default flow"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .exclusiveGateway("gateway")
            .sequenceFlowId("flow")
            .condition("name", "$.foo")
            .defaultFlow()
            .endEvent()
            .done(),
        Arrays.asList(expect("gateway", "Default flow must not have a condition"))
      },
      {
        Bpmn.createExecutableProcess("process").startEvent().receiveTask("foo").endEvent().done(),
        Arrays.asList(expect(ReceiveTask.class, "Must reference a message"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .receiveTask("foo")
            .message("")
            .endEvent()
            .done(),
        Arrays.asList(
            expect(Message.class, "Name must be present and not empty"),
            expect(Message.class, "Must have exactly one zeebe:subscription extension element"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .receiveTask("foo")
            .message(m -> m.name("foo").zeebeCorrelationKey(""))
            .endEvent()
            .done(),
        Arrays.asList(
            expect(ZeebeSubscription.class, "zeebe:correlationKey must be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .receiveTask("foo")
            .message(m -> m.name("foo"))
            .endEvent()
            .done(),
        Arrays.asList(
            expect(Message.class, "Must have exactly one zeebe:subscription extension element"))
      },
      {"default-flow.bpmn", Arrays.asList(expect("gateway", "Default flow must start at gateway"))},
      {
        Bpmn.createExecutableProcess("process").startEvent().userTask("task").endEvent().done(),
        Arrays.asList(expect("task", "Elements of this type are not supported"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .message(b -> b.name("foo").zeebeCorrelationKey("correlationkey"))
            .endEvent()
            .done(),
        Arrays.asList(expect("start", "Must be a none start event"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent("end")
            .signalEventDefinition("foo")
            .id("eventDefinition")
            .done(),
        Arrays.asList(
            expect("end", "Must be a none end event"),
            expect("eventDefinition", "Event definition of this type is not supported"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("foo")
            .message("")
            .endEvent()
            .done(),
        Arrays.asList(
            expect(Message.class, "Name must be present and not empty"),
            expect(Message.class, "Must have exactly one zeebe:subscription extension element"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("catch")
            .timerWithCycle("some config")
            .endEvent()
            .done(),
        Arrays.asList(
            expect(TimerEventDefinition.class, "Event definition of this type is not supported"),
            expect(IntermediateCatchEvent.class, "Must have a message event definition"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .subProcess("subProcess")
            .embeddedSubProcess()
            .startEvent("subProcessStart")
            .message(b -> b.name("message").zeebeCorrelationKey("correlationKey"))
            .endEvent()
            .subProcessDone()
            .endEvent()
            .done(),
        Arrays.asList(expect("subProcessStart", "Must be a none start event"))
      },
      {
        "no-start-event-sub-process.bpmn",
        Arrays.asList(expect("subProcess", "Must have exactly one start event"))
      }
    };
  }

  private static ValidationResults validate(BpmnModelInstance model) {
    final ModelWalker walker = new ModelWalker(model);
    final ValidationVisitor visitor = new ValidationVisitor(ZeebeDesignTimeValidators.VALIDATORS);
    walker.walk(visitor);

    return visitor.getValidationResult();
  }

  @Before
  public void prepareModel() {
    if (modelSource instanceof BpmnModelInstance) {
      modelInstance = (BpmnModelInstance) modelSource;
    } else if (modelSource instanceof String) {
      final InputStream modelStream =
          ZeebeValidationTest.class.getResourceAsStream((String) modelSource);
      modelInstance = Bpmn.readModelFromStream(modelStream);
    } else {
      throw new RuntimeException("Cannot convert parameter to bpmn model");
    }
  }

  @Test
  public void validateModel() {
    // when
    final ValidationResults results = validate(modelInstance);

    Bpmn.validateModel(modelInstance);

    // then
    final List<ExpectedValidationResult> unmatchedExpectations = new ArrayList<>(expectedResults);
    final List<ValidationResult> unmatchedResults =
        results
            .getResults()
            .values()
            .stream()
            .flatMap(l -> l.stream())
            .collect(Collectors.toList());

    match(unmatchedResults, unmatchedExpectations);

    if (!unmatchedResults.isEmpty() || !unmatchedExpectations.isEmpty()) {
      failWith(unmatchedExpectations, unmatchedResults);
    }
  }

  private void match(
      final List<ValidationResult> unmatchedResults,
      final List<ExpectedValidationResult> unmatchedExpectations) {
    final Iterator<ExpectedValidationResult> expectationIt = unmatchedExpectations.iterator();

    outerLoop:
    while (expectationIt.hasNext()) {
      final ExpectedValidationResult currentExpectation = expectationIt.next();
      final Iterator<ValidationResult> resultsIt = unmatchedResults.iterator();

      while (resultsIt.hasNext()) {
        final ValidationResult currentResult = resultsIt.next();
        if (currentExpectation.matches(currentResult)) {
          expectationIt.remove();
          resultsIt.remove();
          continue outerLoop;
        }
      }
    }
  }

  private void failWith(
      final List<ExpectedValidationResult> unmatchedExpectations,
      final List<ValidationResult> unmatchedResults) {
    final StringBuilder sb = new StringBuilder();
    sb.append("Not all expecations were matched by results (or vice versa)\n\n");
    describeUnmatchedExpectations(sb, unmatchedExpectations);
    sb.append("\n");
    describeUnmatchedResults(sb, unmatchedResults);
    fail(sb.toString());
  }

  private static void describeUnmatchedResults(StringBuilder sb, List<ValidationResult> results) {
    sb.append("Unmatched results:\n");
    results.forEach(
        e -> {
          sb.append(ExpectedValidationResult.toString(e));
          sb.append("\n");
        });
  }

  private static void describeUnmatchedExpectations(
      StringBuilder sb, List<ExpectedValidationResult> expectations) {
    sb.append("Unmatched expectations:\n");
    expectations.forEach(
        e -> {
          sb.append(e);
          sb.append("\n");
        });
  }
}
