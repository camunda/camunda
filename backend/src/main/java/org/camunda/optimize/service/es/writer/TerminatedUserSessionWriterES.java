/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.TerminatedUserSessionDto;
import org.camunda.optimize.service.db.schema.index.TerminatedUserSessionIndex;
import org.camunda.optimize.service.db.writer.TerminatedUserSessionWriter;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import static org.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.service.db.DatabaseConstants.TERMINATED_USER_SESSION_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;

@AllArgsConstructor
@Component
@Conditional(ElasticSearchCondition.class)
public class TerminatedUserSessionWriterES extends TerminatedUserSessionWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final DateTimeFormatter dateTimeFormatter;

  @Override
  protected void performWritingTerminatedUserSession(final TerminatedUserSessionDto sessionDto) throws IOException {
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
