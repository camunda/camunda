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
package io.zeebe.model.bpmn.impl.transformation;

import io.zeebe.model.bpmn.impl.error.ErrorCollector;
import io.zeebe.model.bpmn.impl.error.InvalidModelException;
import io.zeebe.model.bpmn.impl.instance.DefinitionsImpl;
import io.zeebe.model.bpmn.impl.instance.ProcessImpl;
import io.zeebe.model.bpmn.instance.Workflow;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import java.util.*;
import org.agrona.DirectBuffer;

public class BpmnTransformer {
  private final ProcessTransformer processTransformer = new ProcessTransformer();

  public WorkflowDefinition transform(DefinitionsImpl definitions) {
    final ErrorCollector errorCollector = new ErrorCollector();
    final Map<DirectBuffer, Workflow> workflowsById = new HashMap<>();
    final List<ProcessImpl> processes = definitions.getProcesses();

    for (int p = 0; p < processes.size(); p++) {
      final ProcessImpl process = processes.get(p);
      processTransformer.transform(errorCollector, process);
      workflowsById.put(process.getBpmnProcessId(), process);
    }

    definitions.getWorkflowsById().putAll(workflowsById);

    reportExistingErrorsOrWarnings(errorCollector);

    return definitions;
  }

  public void reportExistingErrorsOrWarnings(ErrorCollector errorCollector) {
    if (errorCollector.hasErrors()) {
      throw new InvalidModelException(errorCollector.formatErrors());
    }
  }
}
