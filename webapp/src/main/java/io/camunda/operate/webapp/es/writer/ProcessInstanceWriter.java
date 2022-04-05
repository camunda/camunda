/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.es.writer;

import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.schema.templates.ProcessInstanceDependant;
import io.camunda.operate.webapp.es.reader.ProcessInstanceReader;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstanceWriter {

  private static final Logger logger = LoggerFactory.getLogger(ProcessInstanceWriter.class);

  public static final List<ProcessInstanceState> STATES_FOR_DELETION = List.of(ProcessInstanceState.COMPLETED, ProcessInstanceState.CANCELED);

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ListViewTemplate processInstanceTemplate;

  @Autowired
  private List<ProcessInstanceDependant> processInstanceDependantTemplates;

  @Autowired
  private ProcessInstanceReader processInstanceReader;

  public void deleteInstanceById(Long id) throws IOException {
    ProcessInstanceForListViewEntity processInstanceEntity =
        processInstanceReader.getProcessInstanceByKey(id);
    validateDeletion(processInstanceEntity);
    deleteProcessInstanceAndDependants(processInstanceEntity.getProcessInstanceKey().toString());
  }

  public static void validateDeletion(final ProcessInstanceForListViewEntity processInstanceEntity) {
    if (!STATES_FOR_DELETION.contains(processInstanceEntity.getState())) {
      throw new IllegalArgumentException(
          String.format("Process instances needs to be in one of the states %s",
              STATES_FOR_DELETION));
    }
    if (processInstanceEntity.getEndDate() == null ||
        processInstanceEntity.getEndDate().isAfter(OffsetDateTime.now())) {
      throw new IllegalArgumentException(
          String.format("Process instances needs to have an endDate before now: %s < %s",
              processInstanceEntity.getEndDate(), OffsetDateTime.now()));
    }
  }

  private void deleteProcessInstanceAndDependants(final String processInstanceKey) throws IOException {
    List<ProcessInstanceDependant> processInstanceDependantsWithoutOperation =
        processInstanceDependantTemplates.stream()
            .filter(t -> !(t instanceof OperationTemplate))
            .collect(Collectors.toList());
    for (ProcessInstanceDependant template : processInstanceDependantsWithoutOperation) {
      deleteDocument(template.getFullQualifiedName() + "*",
          ProcessInstanceDependant.PROCESS_INSTANCE_KEY,
          processInstanceKey);
    }
    deleteDocument(processInstanceTemplate.getIndexPattern(), ListViewTemplate.PROCESS_INSTANCE_KEY,
        processInstanceKey);
  }

  private long deleteDocument(final String indexName, final String idField, String id)
      throws IOException {
    final DeleteByQueryRequest query = new DeleteByQueryRequest(indexName)
        .setQuery(QueryBuilders.termsQuery(idField, id));
    BulkByScrollResponse response = esClient.deleteByQuery(query, RequestOptions.DEFAULT);
    logger.debug("Delete document {} in {} response: {}", id, indexName, response.getStatus());
    return response.getDeleted();
  }
}
