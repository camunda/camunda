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

import io.camunda.tasklist.schema.indices.FormIndex;
import io.camunda.tasklist.schema.indices.ProcessIndex;
import io.camunda.tasklist.schema.templates.DraftTaskVariableTemplate;
import io.camunda.tasklist.store.DraftVariableStore;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.store.VariableStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/*
  Creates, on Delete Process definition event, list of DeleteRequest required to delete process definition related objects in Tasklist
  Deletes process definition related objects:
  - Tasks on partition provided in Zeebe record
  - TaskVariables on partition provided in Zeebe record
  - Process entity
  - Embedded forms
*/
@Component
public class ProcessDefinitionDeletionProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessDefinitionDeletionProcessor.class);

  @Autowired private ProcessIndex processIndex;

  @Autowired private FormIndex formIndex;

  @Autowired private FormStore formStore;

  @Autowired private TaskStore taskStore;

  @Autowired private VariableStore variableStore;

  @Autowired private DraftTaskVariableTemplate draftTaskVariableTemplate;

  @Autowired private DraftVariableStore draftVariableStore;

  public <T> List<T> createProcessDefinitionDeleteRequests(
      String processDefinitionId,
      BiFunction<String, String, T>
          deleteRequestBuilder // (indexName, documentId) to DeleteRequest mapper
      ) {
    final Map<String, String> taskIdsToIndex =
        taskStore.getTaskIdsWithIndexByProcessDefinitionId(processDefinitionId);
    final List<String> taskIds = new LinkedList<>(taskIdsToIndex.keySet());
    final Map<String, String> taskVariablesIdsToIndex =
        taskIdsToIndex.isEmpty()
            ? Collections.emptyMap()
            : variableStore.getTaskVariablesIdsWithIndexByTaskIds(taskIds);
    final List<String> draftVariableIds = draftVariableStore.getDraftVariablesIdsByTaskIds(taskIds);
    final List<String> embeddedFormIds =
        formStore.getFormIdsByProcessDefinitionId(processDefinitionId);
    LOGGER.info(
        "Deleting process definition (id={}) related objects | {} taskVariables | {} draftVariables | {} tasks | {} embeddedForms",
        processDefinitionId,
        taskVariablesIdsToIndex.size(),
        draftVariableIds.size(),
        taskIdsToIndex.size(),
        embeddedFormIds.size());
    final List<T> result = new ArrayList<>();
    result.addAll(createDeleteRequestList(taskVariablesIdsToIndex, deleteRequestBuilder));
    result.addAll(
        createDeleteRequestList(
            draftVariableIds,
            draftTaskVariableTemplate.getFullQualifiedName(),
            deleteRequestBuilder));
    result.addAll(createDeleteRequestList(taskIdsToIndex, deleteRequestBuilder));
    result.addAll(
        createDeleteRequestList(
            embeddedFormIds, formIndex.getFullQualifiedName(), deleteRequestBuilder));
    result.addAll(
        createDeleteRequestList(
            List.of(processDefinitionId),
            processIndex.getFullQualifiedName(),
            deleteRequestBuilder));
    return result;
  }

  private <T> List<T> createDeleteRequestList(
      List<String> ids, String indexName, BiFunction<String, String, T> deleteRequestBuilder) {
    return ids.stream()
        .map(id -> deleteRequestBuilder.apply(indexName, id))
        .collect(Collectors.toList());
  }

  private <T> List<T> createDeleteRequestList(
      Map<String, String> idsToIndex, BiFunction<String, String, T> deleteRequestBuilder) {
    return idsToIndex.entrySet().stream()
        .map(entry -> deleteRequestBuilder.apply(entry.getValue(), entry.getKey()))
        .collect(Collectors.toList());
  }
}
