/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer.variable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import org.camunda.optimize.service.es.EsBulkByScrollTaskActionProgressReporter;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.ExternalProcessVariableIndex;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_PROCESS_VARIABLE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;

@AllArgsConstructor
@Component
@Slf4j
public class ExternalProcessVariableWriter {

  private final OptimizeElasticsearchClient esClient;
  private final DateTimeFormatter dateTimeFormatter;
  private final ObjectMapper objectMapper;

  public void writeExternalProcessVariables(final List<ExternalProcessVariableDto> variables) {
    final String itemName = "external process variables";
    log.debug("Writing {} {} to Elasticsearch.", variables.size(), itemName);

    final BulkRequest bulkRequest = new BulkRequest();
    variables.forEach(variable -> addInsertExternalVariableRequest(bulkRequest, variable));

    ElasticsearchWriterUtil.doBulkRequest(
      esClient,
      bulkRequest,
      itemName,
      false // there are no nested documents in the externalProcessVariableIndex
    );
  }

  private void addInsertExternalVariableRequest(final BulkRequest bulkRequest,
                                                final ExternalProcessVariableDto externalVariable) {
    try {
      bulkRequest.add(new IndexRequest(EXTERNAL_PROCESS_VARIABLE_INDEX_NAME)
                        .source(objectMapper.writeValueAsString(externalVariable), XContentType.JSON));
    } catch (JsonProcessingException e) {
      log.warn(
        "Could not serialize external process variable: {}. This variable will not be ingested.",
        externalVariable,
        e
      );
    }
  }

  public void deleteExternalVariablesIngestedBefore(final OffsetDateTime timestamp) {
    final String deletedItemIdentifier = String.format("external variables with timestamp older than %s", timestamp);
    log.info("Deleting {}", deletedItemIdentifier);

    final EsBulkByScrollTaskActionProgressReporter progressReporter = new EsBulkByScrollTaskActionProgressReporter(
      getClass().getName(), esClient, DeleteByQueryAction.NAME
    );
    try {
      progressReporter.start();
      final BoolQueryBuilder filterQuery = boolQuery()
        .filter(rangeQuery(ExternalProcessVariableDto.Fields.ingestionTimestamp).lt(dateTimeFormatter.format(timestamp)));

      ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient,
        filterQuery,
        deletedItemIdentifier,
        false,
        // use wildcarded index name to catch all indices that exist after potential rollover
        esClient.getIndexNameService()
          .getOptimizeIndexNameWithVersionWithWildcardSuffix(new ExternalProcessVariableIndex())
      );
    } finally {
      progressReporter.stop();
    }
  }

}
