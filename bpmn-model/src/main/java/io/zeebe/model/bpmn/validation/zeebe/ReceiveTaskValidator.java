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

import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.ReceiveTask;
import io.zeebe.model.bpmn.util.ModelUtil;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ReceiveTaskValidator implements ModelElementValidator<ReceiveTask> {

  @Override
  public Class<ReceiveTask> getElementType() {
    return ReceiveTask.class;
  }

  @Override
  public void validate(ReceiveTask element, ValidationResultCollector validationResultCollector) {
    final Message message = element.getMessage();
    if (message == null) {
      validationResultCollector.addError(0, "Must reference a message");
    }

    final boolean hasDuplicateMessageListener =
        ModelUtil.getActivityMessageBoundaryEvents(element).stream()
            .anyMatch(event -> event.getMessage().getName().equals(message.getName()));

    if (hasDuplicateMessageListener) {
      validationResultCollector.addError(
          0, "Cannot reference the same message name as a boundary event");
    }
  }
}
