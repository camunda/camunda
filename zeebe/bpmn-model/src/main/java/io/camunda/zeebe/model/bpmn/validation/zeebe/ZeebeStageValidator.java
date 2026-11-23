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

import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCase;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeStage;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ZeebeStageValidator implements ModelElementValidator<ZeebeStage> {

  @Override
  public Class<ZeebeStage> getElementType() {
    return ZeebeStage.class;
  }

  @Override
  public void validate(
      final ZeebeStage element, final ValidationResultCollector validationResultCollector) {
    final ModelElementInstance owner = getOwner(element);
    if (!isPlacedOnSubProcess(element)) {
      validationResultCollector.addError(
          0,
          String.format(
              "The stage marker 'zeebe:stage' is only allowed on a sub-process, but was found on '%s'",
              owner.getElementType().getTypeName()));
      return;
    }

    final ModelElementInstance subProcessParent = owner.getParentElement();
    if (!isTopLevel((SubProcess) owner)) {
      validationResultCollector.addError(
          0,
          String.format(
              "The stage marker 'zeebe:stage' is only allowed on a top-level sub-process, but was found on a sub-process nested in '%s'",
              subProcessParent.getElementType().getTypeName()));
      return;
    }

    if (!isMarkedAsCase((Process) subProcessParent)) {
      validationResultCollector.addError(
          0,
          "The stage marker 'zeebe:stage' is only allowed in a process that is marked as a case with 'zeebe:case'");
    }
  }

  /** The element the marker applies to: the owner of the extension elements it sits in. */
  private static ModelElementInstance getOwner(final ZeebeStage stage) {
    final ModelElementInstance parent = stage.getParentElement();
    return parent instanceof ExtensionElements ? parent.getParentElement() : parent;
  }

  private static boolean isPlacedOnSubProcess(final ZeebeStage stage) {
    return stage.getParentElement() instanceof ExtensionElements
        && getOwner(stage) instanceof SubProcess;
  }

  private static boolean isTopLevel(final SubProcess subProcess) {
    return subProcess.getParentElement() instanceof Process;
  }

  private static boolean isMarkedAsCase(final Process process) {
    // not getSingleExtensionElement: it throws when the process has duplicate case markers, which
    // are reported by their own validator
    final ExtensionElements extensionElements = process.getExtensionElements();
    return extensionElements != null
        && extensionElements.getElementsQuery().filterByType(ZeebeCase.class).count() > 0;
  }
}
