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
import io.zeebe.model.bpmn.impl.metadata.TaskDefinitionImpl;
import org.agrona.DirectBuffer;

public class TaskDefinitionValidator {
  public void validate(ErrorCollector validationResult, TaskDefinitionImpl taskDefinition) {
    final DirectBuffer taskType = taskDefinition.getTypeAsBuffer();
    if (taskType == null || taskType.capacity() == 0) {
      validationResult.addError(
          taskDefinition,
          String.format(
              "A task definition must contain a '%s' attribute which specifies the type of the task.",
              BpmnConstants.ZEEBE_ATTRIBUTE_TASK_TYPE));
    }

    final int retries = taskDefinition.getRetries();
    if (retries < 1) {
      validationResult.addError(taskDefinition, "The task retries must be greater than 0.");
    }
  }
}
