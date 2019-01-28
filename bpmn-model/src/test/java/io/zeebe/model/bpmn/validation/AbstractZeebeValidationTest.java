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

import static org.junit.Assert.fail;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.traversal.ModelWalker;
import io.zeebe.model.bpmn.validation.zeebe.ZeebeDesignTimeValidators;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
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

@RunWith(Parameterized.class)
public abstract class AbstractZeebeValidationTest {

  public BpmnModelInstance modelInstance;

  @Parameter(0)
  public Object modelSource;

  @Parameter(1)
  public List<ExpectedValidationResult> expectedResults;

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
          AbstractZeebeValidationTest.class.getResourceAsStream((String) modelSource);
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
            .flatMap(Collection::stream)
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
    sb.append("Not all expectations were matched by results (or vice versa)\n\n");
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
