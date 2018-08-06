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

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.Process;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResult;
import org.camunda.bpm.model.xml.validation.ValidationResultType;
import org.camunda.bpm.model.xml.validation.ValidationResults;
import org.junit.Test;

/** @author Daniel Meyer */
public class ValidateProcessTest {

  @Test
  public void validationFailsIfNoStartEventFound() {

    final List<ModelElementValidator<?>> validators = new ArrayList<ModelElementValidator<?>>();
    validators.add(new ProcessStartEventValidator());

    final BpmnModelInstance bpmnModelInstance = Bpmn.createProcess().done();

    final ValidationResults validationResults = bpmnModelInstance.validate(validators);

    assertThat(validationResults.hasErrors()).isTrue();

    final Map<ModelElementInstance, List<ValidationResult>> results =
        validationResults.getResults();
    assertThat(results.size()).isEqualTo(1);

    final Process process =
        bpmnModelInstance.getDefinitions().getChildElementsByType(Process.class).iterator().next();
    assertThat(results.containsKey(process)).isTrue();

    final List<ValidationResult> resultsForProcess = results.get(process);
    assertThat(resultsForProcess.size()).isEqualTo(1);

    final ValidationResult validationResult = resultsForProcess.get(0);
    assertThat(validationResult.getElement()).isEqualTo(process);
    assertThat(validationResult.getCode()).isEqualTo(10);
    assertThat(validationResult.getMessage())
        .isEqualTo("Process does not have exactly one start event. Got 0.");
    assertThat(validationResult.getType()).isEqualTo(ValidationResultType.ERROR);
  }
}
