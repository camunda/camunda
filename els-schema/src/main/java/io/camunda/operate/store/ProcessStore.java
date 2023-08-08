/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store;

import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ProcessStore {


  // General methods -> TODO: refactor to upper interface?
  Optional<Long> getDistinctCountFor(final String fieldName);

  // ProcessStore
  ProcessEntity getProcessByKey(final Long processDefinitionKey);

  String getDiagramByKey(final Long processDefinitionKey);

  Map<String, List<ProcessEntity>> getProcessesGrouped(@Nullable Set<String> allowedBPMNprocessIds);

  Map<Long, ProcessEntity> getProcessIdsToProcesses();

  Map<Long, ProcessEntity> getProcessesIdsToProcessesWithFields(@Nullable Set<String> allowedBPMNIds, int maxSize, String... fields);

  /// Process instance methods
  ProcessInstanceForListViewEntity getProcessInstanceListViewByKey(Long processInstanceKey);

  Map<String, Long> getCoreStatistics(@Nullable Set<String> allowedBPMNids);

  String getProcessInstanceTreePathById(final String processInstanceId);

  List<Map<String, String>> createCallHierarchyFor(List<String> processInstanceIds, final String currentProcessInstanceId);

  long deleteDocument(final String indexName, final String idField, String id) throws IOException;

  void deleteProcessInstanceFromTreePath(String processInstanceKey);
}
