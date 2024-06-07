/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import static java.lang.String.format;
import static org.camunda.optimize.service.db.DatabaseConstants.INSTANT_DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.longTerms;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.not;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.dashboard.InstantDashboardDataDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.writer.InstantDashboardMetadataWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class InstantDashboardMetadataWriterOS implements InstantDashboardMetadataWriter {
  private final OptimizeOpenSearchClient osClient;

  @Override
  public void saveInstantDashboard(final InstantDashboardDataDto dashboardDataDto) {
    log.debug("Writing new Instant preview dashboard to Opensearch");
    String id = dashboardDataDto.getInstantDashboardId();

    IndexRequest.Builder<InstantDashboardDataDto> requestBuilder =
        new IndexRequest.Builder<InstantDashboardDataDto>()
            .index(INSTANT_DASHBOARD_INDEX_NAME)
            .id(id)
            .document(dashboardDataDto)
            .refresh(Refresh.True);

    Result result = osClient.index(requestBuilder).result();

    if (!Set.of(Result.Created, Result.Updated).contains(result)) {
      String message =
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
    BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
    SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(INSTANT_DASHBOARD_INDEX_NAME)
            .query(not(longTerms(InstantDashboardDataDto.Fields.templateHash, hashesAllowed)));

    List<String> dashboardIdsToBeDeleted =
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
