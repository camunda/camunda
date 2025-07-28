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
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListeners;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeUserTask;
import java.util.Collection;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class TaskListenersValidator implements ModelElementValidator<ZeebeTaskListeners> {

  @Override
  public Class<ZeebeTaskListeners> getElementType() {
    return ZeebeTaskListeners.class;
  }

  @Override
  public void validate(
      final ZeebeTaskListeners element, final ValidationResultCollector validationResultCollector) {
    final Collection<ZeebeTaskListener> taskListeners = element.getTaskListeners();
    if (taskListeners == null || taskListeners.isEmpty()) {
      return;
    }

    if (taskListenersBelongToCamundaUserTask(element)) {
      return;
    }

    // add error
    final String errorMessage = "Task listeners are only allowed on Camunda user tasks.";
    validationResultCollector.addError(0, errorMessage);
  }

  /** Returns true if the task listeners belong to a Camunda user task */
  private static boolean taskListenersBelongToCamundaUserTask(final ZeebeTaskListeners element) {
    final Collection<ExtensionElements> extentionElements =
        element
            .getParentElement()
            .getParentElement()
            .getChildElementsByType(ExtensionElements.class);
    for (final ExtensionElements extentionElement : extentionElements) {
      for (final ModelElementInstance extentionElementElement : extentionElement.getElements()) {
        if (extentionElementElement instanceof ZeebeUserTask) {
          // This is a Zeebe UserTask, i.e. a Camunda user task
          return true;
        }
      }
    }
    return false;
  }
}
