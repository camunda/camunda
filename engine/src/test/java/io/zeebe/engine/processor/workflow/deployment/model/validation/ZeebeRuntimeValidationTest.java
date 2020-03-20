/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.validation;

import static io.zeebe.engine.processor.workflow.deployment.model.validation.ExpectedValidationResult.expect;
import static org.junit.Assert.fail;

import io.zeebe.el.ExpressionLanguage;
import io.zeebe.el.ExpressionLanguageFactory;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.ConditionExpression;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeCalledElement;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeLoopCharacteristics;
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
public final class ZeebeRuntimeValidationTest {

  private static final String INVALID_PATH_QUERY = "$.x";

  private static final String INVALID_PATH_QUERY_MESSAGE =
      "JSON path query is invalid: Unexpected json-path token ROOT_OBJECT";

  private static final String INVALID_EXPRESSION = "?!";

  private static final String INVALID_EXPRESSION_MESSAGE =
      "failed to parse expression '?!': [1.2] failure: end of input expected\n"
          + "\n"
          + "?!\n"
          + " ^";

  public BpmnModelInstance modelInstance;

  @Parameter(0)
  public Object modelSource;

  @Parameter(1)
  public List<ExpectedValidationResult> expectedResults;

  @Parameters(name = "{index}: {1}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        // not a JSON path condition
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .exclusiveGateway()
            .sequenceFlowId("flow")
            .condition(INVALID_EXPRESSION)
            .endEvent()
            .done(),
        Arrays.asList(expect(ConditionExpression.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // not a json path expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeInput(INVALID_PATH_QUERY, "foo"))
            .endEvent()
            .done(),
        Arrays.asList(expect(ZeebeInput.class, INVALID_PATH_QUERY_MESSAGE))
      },
      { // not a json path expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeOutput(INVALID_PATH_QUERY, "foo"))
            .endEvent()
            .done(),
        Arrays.asList(expect(ZeebeOutput.class, INVALID_PATH_QUERY_MESSAGE))
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
            .message(b -> b.name("message").zeebeCorrelationKey(INVALID_EXPRESSION))
            .endEvent()
            .done(),
        Arrays.asList(expect(ZeebeSubscription.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // correlation key expression is not supported
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .receiveTask("catch")
            .message(b -> b.name("message").zeebeCorrelationKey(INVALID_EXPRESSION))
            .endEvent()
            .done(),
        Arrays.asList(expect(ZeebeSubscription.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // input collection expression is not supported
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task", t -> t.multiInstance(m -> m.zeebeInputCollection(INVALID_PATH_QUERY)))
            .done(),
        Arrays.asList(expect(ZeebeLoopCharacteristics.class, INVALID_PATH_QUERY_MESSAGE))
      },
      {
        // output element expression is not supported
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.multiInstance(
                        m ->
                            m.zeebeInputCollection("foo")
                                .zeebeOutputCollection("bar")
                                .zeebeOutputElement(INVALID_PATH_QUERY)))
            .done(),
        Arrays.asList(expect(ZeebeLoopCharacteristics.class, INVALID_PATH_QUERY_MESSAGE))
      },
      {
        // process id expression is not supported
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessIdExpression(INVALID_EXPRESSION))
            .done(),
        Arrays.asList(expect(ZeebeCalledElement.class, INVALID_EXPRESSION_MESSAGE))
      },
    };
  }

  private static ValidationResults validate(final BpmnModelInstance model) {
    final ModelWalker walker = new ModelWalker(model);
    final ExpressionLanguage expressionLanguage =
        ExpressionLanguageFactory.createExpressionLanguage();
    final ValidationVisitor visitor =
        new ValidationVisitor(ZeebeRuntimeValidators.getValidators(expressionLanguage));
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

  private static void describeUnmatchedResults(
      final StringBuilder sb, final List<ValidationResult> results) {
    sb.append("Unmatched results:\n");
    results.forEach(
        e -> {
          sb.append(ExpectedValidationResult.toString(e));
          sb.append("\n");
        });
  }

  private static void describeUnmatchedExpectations(
      final StringBuilder sb, final List<ExpectedValidationResult> expectations) {
    sb.append("Unmatched expectations:\n");
    expectations.forEach(
        e -> {
          sb.append(e);
          sb.append("\n");
        });
  }
}
