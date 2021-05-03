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
package io.zeebe.model.bpmn.validation.zeebe;

import io.zeebe.model.bpmn.instance.Definitions;
import io.zeebe.model.bpmn.instance.Process;
import java.util.Collection;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class DefinitionsValidator implements ModelElementValidator<Definitions> {

  @Override
  public Class<Definitions> getElementType() {
    return Definitions.class;
  }

  @Override
  public void validate(
      final Definitions element, final ValidationResultCollector validationResultCollector) {
    final Collection<Process> processes = element.getChildElementsByType(Process.class);

    if (!processes.stream().anyMatch(p -> p.isExecutable())) {
      validationResultCollector.addError(0, "Must contain at least one executable process");
    }
  }
}
