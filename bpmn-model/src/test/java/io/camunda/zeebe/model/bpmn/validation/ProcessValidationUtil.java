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
package io.camunda.zeebe.model.bpmn.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.traversal.ModelWalker;
import io.camunda.zeebe.model.bpmn.validation.zeebe.ZeebeDesignTimeValidators;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.SoftAssertions;
import org.camunda.bpm.model.xml.validation.ValidationResult;
import org.camunda.bpm.model.xml.validation.ValidationResults;

public final class ProcessValidationUtil {

  public static void assertThatProcessIsValid(final BpmnModelInstance process) {

    validateSchema(process);

    final List<ValidationResult> validationResults = validate(process);
    assertThat(validationResults)
        .describedAs(
            "Expecting the process to be valid but it has the following violations:%n<%s>",
            format(validationResults))
        .isEmpty();
  }

  public static void assertThatProcessHasViolations(
      final BpmnModelInstance process, final ExpectedValidationResult... expectations) {

    validateSchema(process);

    final List<ValidationResult> validationResults = validate(process);
    final SoftAssertions softly = new SoftAssertions();
    softly
        .assertThat(expectations)
        .describedAs("All expectations should match a validation failure.")
        .allSatisfy(
            expectation ->
                assertThat(validationResults)
                    .describedAs("The expectation doesn't match to one of the validation failures.")
                    .anyMatch(expectation::matches));

    softly
        .assertThat(validationResults)
        .describedAs("All validation failures should match an expectation.")
        .allSatisfy(
            validationResult ->
                assertThat(expectations)
                    .describedAs(
                        "The validation failure%n<%s>%n doesn't match to one of the expectations",
                        ExpectedValidationResult.toString(validationResult))
                    .anyMatch(expectation -> expectation.matches(validationResult)));

    softly.assertAll();
  }

  private static void validateSchema(final BpmnModelInstance process) {
    assertThatNoException()
        .describedAs("Expecting the process to match the BPMN schema.")
        .isThrownBy(() -> Bpmn.validateModel(process));
  }

  private static List<ValidationResult> validate(final BpmnModelInstance model) {
    final ValidationVisitor visitor = new ValidationVisitor(ZeebeDesignTimeValidators.VALIDATORS);

    final ModelWalker walker = new ModelWalker(model);
    walker.walk(visitor);
    final ValidationResults result = visitor.getValidationResult();

    return result.getResults().values().stream()
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  private static String format(final List<ValidationResult> validationResults) {
    return validationResults.stream()
        .map(ExpectedValidationResult::toString)
        .collect(Collectors.joining(",\n"));
  }
}
