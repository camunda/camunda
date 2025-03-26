/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.store.DraftVariableStore;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.tasklist.template.DraftTaskVariableTemplate;
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
