/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.INSTANT_DASHBOARD_INDEX_NAME;
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.longTerms;
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.not;
import static java.lang.String.format;

import io.camunda.optimize.dto.optimize.query.dashboard.InstantDashboardDataDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.writer.InstantDashboardMetadataWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class InstantDashboardMetadataWriterOS implements InstantDashboardMetadataWriter {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(InstantDashboardMetadataWriterOS.class);
  private final OptimizeOpenSearchClient osClient;

  public InstantDashboardMetadataWriterOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  public void saveInstantDashboard(final InstantDashboardDataDto dashboardDataDto) {
    log.debug("Writing new Instant preview dashboard to Opensearch");
    final String id = dashboardDataDto.getInstantDashboardId();

    final IndexRequest.Builder<InstantDashboardDataDto> requestBuilder =
        new IndexRequest.Builder<InstantDashboardDataDto>()
            .index(INSTANT_DASHBOARD_INDEX_NAME)
            .id(id)
            .document(dashboardDataDto)
            .refresh(Refresh.True);

    final Result result = osClient.index(requestBuilder).result();

    if (!Set.of(Result.Created, Result.Updated).contains(result)) {
      final String message =
          "Could not write Instant preview dashboard data to Opensearch. "
              + "Maybe the connection to Opensearch got lost?";
      log.error(message);
      throw new OptimizeRuntimeException(message);
    }

    log.debug("Instant preview dashboard information with id [{}] has been created", id);
  }

  @Override
  public List<String> deleteOutdatedTemplateEntriesAndGetExistingDashboardIds(
      final List<Long> hashesAllowed) throws IOException {
    record Result(String dashboardId) {}

    final BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
    final SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(INSTANT_DASHBOARD_INDEX_NAME)
            .query(not(longTerms(InstantDashboardDataDto.Fields.templateHash, hashesAllowed)));

    final List<String> dashboardIdsToBeDeleted =
        osClient.searchValues(requestBuilder, Result.class).stream()
            .map(Result::dashboardId)
            .toList();

    if (!dashboardIdsToBeDeleted.isEmpty()) {
      dashboardIdsToBeDeleted.forEach(
          id ->
              bulkRequestBuilder.operations(
                  op -> op.delete(del -> del.index(INSTANT_DASHBOARD_INDEX_NAME).id(id))));

      log.debug(
          "Deleting [{}] instant dashboard documents by id with bulk request.",
          dashboardIdsToBeDeleted.size());

      osClient.bulk(
          bulkRequestBuilder,
          format(
              "Failed to bulk delete from %s %s outdated template entries: %s",
              INSTANT_DASHBOARD_INDEX_NAME,
              dashboardIdsToBeDeleted.size(),
              dashboardIdsToBeDeleted));
    }

    return dashboardIdsToBeDeleted;
  }
}
