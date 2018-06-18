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
package io.zeebe.model.bpmn.impl.validation;

import io.zeebe.model.bpmn.impl.error.ErrorCollector;
import io.zeebe.model.bpmn.impl.error.InvalidModelException;
import io.zeebe.model.bpmn.impl.instance.DefinitionsImpl;
import io.zeebe.model.bpmn.impl.instance.ProcessImpl;
import java.util.List;
import java.util.stream.Collectors;

public class BpmnValidator {
  private final ProcessValidator processValidator = new ProcessValidator();

  public void validate(DefinitionsImpl definition) {
    final ErrorCollector validationResult = new ErrorCollector();

    final List<ProcessImpl> executableProcesses =
        definition
            .getProcesses()
            .stream()
            .filter(ProcessImpl::isExecutable)
            .collect(Collectors.toList());

    if (executableProcesses.isEmpty()) {
      validationResult.addError(
          definition, "BPMN model must contain at least one executable process.");
    }

    for (ProcessImpl executableProcess : executableProcesses) {
      processValidator.validate(validationResult, executableProcess);
    }

    reportExsitingErrorsOrWarnings(validationResult);
  }

  public void reportExsitingErrorsOrWarnings(ErrorCollector validationResult) {
    if (validationResult.hasErrors()) {
      throw new InvalidModelException(validationResult.formatErrors());
    }
  }
}
