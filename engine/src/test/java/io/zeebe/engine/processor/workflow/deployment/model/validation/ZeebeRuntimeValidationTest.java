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
package io.zeebe.engine.processor.workflow.deployment.model.validation;

import static io.zeebe.engine.processor.workflow.deployment.model.validation.ExpectedValidationResult.expect;
import static org.junit.Assert.fail;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.ConditionExpression;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutput;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
import io.zeebe.model.bpmn.traversal.ModelWalker;
import io.zeebe.model.bpmn.validation.ValidationVisitor;
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
public class ZeebeRuntimeValidationTest {

  public BpmnModelInstance modelInstance;

  @Parameter(0)
  public Object modelSource;

  @Parameter(1)
  public List<ExpectedValidationResult> expectedResults;

  @Parameters(name = "{index}: {1}")
  public static final Object[][] parameters() {
    return new Object[][] {
      {
        // not a JSON path condition
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .exclusiveGateway()
            .sequenceFlowId("flow")
            .condition("foo")
            .endEvent()
            .done(),
        Arrays.asList(
            expect(
                ConditionExpression.class,
                "Condition expression is invalid: [1.4] failure: expected comparison operator ('==', '!=', '<', '<=', '>', '>=')\n"
                    + "\n"
                    + "foo\n"
                    + "   ^"))
      },
      {
        // not a json path expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeInput("$.foo", "foo"))
            .endEvent()
            .done(),
        Arrays.asList(
            expect(
                ZeebeInput.class,
                "JSON path query is invalid: Unexpected json-path token ROOT_OBJECT"))
      },
      { // not a json path expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeOutput("$.foo", "foo"))
            .endEvent()
            .done(),
        Arrays.asList(
            expect(
                ZeebeOutput.class,
                "JSON path query is invalid: Unexpected json-path token ROOT_OBJECT"))
      },
      {
        // input source expression is not supported
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeInput("foo[1]", "foo"))
            .endEvent()
            .done(),
        Arrays.asList(
            expect(
                ZeebeInput.class,
                "JSON path query is invalid: Unexpected json-path token SUBSCRIPT_OPERATOR_BEGIN"))
      },
      {
        // output target expression is not supported
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeInput("foo", "a[1]"))
            .endEvent()
            .done(),
        Arrays.asList(
            expect(
                ZeebeInput.class,
                "JSON path query is invalid: Unexpected json-path token SUBSCRIPT_OPERATOR_BEGIN"))
      },
      {
        // correlation key expression is not supported
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("catch")
            .message(b -> b.name("message").zeebeCorrelationKey("$.foo"))
            .endEvent()
            .done(),
        Arrays.asList(
            expect(
                ZeebeSubscription.class,
                "JSON path query is invalid: Unexpected json-path token ROOT_OBJECT"))
      },
      {
        // correlation key expression is not supported
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .receiveTask("catch")
            .message(b -> b.name("message").zeebeCorrelationKey("$.foo"))
            .endEvent()
            .done(),
        Arrays.asList(
            expect(
                ZeebeSubscription.class,
                "JSON path query is invalid: Unexpected json-path token ROOT_OBJECT"))
      },
      {
        // correlation key expression is empty
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .receiveTask("catch")
            .message(b -> b.name("message").zeebeCorrelationKey(null))
            .endEvent()
            .done(),
        Arrays.asList(expect(ZeebeSubscription.class, "JSON path query is empty"))
      },
    };
  }

  private static ValidationResults validate(BpmnModelInstance model) {
    final ModelWalker walker = new ModelWalker(model);
    final ValidationVisitor visitor = new ValidationVisitor(ZeebeRuntimeValidators.VALIDATORS);
    walker.walk(visitor);

    return visitor.getValidationResult();
  }

  @Before
  public void prepareModel() {
    if (modelSource instanceof BpmnModelInstance) {
      modelInstance = (BpmnModelInstance) modelSource;
    } else if (modelSource instanceof String) {
      final InputStream modelStream =
          ZeebeRuntimeValidationTest.class.getResourceAsStream((String) modelSource);
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
        results.getResults().values().stream()
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
