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
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.service.es.EsBulkByScrollTaskActionProgressReporter;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.springframework.stereotype.Component;

import java.security.InvalidParameterException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.DECISION_DEFINITION_KEY;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@AllArgsConstructor
@Component
@Slf4j
public class DecisionInstanceWriter {

  private final ObjectMapper objectMapper;
  private final DateTimeFormatter dateTimeFormatter;
  private final OptimizeElasticsearchClient esClient;

  public void importDecisionInstances(List<DecisionInstanceDto> decisionInstanceDtos) {
    String importItemName = "decision definition information";
    log.debug("Writing [{}] {} to ES.", decisionInstanceDtos.size(), importItemName);
    ElasticsearchWriterUtil.doBulkRequestWithList(
      esClient,
      importItemName,
      decisionInstanceDtos,
      this::addImportDecisionInstanceRequest
    );
  }

  private void addImportDecisionInstanceRequest(final BulkRequest bulkRequest,
                                                final OptimizeDto optimizeDto) {
    if (!(optimizeDto instanceof DecisionInstanceDto)) {
      throw new InvalidParameterException("Method called with incorrect instance of DTO.");
    }
    DecisionInstanceDto decisionInstanceDto = (DecisionInstanceDto) optimizeDto;

    final String decisionInstanceId = decisionInstanceDto.getDecisionInstanceId();
    String source = "";
    try {
      source = objectMapper.writeValueAsString(decisionInstanceDto);
    } catch (JsonProcessingException e) {
      String reason =
        String.format("Error while processing JSON for decision instance DTO with ID [%s].",
                      decisionInstanceDto.getProcessInstanceId());
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    final IndexRequest request = new IndexRequest(DECISION_INSTANCE_INDEX_NAME)
      .id(decisionInstanceId)
      .source(source, XContentType.JSON);

    bulkRequest.add(request);
  }

  public void deleteDecisionInstancesByDefinitionKeyAndEvaluationDateOlderThan(final String decisionDefinitionKey,
                                                                               final OffsetDateTime evaluationDate) {
    final EsBulkByScrollTaskActionProgressReporter progressReporter = new EsBulkByScrollTaskActionProgressReporter(
      getClass().getName(), esClient, DeleteByQueryAction.NAME
    );
    try {
      progressReporter.start();
      final BoolQueryBuilder filterQuery = boolQuery()
        .filter(termQuery(DECISION_DEFINITION_KEY, decisionDefinitionKey))
        .filter(rangeQuery(DecisionInstanceIndex.EVALUATION_DATE_TIME).lt(dateTimeFormatter.format(evaluationDate)));

      ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient,
        filterQuery,
        String.format(
          "decision instances with definitionKey %s and evaluationDate past %s",
          decisionDefinitionKey,
          evaluationDate
        ),
        true,
        DECISION_INSTANCE_INDEX_NAME
      );
    } finally {
      progressReporter.stop();
    }
  }
}