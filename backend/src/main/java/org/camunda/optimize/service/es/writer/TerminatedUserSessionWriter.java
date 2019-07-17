/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.TerminatedUserSessionDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.type.TerminatedUserSessionType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TERMINATED_USER_SESSION_TYPE;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;

@AllArgsConstructor
@Component
@Slf4j
public class TerminatedUserSessionWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final DateTimeFormatter dateTimeFormatter;

  public void writeTerminatedUserSession(final TerminatedUserSessionDto sessionDto) {
    log.debug("Writing terminated user session with id [{}] to elasticsearch.", sessionDto.getId());
    try {
      final String jsonSource = objectMapper.writeValueAsString(sessionDto);

      final IndexRequest request =
        new IndexRequest(TERMINATED_USER_SESSION_TYPE, TERMINATED_USER_SESSION_TYPE, sessionDto.getId())
          .source(jsonSource, XContentType.JSON)
          .setRefreshPolicy(IMMEDIATE);

      esClient.index(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String message = "Could not write Optimize version to Elasticsearch.";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public void deleteTerminatedUserSessionsOlderThan(final OffsetDateTime timestamp) {
    log.debug("Deleting terminated user sessions older than {}", timestamp);

    final BoolQueryBuilder filterQuery = boolQuery().filter(
      rangeQuery(TerminatedUserSessionType.TERMINATION_TIMESTAMP)
        .lt(dateTimeFormatter.format(timestamp))
        .format(OPTIMIZE_DATE_FORMAT)
    );
    final DeleteByQueryRequest request = new DeleteByQueryRequest(TERMINATED_USER_SESSION_TYPE)
      .setQuery(filterQuery)
      .setAbortOnVersionConflict(false)
      .setRefresh(true);

    final BulkByScrollResponse bulkByScrollResponse;
    try {
      bulkByScrollResponse = esClient.deleteByQuery(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not delete terminated user session instances", e);
    }

    log.info(
      "Deleted {} terminated user session instances older than {}", bulkByScrollResponse.getDeleted(), timestamp
    );

  }
}
