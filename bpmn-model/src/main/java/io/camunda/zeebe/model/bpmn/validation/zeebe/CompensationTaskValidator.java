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

import io.camunda.zeebe.model.bpmn.impl.instance.ManualTaskImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ScriptTaskImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.SendTaskImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ServiceTaskImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.TaskImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.UserTaskImpl;
import io.camunda.zeebe.model.bpmn.instance.Task;
import java.util.Arrays;
import java.util.List;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class CompensationTaskValidator implements ModelElementValidator<Task> {

  private static final List<Class<? extends Task>> SUPPORTED_TASK =
      Arrays.asList(
          ServiceTaskImpl.class,
          UserTaskImpl.class,
          SendTaskImpl.class,
          ScriptTaskImpl.class,
          ManualTaskImpl.class,
          TaskImpl.class);

  @Override
  public Class<Task> getElementType() {
    return Task.class;
  }

  @Override
  public void validate(
      final Task element, final ValidationResultCollector validationResultCollector) {

    final String isForCompensation = element.getAttributeValue("isForCompensation");
    if (Boolean.parseBoolean(isForCompensation)) {
      validateCompensationTask(element, validationResultCollector);

      if (!element.getIncoming().isEmpty()) {
        validationResultCollector.addError(
            0, "A compensation handler should have no incoming sequence flows");
      }

      if (!element.getOutgoing().isEmpty()) {
        validationResultCollector.addError(
            0, "A compensation handler should have no outgoing sequence flows");
      }

      if (element.getBoundaryEvents().count() > 0) {
        validationResultCollector.addError(
            0, "A compensation handler should have no boundary events");
      }
    }
  }

  private void validateCompensationTask(
      final Task element, final ValidationResultCollector validationResultCollector) {
    if (SUPPORTED_TASK.stream().noneMatch(type -> type.equals(element.getClass()))) {
      validationResultCollector.addError(
          0,
          "Compensation task must be one of: service task, user task, send task, script task, manual task, or undefined task.");
    }
  }
}
