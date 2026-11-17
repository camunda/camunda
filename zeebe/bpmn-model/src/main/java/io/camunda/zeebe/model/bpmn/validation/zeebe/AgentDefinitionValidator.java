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

import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAgentDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAgentType;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

/**
 * Validates that a {@code zeebe:agentDefinition} marker's {@code agentType} is consistent with the
 * element it is attached to: {@code aiAgentSubProcess} is only valid on an ad-hoc sub-process,
 * {@code aiAgentTask} is only valid on a service task, while {@code external} is valid on either.
 */
public final class AgentDefinitionValidator implements ModelElementValidator<FlowElement> {

  @Override
  public Class<FlowElement> getElementType() {
    return FlowElement.class;
  }

  @Override
  public void validate(
      final FlowElement element, final ValidationResultCollector validationResultCollector) {
    final ExtensionElements extensionElements = element.getExtensionElements();
    if (extensionElements == null) {
      return;
    }

    extensionElements.getChildElementsByType(ZeebeAgentDefinition.class).stream()
        .findFirst()
        .ifPresent(
            agentDefinition ->
                validateAgentType(element, agentDefinition, validationResultCollector));
  }

  private static void validateAgentType(
      final FlowElement element,
      final ZeebeAgentDefinition agentDefinition,
      final ValidationResultCollector validationResultCollector) {
    final ZeebeAgentType agentType = agentDefinition.getAgentType();
    if (agentType == null) {
      // missing/empty agentType is already reported by the required-attribute check
      return;
    }

    switch (agentType) {
      case aiAgentSubProcess:
        if (!(element instanceof AdHocSubProcess)) {
          validationResultCollector.addError(
              0, "agentType 'aiAgentSubProcess' is only allowed on an ad-hoc sub-process.");
        }
        break;

      case aiAgentTask:
        if (!(element instanceof ServiceTask)) {
          validationResultCollector.addError(
              0, "agentType 'aiAgentTask' is only allowed on a service task.");
        }
        break;

      case external:
        if (!(element instanceof AdHocSubProcess) && !(element instanceof ServiceTask)) {
          validationResultCollector.addError(
              0,
              "agentType 'external' is only allowed on a service task or an ad-hoc sub-process.");
        }
        break;

      default:
        break;
    }
  }
}
