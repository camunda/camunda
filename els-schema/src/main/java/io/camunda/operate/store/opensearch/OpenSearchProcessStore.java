/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch;

import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.store.ProcessStore;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Profile("opensearch")
@Component
public class OpenSearchProcessStore implements ProcessStore {
  private static final Logger logger = LoggerFactory.getLogger(OpenSearchProcessStore.class);
  @Autowired
  private OpenSearchClient openSearchClient;

  @Autowired
  private ProcessIndex processIndex;

  @Override
  public Optional<Long> getDistinctCountFor(String fieldName) {
    return Optional.empty();
  }

  @Override
  public ProcessEntity getProcessByKey(Long processDefinitionKey) {
    try {
      final SearchResponse<ProcessEntity> response = openSearchClient.search(s -> s
                      .index(List.of(processIndex.getAlias()))
                      .query(q -> q
                              .term(t -> t.field(ProcessIndex.KEY).value(FieldValue.of(processDefinitionKey)))),
              ProcessEntity.class);
      final long total = response.hits().total().value();
      if (total == 1) {
        return response.documents().get(0);
      } else if (total > 1) {
        throw new OperateRuntimeException(String.format("Could not find unique process with key '%s'.", processDefinitionKey));
      } else {
        throw new OperateRuntimeException(String.format("Could not find process with key '%s'.", processDefinitionKey));
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public String getDiagramByKey(Long processDefinitionKey) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<ProcessKey, List<ProcessEntity>> getProcessesGrouped(String tenantId, Set<String> allowedBPMNprocessIds) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<Long, ProcessEntity> getProcessIdsToProcesses() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<Long, ProcessEntity> getProcessesIdsToProcessesWithFields(Set<String> allowedBPMNIds,
                                                                       int maxSize, String... fields) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ProcessInstanceForListViewEntity getProcessInstanceListViewByKey(Long processInstanceKey) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<String, Long> getCoreStatistics(Set<String> allowedBPMNids) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getProcessInstanceTreePathById(String processInstanceId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Map<String, String>> createCallHierarchyFor(List<String> processInstanceIds,
                                                          String currentProcessInstanceId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long deleteDocument(String indexName, String idField, String id) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void deleteProcessInstanceFromTreePath(String processInstanceKey) {
    throw new UnsupportedOperationException();
  }
}
