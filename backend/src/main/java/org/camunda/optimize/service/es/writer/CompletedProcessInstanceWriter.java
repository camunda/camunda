/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.es.EsBulkByScrollTaskActionProgressReporter;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.BUSINESS_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ENGINE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.STATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.TENANT_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@AllArgsConstructor
@Component
@Slf4j
public class CompletedProcessInstanceWriter {
  private static final Set<String> PRIMITIVE_UPDATABLE_FIELDS = ImmutableSet.of(
    PROCESS_DEFINITION_KEY, PROCESS_DEFINITION_VERSION, PROCESS_DEFINITION_ID,
    BUSINESS_KEY, START_DATE, END_DATE, DURATION, STATE,
    ENGINE, TENANT_ID
  );

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final DateTimeFormatter dateTimeFormatter;

  public void importProcessInstances(List<ProcessInstanceDto> processInstances) throws Exception {
    log.debug("Writing [{}] completed process instances to elasticsearch", processInstances.size());

    BulkRequest processInstanceBulkRequest = new BulkRequest();

    for (ProcessInstanceDto procInst : processInstances) {
      addImportProcessInstanceRequest(processInstanceBulkRequest, procInst);
    }
    BulkResponse bulkResponse = esClient.bulk(processInstanceBulkRequest, RequestOptions.DEFAULT);
    if (bulkResponse.hasFailures()) {
      String errorMessage = String.format(
        "There were failures while writing process instances with message: %s",
        bulkResponse.buildFailureMessage()
      );
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  public void deleteProcessInstancesByProcessDefinitionKeyAndEndDateOlderThan(final String processDefinitionKey,
                                                                              final OffsetDateTime endDate) {
    log.info(
      "Deleting process instances for processDefinitionKey {} and endDate past {}",
      processDefinitionKey,
      endDate
    );

    final EsBulkByScrollTaskActionProgressReporter progressReporter = new EsBulkByScrollTaskActionProgressReporter(
      getClass().getName(), esClient, DeleteByQueryAction.NAME
    );
    try {
      progressReporter.start();
      final BoolQueryBuilder filterQuery = boolQuery()
        .filter(termQuery(ProcessInstanceIndex.PROCESS_DEFINITION_KEY, processDefinitionKey))
        .filter(rangeQuery(ProcessInstanceIndex.END_DATE).lt(dateTimeFormatter.format(endDate)));
      DeleteByQueryRequest request = new DeleteByQueryRequest(PROCESS_INSTANCE_INDEX_NAME)
        .setQuery(filterQuery)
        .setAbortOnVersionConflict(false)
        .setRefresh(true);

      BulkByScrollResponse bulkByScrollResponse;
      try {
        bulkByScrollResponse = esClient.deleteByQuery(request, RequestOptions.DEFAULT);
      } catch (IOException e) {
        String reason =
          String.format("Could not delete process instances " +
                          "for process definition key [%s] and end date [%s].", processDefinitionKey, endDate);
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }

      log.debug(
        "BulkByScrollResponse on deleting process instances for processDefinitionKey {}: {}",
        processDefinitionKey,
        bulkByScrollResponse
      );
      log.info(
        "Deleted {} process instances for processDefinitionKey {} and endDate past {}",
        bulkByScrollResponse.getDeleted(),
        processDefinitionKey,
        endDate
      );
    } finally {
      progressReporter.stop();
    }

  }

  private void addImportProcessInstanceRequest(BulkRequest bulkRequest, ProcessInstanceDto procInst)
    throws JsonProcessingException {

    if (procInst.getEndDate() == null) {
      log.warn("End date should not be null for completed process instances!");
    }

    final Script updateScript = ElasticsearchWriterUtil.createPrimitiveFieldUpdateScript(
      PRIMITIVE_UPDATABLE_FIELDS,
      procInst
    );
    final String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);
    final String processInstanceId = procInst.getProcessInstanceId();
    UpdateRequest request = new UpdateRequest(
      PROCESS_INSTANCE_INDEX_NAME,
      PROCESS_INSTANCE_INDEX_NAME, processInstanceId)
      .script(updateScript)
      .upsert(newEntryIfAbsent, XContentType.JSON)
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(request);

  }

}