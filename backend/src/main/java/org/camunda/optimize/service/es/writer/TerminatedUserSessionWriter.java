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
import org.camunda.optimize.service.es.schema.index.TerminatedUserSessionIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TERMINATED_USER_SESSION_INDEX_NAME;
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
        new IndexRequest(TERMINATED_USER_SESSION_INDEX_NAME)
          .id(sessionDto.getId())
          .source(jsonSource, XContentType.JSON)
          .setRefreshPolicy(IMMEDIATE);

      esClient.index(request);
    } catch (IOException e) {
      String message = "Could not write Optimize version to Elasticsearch.";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public void deleteTerminatedUserSessionsOlderThan(final OffsetDateTime timestamp) {
    final BoolQueryBuilder filterQuery = boolQuery().filter(
      rangeQuery(TerminatedUserSessionIndex.TERMINATION_TIMESTAMP)
        .lt(dateTimeFormatter.format(timestamp))
        .format(OPTIMIZE_DATE_FORMAT)
    );

    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
      esClient,
      filterQuery,
      String.format("terminated user sessions with timestamp older than %s", timestamp),
      true,
      TERMINATED_USER_SESSION_INDEX_NAME
    );
  }
}
