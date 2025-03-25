/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import static io.camunda.zeebe.engine.processing.deployment.model.validation.ExpectedValidationResult.expect;
import static org.junit.Assert.fail;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor.EvaluationContextLookup;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.ExpressionTransformer;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.ConditionExpression;
import io.camunda.zeebe.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAssignmentDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledElement;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeLoopCharacteristics;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeOutput;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebePriorityDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskHeaders;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskSchedule;
import io.camunda.zeebe.model.bpmn.traversal.ModelWalker;
import io.camunda.zeebe.model.bpmn.validation.ValidationVisitor;
import io.camunda.zeebe.protocol.Protocol;
import java.io.InputStream;
import java.time.InstantSource;
import java.util.ArrayList;
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

  private static final String INVALID_EXPRESSION = "a & b";
  private static final String INVALID_EXPRESSION_MESSAGE = "failed to parse expression 'a & b'";
  private static final String STATIC_EXPRESSION = "x";
  private static final String STATIC_EXPRESSION_MESSAGE =
      "Expected expression but found static value 'x'. An expression must start with '=' (e.g. '=x').";
  private static final String MISSING_EXPRESSION_MESSAGE = "Expected expression but not found.";
  private static final String MISSING_PATH_EXPRESSION_MESSAGE =
      "Expected path expression but not found.";
  private static final String INVALID_PATH_EXPRESSION = "a ? b";
  private static final String INVALID_PATH_EXPRESSION_MESSAGE =
      "Expected path expression 'a ? b' but doesn't match the pattern '[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*'.";
  private static final String RESERVED_TASK_HEADER_KEY =
      Protocol.RESERVED_HEADER_NAME_PREFIX + "reserved-header-key";
  private static final String RESERVED_TASK_HEADER_MESSAGE =
      "Attribute 'key' contains '%s', but header keys starting with '%s' are reserved for internal use.";
  public BpmnModelInstance modelInstance;

  @Parameter(0)
  public Object modelSource;

  @Parameter(1)
  public List<ExpectedValidationResult> expectedResults;

  @Parameters(name = "{index}: {1}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        // not a valid expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .exclusiveGateway()
            .sequenceFlowId("flow")
            .conditionExpression(INVALID_EXPRESSION)
            .endEvent()
            .done(),
        List.of(expect(ConditionExpression.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // static expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .exclusiveGateway()
            .sequenceFlowId("flow")
            .condition(STATIC_EXPRESSION)
            .endEvent()
            .done(),
        List.of(expect(ConditionExpression.class, STATIC_EXPRESSION_MESSAGE))
      },
      {
        // not a valid expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeInputExpression(INVALID_EXPRESSION, "foo"))
            .endEvent()
            .done(),
        List.of(expect(ZeebeInput.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // empty path expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeInputExpression("foo", ""))
            .endEvent()
            .done(),
        List.of(expect(ZeebeInput.class, MISSING_PATH_EXPRESSION_MESSAGE))
      },
      {
        // invalid target expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeInputExpression("foo", INVALID_PATH_EXPRESSION))
            .endEvent()
            .done(),
        List.of(expect(ZeebeInput.class, INVALID_PATH_EXPRESSION_MESSAGE))
      },
      { // not a valid expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeOutputExpression(INVALID_EXPRESSION, "foo"))
            .endEvent()
            .done(),
        List.of(expect(ZeebeOutput.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // static expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeOutput(STATIC_EXPRESSION, "bar"))
            .endEvent()
            .done(),
        List.of(expect(ZeebeOutput.class, STATIC_EXPRESSION_MESSAGE))
      },
      {
        // empty expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeOutput("", "bar"))
            .endEvent()
            .done(),
        List.of(expect(ZeebeOutput.class, MISSING_EXPRESSION_MESSAGE))
      },
      {
        // invalid target expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeOutputExpression("foo", INVALID_PATH_EXPRESSION))
            .endEvent()
            .done(),
        List.of(expect(ZeebeOutput.class, INVALID_PATH_EXPRESSION_MESSAGE))
      },
      {
        // empty path expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeOutputExpression("foo", ""))
            .endEvent()
            .done(),
        List.of(expect(ZeebeOutput.class, MISSING_PATH_EXPRESSION_MESSAGE))
      },
      {
        // name expression is invalid
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("catch")
            .message(b -> b.name("message").zeebeCorrelationKeyExpression(INVALID_EXPRESSION))
            .endEvent()
            .done(),
        List.of(expect(ZeebeSubscription.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // static expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("catch")
            .message(b -> b.name("message").zeebeCorrelationKey(STATIC_EXPRESSION))
            .endEvent()
            .done(),
        List.of(expect(ZeebeSubscription.class, STATIC_EXPRESSION_MESSAGE))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .receiveTask("catch")
            .message(b -> b.name("message").zeebeCorrelationKeyExpression(INVALID_EXPRESSION))
            .endEvent()
            .done(),
        List.of(expect(ZeebeSubscription.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // static expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .receiveTask("catch")
            .message(b -> b.name("message").zeebeCorrelationKey(STATIC_EXPRESSION))
            .endEvent()
            .done(),
        List.of(expect(ZeebeSubscription.class, STATIC_EXPRESSION_MESSAGE))
      },
      {
        // input collection expression is not supported
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t -> t.multiInstance(m -> m.zeebeInputCollectionExpression(INVALID_EXPRESSION)))
            .done(),
        List.of(expect(ZeebeLoopCharacteristics.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // input collection expression is static
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task", t -> t.multiInstance(m -> m.zeebeInputCollection(STATIC_EXPRESSION)))
            .done(),
        List.of(expect(ZeebeLoopCharacteristics.class, STATIC_EXPRESSION_MESSAGE))
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
                            m.zeebeInputCollectionExpression("x")
                                .zeebeOutputElementExpression(INVALID_EXPRESSION)))
            .done(),
        List.of(expect(ZeebeLoopCharacteristics.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // output element  expression is static
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.multiInstance(
                        m ->
                            m.zeebeInputCollectionExpression("x")
                                .zeebeOutputElement(STATIC_EXPRESSION)))
            .done(),
        List.of(expect(ZeebeLoopCharacteristics.class, STATIC_EXPRESSION_MESSAGE))
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
                            m.zeebeInputCollectionExpression("foo")
                                .zeebeOutputCollection("bar")
                                .zeebeOutputElementExpression(INVALID_EXPRESSION)))
            .done(),
        List.of(expect(ZeebeLoopCharacteristics.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // process id expression is not supported
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessIdExpression(INVALID_EXPRESSION))
            .done(),
        List.of(expect(ZeebeCalledElement.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        /* message on start event has expression that contains a variable reference
         * This must fail validation, because at the time the expression is evaluated,
         * there are no variables defined
         */
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .message(messageBuilder -> messageBuilder.nameExpression("variableReference"))
            .done(),
        List.of(
            expect(
                StartEvent.class,
                "Expected constant expression of type String for message name '=variableReference', but was NULL"))
      },
      {
        /* message on start event has expression that evaluates to something other than string
         * This must fail validation, because the message name must be a string
         */
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .message(messageBuilder -> messageBuilder.nameExpression("false"))
            .done(),
        List.of(
            expect(
                StartEvent.class,
                "Expected constant expression of type String for message name '=false', but was BOOLEAN"))
      },
      {
        /* invalid variable input mapping */
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task", task -> task.zeebeJobType("test").zeebeInputExpression("x", "null"))
            .done(),
        List.of(
            expect(
                ZeebeInput.class,
                "Expected path expression 'null' but is one of the reserved words (null, true, false, function, if, then, else, for, between, instance, of)."))
      },
      {
        /* invalid variable output mapping */
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task", task -> task.zeebeJobType("test").zeebeOutputExpression("x", "true"))
            .done(),
        List.of(
            expect(
                ZeebeOutput.class,
                "Expected path expression 'true' but is one of the reserved words (null, true, false, function, if, then, else, for, between, instance, of)."))
      },
      {
        /* invalid assignee expression */
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task", b -> b.zeebeAssigneeExpression(INVALID_EXPRESSION))
            .done(),
        List.of(expect(ZeebeAssignmentDefinition.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        /* invalid candidateGroups expression */
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task", b -> b.zeebeCandidateGroupsExpression(INVALID_EXPRESSION))
            .done(),
        List.of(expect(ZeebeAssignmentDefinition.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        /* invalid candidateUsers expression */
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task", b -> b.zeebeCandidateUsersExpression(INVALID_EXPRESSION))
            .done(),
        List.of(expect(ZeebeAssignmentDefinition.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        /* invalid candidateGroups static value */
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task", b -> b.zeebeCandidateGroups("1,,"))
            .done(),
        List.of(
            expect(
                ZeebeAssignmentDefinition.class,
                "Expected static value to be a list of comma-separated values, e.g. 'a,b,c', but found '1,,'"))
      },
      {
        /* invalid candidateUsers static value */
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task", b -> b.zeebeCandidateUsers("1,,"))
            .done(),
        List.of(
            expect(
                ZeebeAssignmentDefinition.class,
                "Expected static value to be a list of comma-separated values, e.g. 'a,b,c', but found '1,,'"))
      },
      {
        /* invalid dueDate expression */
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task", b -> b.zeebeDueDateExpression(INVALID_EXPRESSION))
            .done(),
        List.of(expect(ZeebeTaskSchedule.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        /* invalid dueDate static value */
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task", b -> b.zeebeDueDate("12345"))
            .done(),
        List.of(
            expect(
                ZeebeTaskSchedule.class,
                """
                Expected static value to be a valid DateTime String, e.g. \
                '2023-03-02T15:35+02:00', but found '12345'."""))
      },
      {
        /* invalid followUpDate expression */
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task", b -> b.zeebeFollowUpDateExpression(INVALID_EXPRESSION))
            .done(),
        List.of(expect(ZeebeTaskSchedule.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        /* invalid followUpDate static value */
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task", b -> b.zeebeFollowUpDate("12345"))
            .done(),
        List.of(
            expect(
                ZeebeTaskSchedule.class,
                """
                Expected static value to be a valid DateTime String, e.g. \
                '2023-03-02T15:35+02:00', but found '12345'."""))
      },
      {
        /* reserved header key */
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeTaskHeader(RESERVED_TASK_HEADER_KEY, STATIC_EXPRESSION)
            .done(),
        List.of(
            expect(
                ZeebeTaskHeaders.class,
                String.format(
                    RESERVED_TASK_HEADER_MESSAGE,
                    RESERVED_TASK_HEADER_KEY,
                    Protocol.RESERVED_HEADER_NAME_PREFIX)))
      },
      {
        /* invalid completion condition expression */
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.multiInstance(
                        m ->
                            m.completionCondition(
                                ExpressionTransformer.asFeelExpressionString(INVALID_EXPRESSION))))
            .done(),
        List.of(expect(MultiInstanceLoopCharacteristics.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        /* invalid priority expression */
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task", b -> b.zeebeTaskPriorityExpression(INVALID_EXPRESSION))
            .done(),
        List.of(expect(ZeebePriorityDefinition.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        /*invalid priority static value */
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task", b -> b.zeebeTaskPriority("abc"))
            .done(),
        List.of(
            expect(
                ZeebePriorityDefinition.class,
                "Expected static value to be a valid Number between 0 and 100, but found 'abc'"))
      },
      {
        /*whitespace priority static value */
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task", b -> b.zeebeTaskPriority(" "))
            .done(),
        List.of(
            expect(
                ZeebePriorityDefinition.class,
                "Expected static value to be a valid Number between 0 and 100, but found ' '"))
      },
      {
        /*out of range priority static value */
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task", b -> b.zeebeTaskPriority("120"))
            .done(),
        List.of(
            expect(
                ZeebePriorityDefinition.class,
                "Priority must be a number between 0 and 100, but was '120'"))
      },
      {
        /*decimal priority static value */
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task", b -> b.zeebeTaskPriority("33.3"))
            .done(),
        List.of(
            expect(
                ZeebePriorityDefinition.class,
                "Expected static value to be a valid Number between 0 and 100, but found '33.3'"))
      },
    };
  }

  private static ValidationResults validate(final BpmnModelInstance model) {
    final ModelWalker walker = new ModelWalker(model);
    final ExpressionLanguage expressionLanguage =
        ExpressionLanguageFactory.createExpressionLanguage(
            new ZeebeFeelEngineClock(InstantSource.system()));
    final EvaluationContextLookup emptyLookup = scopeKey -> name -> null;
    final var expressionProcessor = new ExpressionProcessor(expressionLanguage, emptyLookup);
    final ValidationVisitor visitor =
        new ValidationVisitor(
            ZeebeRuntimeValidators.getValidators(expressionLanguage, expressionProcessor));
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
