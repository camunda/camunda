/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store;

import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import java.io.IOException;
import java.util.*;
import org.springframework.lang.Nullable;

public interface ProcessStore {

  // General methods -> TODO: refactor to upper interface?
  Optional<Long> getDistinctCountFor(final String fieldName);

  void refreshIndices(String... indices);

  // ProcessStore
  ProcessEntity getProcessByKey(final Long processDefinitionKey);

  String getDiagramByKey(final Long processDefinitionKey);

  Map<ProcessKey, List<ProcessEntity>> getProcessesGrouped(
      String tenantId, @Nullable Set<String> allowedBPMNprocessIds);

  Map<Long, ProcessEntity> getProcessesIdsToProcessesWithFields(
      @Nullable Set<String> allowedBPMNIds, int maxSize, String... fields);

  long deleteProcessDefinitionsByKeys(Long... processDefinitionKeys);

  /// Process instance methods
  ProcessInstanceForListViewEntity getProcessInstanceListViewByKey(Long processInstanceKey);

  Map<String, Long> getCoreStatistics(@Nullable Set<String> allowedBPMNids);

  String getProcessInstanceTreePathById(final String processInstanceId);

  List<Map<String, String>> createCallHierarchyFor(
      List<String> processInstanceIds, final String currentProcessInstanceId);

  long deleteDocument(final String indexName, final String idField, String id) throws IOException;

  void deleteProcessInstanceFromTreePath(String processInstanceKey);

  List<ProcessInstanceForListViewEntity> getProcessInstancesByProcessAndStates(
      long processDefinitionKey,
      Set<ProcessInstanceState> states,
      int size,
      String[] includeFields);

  List<ProcessInstanceForListViewEntity> getProcessInstancesByParentKeys(
      Set<Long> parentProcessInstanceKeys, int size, String[] includeFields);

  long deleteProcessInstancesAndDependants(Set<Long> processInstanceKeys);

  class ProcessKey {
    private String bpmnProcessId;
    private String tenantId;

    public ProcessKey(final String bpmnProcessId, final String tenantId) {
      this.bpmnProcessId = bpmnProcessId;
      this.tenantId = tenantId;
    }

    public String getBpmnProcessId() {
      return bpmnProcessId;
    }

    public ProcessKey setBpmnProcessId(final String bpmnProcessId) {
      this.bpmnProcessId = bpmnProcessId;
      return this;
    }

    public String getTenantId() {
      return tenantId;
    }

    public ProcessKey setTenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public int hashCode() {
      return Objects.hash(bpmnProcessId, tenantId);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final ProcessKey that = (ProcessKey) o;
      return Objects.equals(bpmnProcessId, that.bpmnProcessId)
          && Objects.equals(tenantId, that.tenantId);
    }
  }
}
