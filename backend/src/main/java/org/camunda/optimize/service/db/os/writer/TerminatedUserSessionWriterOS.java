/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.TerminatedUserSessionDto;
import org.camunda.optimize.service.db.schema.index.TerminatedUserSessionIndex;
import org.camunda.optimize.service.db.writer.TerminatedUserSessionWriter;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import static org.camunda.optimize.service.db.DatabaseConstants.TERMINATED_USER_SESSION_INDEX_NAME;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class TerminatedUserSessionWriterOS extends TerminatedUserSessionWriter {

  private final OptimizeOpenSearchClient osClient;
  private final DateTimeFormatter dateTimeFormatter;

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
  protected void performDeleteTerminatedUserSessionOlderThan(final OffsetDateTime timestamp) throws IOException {
    final Query filterQuery =
      QueryDSL.gt(TerminatedUserSessionIndex.TERMINATION_TIMESTAMP, dateTimeFormatter.format(timestamp));

    osClient.deleteByQuery(filterQuery, TERMINATED_USER_SESSION_INDEX_NAME);
  }

}
