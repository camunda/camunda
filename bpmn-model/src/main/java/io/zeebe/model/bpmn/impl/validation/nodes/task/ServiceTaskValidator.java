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
package io.zeebe.model.bpmn.impl.validation.nodes.task;

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.impl.error.ErrorCollector;
import io.zeebe.model.bpmn.impl.instance.ServiceTaskImpl;
import io.zeebe.model.bpmn.impl.metadata.InputOutputMappingImpl;
import io.zeebe.model.bpmn.impl.metadata.TaskDefinitionImpl;
import io.zeebe.model.bpmn.impl.metadata.TaskHeadersImpl;

public class ServiceTaskValidator {
  private final TaskDefinitionValidator taskDefinitionValidator = new TaskDefinitionValidator();
  private final TaskHeadersValidator taskHeadersValidator = new TaskHeadersValidator();
  private final InputOutputMappingValidator inputOutputMappingValidator =
      new InputOutputMappingValidator();

  public void validate(ErrorCollector validationResult, ServiceTaskImpl serviceTask) {
    final TaskDefinitionImpl taskDefinition = serviceTask.getTaskDefinitionImpl();
    if (taskDefinition == null) {
      validationResult.addError(
          serviceTask,
          String.format(
              "A service task must contain a '%s' extension element.",
              BpmnConstants.ZEEBE_ELEMENT_TASK_DEFINITION));
    } else {
      taskDefinitionValidator.validate(validationResult, taskDefinition);
    }

    final TaskHeadersImpl taskHeaders = serviceTask.getTaskHeaders();
    if (taskHeaders != null) {
      taskHeadersValidator.validate(validationResult, taskHeaders);
    }

    final InputOutputMappingImpl inputOutputMapping = serviceTask.getInputOutputMapping();
    if (inputOutputMapping != null) {
      inputOutputMappingValidator.validate(validationResult, inputOutputMapping);
    }
  }
}
