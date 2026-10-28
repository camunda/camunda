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
import io.camunda.process.test.api.mock.VariablesTable;
import io.camunda.process.test.api.testCases.VariablesTableRow;
import io.camunda.process.test.api.testCases.instructions.MockJobWorkerCompleteJobInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MockJobWorkerCompleteJobInstructionHandler
    implements TestCaseInstructionHandler<MockJobWorkerCompleteJobInstruction> {

  @Override
  public void execute(
      final MockJobWorkerCompleteJobInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    validateExclusivity(instruction);

    if (instruction.getVariablesExpression().isPresent()) {
      final String expression = normalize(instruction.getVariablesExpression().get());
      context
          .mockJobWorker(instruction.getJobType())
          .thenComplete(jobVariables -> evaluate(camundaClient, expression, jobVariables));
    } else if (!instruction.getVariablesTable().isEmpty()) {
      context
          .mockJobWorker(instruction.getJobType())
          .thenComplete(toVariablesTable(instruction.getVariablesTable()));
    } else if (instruction.getUseExampleData()) {
      context.mockJobWorker(instruction.getJobType()).thenCompleteWithExampleData();
    } else {
      context.mockJobWorker(instruction.getJobType()).thenComplete(instruction.getVariables());
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
                  + "'useExampleData' to be set for job type '%s', but found: %s.",
              instruction.getJobType(), setFields));
    }
  }

  @Override
  public Class<MockJobWorkerCompleteJobInstruction> getInstructionType() {
    return MockJobWorkerCompleteJobInstruction.class;
  }
}
