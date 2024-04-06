/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.zeebeimport.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.schema.indices.FormIndex;
import io.camunda.tasklist.schema.indices.ProcessIndex;
import io.camunda.tasklist.schema.templates.DraftTaskVariableTemplate;
import io.camunda.tasklist.store.DraftVariableStore;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.store.VariableStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessDefinitionDeletionProcessorTest {

  @InjectMocks private ProcessDefinitionDeletionProcessor processDefinitionDeletionProcessor;

  @Mock private ProcessIndex processIndex;

  @Mock private FormIndex formIndex;

  @Mock private FormStore formStore;

  @Mock private TaskStore taskStore;

  @Mock private VariableStore variableStore;

  @Mock private DraftTaskVariableTemplate draftTaskVariableTemplate;

  @Mock private DraftVariableStore draftVariableStore;

  @Test
  void createProcessDefinitionDeleteRequests() {
    final String processDefinitionId = "processId";
    final String formId = "formId";
    final String taskId1 = "taskId1";
    final String taskId2 = "taskId2";
    final String variableTaskId1 = "variableTaskId1";
    final String variableTaskId2 = "variableTaskId2";
    final String draftVariableId = "draftVariableId";
    final String taskIndexName = "task-index";
    final String variableIndexName = "task-variable-index";
    final String formIndexName = "form-index";
    final String processIndexName = "process-index";
    final String draftVariableIndexName = "draft-variable-index";

    when(formIndex.getFullQualifiedName()).thenReturn(formIndexName);
    when(processIndex.getFullQualifiedName()).thenReturn(processIndexName);
    when(draftTaskVariableTemplate.getFullQualifiedName()).thenReturn(draftVariableIndexName);
    when(formStore.getFormIdsByProcessDefinitionId(processDefinitionId))
        .thenReturn(List.of(formId));
    final Map<String, String> tasksMap = new LinkedHashMap<>();
    tasksMap.put(taskId1, taskIndexName);
    tasksMap.put(taskId2, taskIndexName + "_06-10-2023");
    when(taskStore.getTaskIdsWithIndexByProcessDefinitionId(processDefinitionId))
        .thenReturn(tasksMap);
    final Map<String, String> taskVariablesMap = new LinkedHashMap<>();
    taskVariablesMap.put(variableTaskId1, variableIndexName);
    taskVariablesMap.put(variableTaskId2, variableIndexName + "_06-10-2023");
    when(variableStore.getTaskVariablesIdsWithIndexByTaskIds(List.of(taskId1, taskId2)))
        .thenReturn(taskVariablesMap);
    when(draftVariableStore.getDraftVariablesIdsByTaskIds(List.of(taskId1, taskId2)))
        .thenReturn(List.of(draftVariableId));

    final List<Pair<String, String>> result =
        processDefinitionDeletionProcessor.createProcessDefinitionDeleteRequests(
            processDefinitionId, Pair::of);

    assertEquals(
        List.of(
            Pair.of(variableIndexName, variableTaskId1),
            Pair.of(variableIndexName + "_06-10-2023", variableTaskId2),
            Pair.of(draftVariableIndexName, draftVariableId),
            Pair.of(taskIndexName, taskId1),
            Pair.of(taskIndexName + "_06-10-2023", taskId2),
            Pair.of(formIndexName, formId),
            Pair.of(processIndexName, processDefinitionId)),
        result);
  }
}
