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
import java.util.*;

public interface ProcessStore {


  // General methods -> TODO: refactor to upper interface?
  Optional<Long> getDistinctCountFor(final String fieldName);

  // ProcessStore
  ProcessEntity getProcessByKey(final Long processDefinitionKey);

  String getDiagramByKey(final Long processDefinitionKey);

  Map<ProcessKey, List<ProcessEntity>> getProcessesGrouped(String tenantId, @Nullable Set<String> allowedBPMNprocessIds);

  Map<Long, ProcessEntity> getProcessesIdsToProcessesWithFields(@Nullable Set<String> allowedBPMNIds, int maxSize, String... fields);

  /// Process instance methods
  ProcessInstanceForListViewEntity getProcessInstanceListViewByKey(Long processInstanceKey);

  Map<String, Long> getCoreStatistics(@Nullable Set<String> allowedBPMNids);

  String getProcessInstanceTreePathById(final String processInstanceId);

  List<Map<String, String>> createCallHierarchyFor(List<String> processInstanceIds, final String currentProcessInstanceId);

  long deleteDocument(final String indexName, final String idField, String id) throws IOException;

  void deleteProcessInstanceFromTreePath(String processInstanceKey);

  class ProcessKey {
    private String bpmnProcessId;
    private String tenantId;

    public ProcessKey(String bpmnProcessId, String tenantId) {
      this.bpmnProcessId = bpmnProcessId;
      this.tenantId = tenantId;
    }

    public String getBpmnProcessId() {
      return bpmnProcessId;
    }

    public ProcessKey setBpmnProcessId(String bpmnProcessId) {
      this.bpmnProcessId = bpmnProcessId;
      return this;
    }

    public String getTenantId() {
      return tenantId;
    }

    public ProcessKey setTenantId(String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
      ProcessKey that = (ProcessKey) o;
      return Objects.equals(bpmnProcessId, that.bpmnProcessId) && Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(bpmnProcessId, tenantId);
    }
  }
}
