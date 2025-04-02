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

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.optimize.dto.optimize.query.TerminatedUserSessionDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexRequestBuilderES;
import io.camunda.optimize.service.db.repository.es.TaskRepositoryES;
import io.camunda.optimize.service.db.schema.index.TerminatedUserSessionIndex;
import io.camunda.optimize.service.db.writer.TerminatedUserSessionWriter;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Conditional(ElasticSearchCondition.class)
public class TerminatedUserSessionWriterES extends TerminatedUserSessionWriter {

  private final OptimizeElasticsearchClient esClient;
  private final TaskRepositoryES taskRepositoryES;

  @Override
  protected void performWritingTerminatedUserSession(final TerminatedUserSessionDto sessionDto)
      throws IOException {
    esClient.index(
        OptimizeIndexRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, TERMINATED_USER_SESSION_INDEX_NAME)
                    .id(sessionDto.getId())
                    .refresh(Refresh.True)
                    .document(sessionDto)));
  }

  @Override
  protected void performDeleteTerminatedUserSessionOlderThan(final OffsetDateTime timestamp) {
    final Query filterQuery =
        Query.of(
            b ->
                b.bool(
                    bb ->
                        bb.filter(
                            f ->
                                f.range(
                                    r ->
                                        r.date(
                                            d ->
                                                d.field(
                                                        TerminatedUserSessionIndex
                                                            .TERMINATION_TIMESTAMP)
                                                    .lt(String.valueOf(timestamp))
                                                    .format(OPTIMIZE_DATE_FORMAT))))));

    taskRepositoryES.tryDeleteByQueryRequest(
        filterQuery,
        String.format("terminated user sessions with timestamp older than %s", timestamp),
        true,
        TERMINATED_USER_SESSION_INDEX_NAME);
  }
}
