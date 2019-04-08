/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.es.EsBulkByScrollTaskActionProgressReporter;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class CompletedProcessInstanceWriter {
  private final Logger logger = LoggerFactory.getLogger(getClass());


  private RestHighLevelClient esClient;
  private ObjectMapper objectMapper;
  private DateTimeFormatter dateTimeFormatter;

  @Autowired
  public CompletedProcessInstanceWriter(RestHighLevelClient esClient,
                                        ObjectMapper objectMapper,
                                        DateTimeFormatter dateTimeFormatter) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
    this.dateTimeFormatter = dateTimeFormatter;
  }

  public void importProcessInstances(List<ProcessInstanceDto> processInstances) throws Exception {
    logger.debug("Writing [{}] completed process instances to elasticsearch", processInstances.size());

    BulkRequest processInstanceBulkRequest = new BulkRequest();

    for (ProcessInstanceDto procInst : processInstances) {
      addImportProcessInstanceRequest(processInstanceBulkRequest, procInst);
    }
    BulkResponse bulkResponse = esClient.bulk(processInstanceBulkRequest, RequestOptions.DEFAULT);
    if (bulkResponse.hasFailures()) {
      String errorMessage = String.format(
        "There were failures while writing process instances with message: {}",
        bulkResponse.buildFailureMessage()
      );
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  public void deleteProcessInstancesByProcessDefinitionKeyAndEndDateOlderThan(final String processDefinitionKey,
                                                                              final OffsetDateTime endDate) {
    logger.info(
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
        .filter(termQuery(ProcessInstanceType.PROCESS_DEFINITION_KEY, processDefinitionKey))
        .filter(rangeQuery(ProcessInstanceType.END_DATE).lt(dateTimeFormatter.format(endDate)));
      DeleteByQueryRequest request = new DeleteByQueryRequest(getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE))
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
        logger.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }

      logger.debug(
        "BulkByScrollResponse on deleting process instances for processDefinitionKey {}: {}",
        processDefinitionKey,
        bulkByScrollResponse
      );
      logger.info(
        "Deleted {} process instances for processDefinitionKey {} and endDate past {}",
        bulkByScrollResponse.getDeleted(),
        processDefinitionKey,
        endDate
      );
    } finally {
      progressReporter.stop();
    }

  }

  private void addImportProcessInstanceRequest(BulkRequest bulkRequest, ProcessInstanceDto procInst) throws
                                                                                                     JsonProcessingException {
    String processInstanceId = procInst.getProcessInstanceId();
    Map<String, Object> params = new HashMap<>();
    params.put(ProcessInstanceType.START_DATE, dateTimeFormatter.format(procInst.getStartDate()));
    String endDate = (procInst.getEndDate() != null) ? dateTimeFormatter.format(procInst.getEndDate()) : null;
    if (endDate == null) {
      logger.warn("End date should not be null for completed process instances!");
    }
    params.put(ProcessInstanceType.STATE, procInst.getState());
    params.put(ProcessInstanceType.END_DATE, endDate);
    params.put(ProcessInstanceType.ENGINE, procInst.getEngine());
    params.put(ProcessInstanceType.DURATION, procInst.getDurationInMs());
    params.put(ProcessInstanceType.PROCESS_DEFINITION_VERSION, procInst.getProcessDefinitionVersion());
    params.put(ProcessInstanceType.BUSINESS_KEY, procInst.getBusinessKey());

    Script updateScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.startDate = params.startDate; " +
        "ctx._source.endDate = params.endDate; " +
        "ctx._source.durationInMs = params.durationInMs;" +
        "ctx._source.processDefinitionVersion = params.processDefinitionVersion;" +
        "ctx._source.engine = params.engine;" +
        "ctx._source.businessKey = params.businessKey;" +
        "ctx._source.state = params.state;",
      params
    );

    String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);

    UpdateRequest request =
      new UpdateRequest(getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE), PROC_INSTANCE_TYPE, processInstanceId)
        .script(updateScript)
        .upsert(newEntryIfAbsent, XContentType.JSON)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(request);

  }

}