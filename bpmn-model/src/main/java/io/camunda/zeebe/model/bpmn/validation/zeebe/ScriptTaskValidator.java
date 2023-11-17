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
package io.camunda.zeebe.model.bpmn.validation.zeebe;

import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.ScriptTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeScript;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.Collection;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public final class ScriptTaskValidator implements ModelElementValidator<ScriptTask> {

  @Override
  public Class<ScriptTask> getElementType() {
    return ScriptTask.class;
  }

  @Override
  public void validate(
      final ScriptTask element, final ValidationResultCollector validationResultCollector) {

    if (!hasExactlyOneExtension(element)) {
      validationResultCollector.addError(
          0,
          String.format(
              "Must have either one 'zeebe:%s' or one 'zeebe:%s' extension element",
              ZeebeConstants.ELEMENT_SCRIPT, ZeebeConstants.ELEMENT_TASK_DEFINITION));
    }
  }

  private boolean hasExactlyOneExtension(final ScriptTask element) {
    final ExtensionElements extensionElements = element.getExtensionElements();

    if (extensionElements == null) {
      return false;
    }

    final Collection<ZeebeScript> scriptExtensions =
        extensionElements.getChildElementsByType(ZeebeScript.class);
    final Collection<ZeebeTaskDefinition> taskDefinitionExtensions =
        extensionElements.getChildElementsByType(ZeebeTaskDefinition.class);

    return scriptExtensions.size() == 1 && taskDefinitionExtensions.isEmpty()
        || scriptExtensions.isEmpty() && taskDefinitionExtensions.size() == 1;
  }
}
