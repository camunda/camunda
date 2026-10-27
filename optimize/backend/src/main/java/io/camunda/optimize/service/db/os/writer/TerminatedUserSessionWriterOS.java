/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.TERMINATED_USER_SESSION_INDEX_NAME;

import io.camunda.optimize.dto.optimize.query.TerminatedUserSessionDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.schema.index.TerminatedUserSessionIndex;
import io.camunda.optimize.service.db.writer.TerminatedUserSessionWriter;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class TerminatedUserSessionWriterOS extends TerminatedUserSessionWriter {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(TerminatedUserSessionWriterOS.class);
  private final OptimizeOpenSearchClient osClient;
  private final DateTimeFormatter dateTimeFormatter;

  public TerminatedUserSessionWriterOS(
      final OptimizeOpenSearchClient osClient, final DateTimeFormatter dateTimeFormatter) {
    this.osClient = osClient;
    this.dateTimeFormatter = dateTimeFormatter;
  }

  @Override
  protected void performWritingTerminatedUserSession(final TerminatedUserSessionDto sessionDto) {
    final IndexRequest.Builder<TerminatedUserSessionDto> request =
        new IndexRequest.Builder<TerminatedUserSessionDto>()
            .index(TERMINATED_USER_SESSION_INDEX_NAME)
            .id(sessionDto.getId())
            .document(sessionDto)
            .refresh(Refresh.True);
    osClient.index(request);
  }

  @Override
  protected void performDeleteTerminatedUserSessionOlderThan(final OffsetDateTime timestamp)
      throws IOException {
    final Query filterQuery =
        QueryDSL.lte(
            TerminatedUserSessionIndex.TERMINATION_TIMESTAMP, dateTimeFormatter.format(timestamp));

    osClient.deleteByQuery(filterQuery, true, TERMINATED_USER_SESSION_INDEX_NAME);
  }
}
