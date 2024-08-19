/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.DatabaseConstants.TERMINATED_USER_SESSION_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.TerminatedUserSessionDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.schema.index.TerminatedUserSessionIndex;
import io.camunda.optimize.service.db.writer.TerminatedUserSessionWriter;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class TerminatedUserSessionWriterES extends TerminatedUserSessionWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final DateTimeFormatter dateTimeFormatter;

  public TerminatedUserSessionWriterES(
      final OptimizeElasticsearchClient esClient,
      final ObjectMapper objectMapper,
      final DateTimeFormatter dateTimeFormatter) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
    this.dateTimeFormatter = dateTimeFormatter;
  }

  @Override
  protected void performWritingTerminatedUserSession(final TerminatedUserSessionDto sessionDto)
      throws IOException {
    final String jsonSource = objectMapper.writeValueAsString(sessionDto);
    final IndexRequest request =
        new IndexRequest(TERMINATED_USER_SESSION_INDEX_NAME)
            .id(sessionDto.getId())
            .source(jsonSource, XContentType.JSON)
            .setRefreshPolicy(IMMEDIATE);
    esClient.index(request);
  }

  @Override
  protected void performDeleteTerminatedUserSessionOlderThan(final OffsetDateTime timestamp) {
    final BoolQueryBuilder filterQuery =
        boolQuery()
            .filter(
                rangeQuery(TerminatedUserSessionIndex.TERMINATION_TIMESTAMP)
                    .lt(dateTimeFormatter.format(timestamp))
                    .format(OPTIMIZE_DATE_FORMAT));

    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient,
        filterQuery,
        String.format("terminated user sessions with timestamp older than %s", timestamp),
        true,
        TERMINATED_USER_SESSION_INDEX_NAME);
  }
}
