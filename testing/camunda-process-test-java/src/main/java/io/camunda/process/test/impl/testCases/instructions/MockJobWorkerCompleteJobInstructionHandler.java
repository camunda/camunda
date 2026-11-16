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
package io.camunda.process.test.impl.testCases.instructions;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder;
import io.camunda.process.test.api.mock.VariablesTable;
import io.camunda.process.test.api.testCases.VariablesTableRow;
import io.camunda.process.test.api.testCases.instructions.MockJobWorkerCompleteJobInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;
import io.camunda.process.test.impl.testCases.TestRunContext;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MockJobWorkerCompleteJobInstructionHandler
    implements TestCaseInstructionHandler<MockJobWorkerCompleteJobInstruction> {

  private final TestRunContext runContext;

  public MockJobWorkerCompleteJobInstructionHandler(final TestRunContext runContext) {
    this.runContext = runContext;
  }

  @Override
  public void execute(
      final MockJobWorkerCompleteJobInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    validateExclusivity(instruction);

    // Validate before creating the mock worker, so a blank expression fails right away instead of
    // on the first activated job.
    final String expression =
        instruction
            .getVariablesExpression()
            .map(MockJobWorkerCompleteJobInstructionHandler::normalize)
            .orElse(null);
    if (expression != null && expression.substring(1).trim().isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "Expected 'variablesExpression' to be a non-blank FEEL expression for mock '%s', "
                  + "but it was blank.",
              describeTarget(instruction)));
    }

    final JobWorkerMockBuilder mockBuilder;
    if (instruction.getCalledProcessId().isPresent()) {
      // stub jobs are never reserved, so a token would only get their commands rejected
      mockBuilder = context.mockCallActivity(instruction.getCalledProcessId().get());
    } else {
      mockBuilder = context.mockJobWorker(instruction.getJobType().get());
      instruction
          .getReservationToken()
          .map(runContext::resolveReservationToken)
          .ifPresent(mockBuilder::withReservationToken);
    }

    if (expression != null) {
      // the runner's client closes when the test case run returns, but the mock worker keeps
      // running, so the callback needs a client that is managed by the runtime
      final CamundaClient evaluationClient = context.createClient();
      mockBuilder.thenComplete(
          jobVariables -> evaluate(evaluationClient, expression, jobVariables));
    } else if (!instruction.getVariablesTable().isEmpty()) {
      mockBuilder.thenComplete(toVariablesTable(instruction.getVariablesTable()));
    } else if (instruction.getUseExampleData()) {
      mockBuilder.thenCompleteWithExampleData();
    } else {
      mockBuilder.thenComplete(instruction.getVariables());
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> evaluate(
      final CamundaClient camundaClient,
      final String expression,
      final Map<String, Object> jobVariables) {

    final Object result =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression(expression)
            .variables(jobVariables)
            .send()
            .join()
            .getResult();

    if (!(result instanceof Map)) {
      throw new IllegalStateException(
          String.format(
              "Expected the FEEL expression '%s' to evaluate to a JSON object, but it evaluated "
                  + "to '%s'.",
              expression, result));
    }

    return (Map<String, Object>) result;
  }

  private VariablesTable toVariablesTable(final List<VariablesTableRow> tableRows) {
    final Iterator<VariablesTableRow> rows = tableRows.iterator();
    final VariablesTableRow firstRow = rows.next();

    VariablesTable.Builder builder =
        VariablesTable.when(firstRow.getWhen()).thenReturn(firstRow.getVariables());

    while (rows.hasNext()) {
      final VariablesTableRow row = rows.next();
      builder = builder.when(row.getWhen()).thenReturn(row.getVariables());
    }

    return builder;
  }

  private static String normalize(final String expression) {
    final String trimmedExpression = expression.trim();
    return trimmedExpression.startsWith("=") ? trimmedExpression : "=" + trimmedExpression;
  }

  private static String describeTarget(final MockJobWorkerCompleteJobInstruction instruction) {
    return instruction
        .getJobType()
        .orElseGet(() -> instruction.getCalledProcessId().orElse("<unknown>"));
  }

  private static void validateExclusivity(final MockJobWorkerCompleteJobInstruction instruction) {
    final boolean hasVariables = !instruction.getVariables().isEmpty();
    final boolean hasExpression = instruction.getVariablesExpression().isPresent();
    final boolean hasTable = !instruction.getVariablesTable().isEmpty();
    final boolean useExampleData = instruction.getUseExampleData();

    if (!hasExpression && !hasTable) {
      // the legacy combination of 'variables' and 'useExampleData' stays lenient
      return;
    }

    final List<String> setFields = new ArrayList<>();
    if (hasVariables) {
      setFields.add("variables");
    }
    if (hasExpression) {
      setFields.add("variablesExpression");
    }
    if (hasTable) {
      setFields.add("variablesTable");
    }
    if (useExampleData) {
      setFields.add("useExampleData");
    }

    if (setFields.size() > 1) {
      throw new IllegalArgumentException(
          String.format(
              "Expected only one of 'variables', 'variablesExpression', 'variablesTable' or "
                  + "'useExampleData' to be set for mock '%s', but found: %s.",
              describeTarget(instruction), setFields));
    }
  }

  @Override
  public Class<MockJobWorkerCompleteJobInstruction> getInstructionType() {
    return MockJobWorkerCompleteJobInstruction.class;
  }
}
