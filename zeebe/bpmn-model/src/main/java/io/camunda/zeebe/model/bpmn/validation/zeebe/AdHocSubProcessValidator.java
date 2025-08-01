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
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAdHoc;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAdHocImplementationType;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.Collection;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public final class AdHocSubProcessValidator implements ModelElementValidator<AdHocSubProcess> {

  @Override
  public Class<AdHocSubProcess> getElementType() {
    return AdHocSubProcess.class;
  }

  @Override
  public void validate(
      final AdHocSubProcess adHocSubProcess,
      final ValidationResultCollector validationResultCollector) {
    IdentifiableBpmnElementValidator.validate(adHocSubProcess, validationResultCollector);
    validateTaskDefinition(adHocSubProcess, validationResultCollector);

    final Collection<FlowElement> flowElements = adHocSubProcess.getFlowElements();

    if (flowElements.isEmpty()) {
      validationResultCollector.addError(0, "Must have at least one activity.");
    }

    if (hasStartEvent(flowElements)) {
      validationResultCollector.addError(0, "Must not contain a start event.");
    }

    if (hasEndEvent(flowElements)) {
      validationResultCollector.addError(0, "Must not contain an end event.");
    }
  }

  private static void validateTaskDefinition(
      final AdHocSubProcess adHocSubProcess,
      final ValidationResultCollector validationResultCollector) {

    final ExtensionElements extensionElements = adHocSubProcess.getExtensionElements();
    if (extensionElements == null) {
      return;
    }

    final ZeebeAdHocImplementationType implementationType =
        extensionElements.getChildElementsByType(ZeebeAdHoc.class).stream()
            .findFirst()
            .map(ZeebeAdHoc::getImplementationType)
            .orElse(ZeebeAdHocImplementationType.BPMN);

    final Collection<ZeebeTaskDefinition> taskDefinitions =
        extensionElements.getChildElementsByType(ZeebeTaskDefinition.class);

    if (implementationType == ZeebeAdHocImplementationType.BPMN && !taskDefinitions.isEmpty()) {
      validationResultCollector.addError(
          0,
          String.format(
              "Must not have a zeebe:%s extension element with implementation type %s.",
              ZeebeConstants.ELEMENT_TASK_DEFINITION, ZeebeAdHocImplementationType.BPMN));
    }

    if (implementationType == ZeebeAdHocImplementationType.JOB_WORKER
        && taskDefinitions.size() != 1) {
      validationResultCollector.addError(
          0,
          String.format(
              "Must have exactly one zeebe:%s extension element with implementation type %s.",
              ZeebeConstants.ELEMENT_TASK_DEFINITION, ZeebeAdHocImplementationType.JOB_WORKER));
    }
  }

  private static boolean hasStartEvent(final Collection<FlowElement> flowElements) {
    return flowElements.stream().anyMatch(StartEvent.class::isInstance);
  }

  private static boolean hasEndEvent(final Collection<FlowElement> flowElements) {
    return flowElements.stream().anyMatch(EndEvent.class::isInstance);
  }
}
