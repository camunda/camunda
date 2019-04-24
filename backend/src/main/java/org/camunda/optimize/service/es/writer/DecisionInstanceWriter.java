/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.service.es.EsBulkByScrollTaskActionProgressReporter;
import org.camunda.optimize.service.es.schema.type.DecisionInstanceType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.DECISION_DEFINITION_KEY;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@AllArgsConstructor
@Component
@Slf4j
public class DecisionInstanceWriter {

  private final ObjectMapper objectMapper;
  private final DateTimeFormatter dateTimeFormatter;
  private RestHighLevelClient esClient;

  public void importDecisionInstances(List<DecisionInstanceDto> decisionInstanceDtos) throws Exception {
    log.debug("Writing [{}] decision instances to elasticsearch", decisionInstanceDtos.size());

    BulkRequest processInstanceBulkRequest = new BulkRequest();

    for (DecisionInstanceDto decisionInstanceDto : decisionInstanceDtos) {
      addImportDecisionInstanceRequest(processInstanceBulkRequest, decisionInstanceDto);
    }
    try {
      BulkResponse bulkResponse = esClient.bulk(processInstanceBulkRequest, RequestOptions.DEFAULT);
      if (bulkResponse.hasFailures()) {
        String errorMessage = String.format(
          "There were failures while writing decision instances. Received error message: %s",
          bulkResponse.buildFailureMessage()
        );
        throw new OptimizeRuntimeException(errorMessage);
      }
    } catch (IOException e) {
      log.error("There were failures while writing decision instances.", e);
    }
  }

  private void addImportDecisionInstanceRequest(final BulkRequest bulkRequest,
                                                final DecisionInstanceDto decisionInstanceDto
  ) throws JsonProcessingException {
    final String decisionInstanceId = decisionInstanceDto.getDecisionInstanceId();
    final String source = objectMapper.writeValueAsString(decisionInstanceDto);

    final IndexRequest request = new IndexRequest(
      getOptimizeIndexAliasForType(DECISION_INSTANCE_TYPE),
      DECISION_INSTANCE_TYPE,
      decisionInstanceId
    )
      .source(source, XContentType.JSON);

    bulkRequest.add(request);
  }

  public void deleteDecisionInstancesByDefinitionKeyAndEvaluationDateOlderThan(final String decisionDefinitionKey,
                                                                               final OffsetDateTime evaluationDate) {
    log.info(
      "Deleting decision instances for decisionDefinitionKey {} and evaluationDate past {}",
      decisionDefinitionKey, evaluationDate
    );

    final EsBulkByScrollTaskActionProgressReporter progressReporter = new EsBulkByScrollTaskActionProgressReporter(
      getClass().getName(), esClient, DeleteByQueryAction.NAME
    );
    try {
      progressReporter.start();
      final BoolQueryBuilder filterQuery = boolQuery()
        .filter(termQuery(DECISION_DEFINITION_KEY, decisionDefinitionKey))
        .filter(rangeQuery(DecisionInstanceType.EVALUATION_DATE_TIME).lt(dateTimeFormatter.format(evaluationDate)));
      DeleteByQueryRequest request = new DeleteByQueryRequest(getOptimizeIndexAliasForType(DECISION_INSTANCE_TYPE))
        .setQuery(filterQuery)
        .setAbortOnVersionConflict(false)
        .setRefresh(true);

      BulkByScrollResponse bulkByScrollResponse;
      try {
        bulkByScrollResponse = esClient.deleteByQuery(request, RequestOptions.DEFAULT);
      } catch (IOException e) {
        String reason =
          String.format(
            "Could not delete decision instances for decision definition key [%s] and evaluation date [%s].",
            decisionDefinitionKey, evaluationDate
          );
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }

      log.debug(
        "BulkByScrollResponse on deleting decision instances for decisionDefinitionKey {}: {}",
        decisionDefinitionKey, bulkByScrollResponse
      );
      log.info(
        "Deleted {} decision instances for decisionDefinitionKey {} and evaluationDate past {}",
        bulkByScrollResponse.getDeleted(), decisionDefinitionKey, evaluationDate
      );
    } finally {
      progressReporter.stop();
    }
  }
}