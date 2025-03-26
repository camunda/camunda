/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.common;

import io.camunda.tasklist.store.DraftVariableStore;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.tasklist.template.DraftTaskVariableTemplate;
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
import org.springframework.beans.factory.annotation.Qualifier;
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

  @Autowired
  @Qualifier("tasklistProcessIndex")
  private ProcessIndex processIndex;

  @Autowired private FormIndex formIndex;

  @Autowired private FormStore formStore;

  @Autowired private TaskStore taskStore;

  @Autowired private VariableStore variableStore;

  @Autowired private DraftTaskVariableTemplate draftTaskVariableTemplate;

  @Autowired private DraftVariableStore draftVariableStore;

  public <T> List<T> createProcessDefinitionDeleteRequests(
      final String processDefinitionId,
      final BiFunction<String, String, T>
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
      final List<String> ids,
      final String indexName,
      final BiFunction<String, String, T> deleteRequestBuilder) {
    return ids.stream()
        .map(id -> deleteRequestBuilder.apply(indexName, id))
        .collect(Collectors.toList());
  }

  private <T> List<T> createDeleteRequestList(
      final Map<String, String> idsToIndex,
      final BiFunction<String, String, T> deleteRequestBuilder) {
    return idsToIndex.entrySet().stream()
        .map(entry -> deleteRequestBuilder.apply(entry.getValue(), entry.getKey()))
        .collect(Collectors.toList());
  }
}
