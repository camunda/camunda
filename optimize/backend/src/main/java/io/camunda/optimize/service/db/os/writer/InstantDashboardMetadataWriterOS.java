/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.INSTANT_DASHBOARD_INDEX_NAME;

import io.camunda.optimize.dto.optimize.query.dashboard.InstantDashboardDataDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.builders.OptimizeDeleteOperationBuilderOS;
import io.camunda.optimize.service.db.writer.InstantDashboardMetadataWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class InstantDashboardMetadataWriterOS implements InstantDashboardMetadataWriter {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(InstantDashboardMetadataWriterOS.class);
  private final OptimizeOpenSearchClient osClient;

  public InstantDashboardMetadataWriterOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  public void saveInstantDashboard(final InstantDashboardDataDto dashboardDataDto) {
    LOG.debug("Writing new Instant preview dashboard to Opensearch");
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
      LOG.error(message);
      throw new OptimizeRuntimeException(message);
    }

    LOG.debug("Instant preview dashboard information with id [{}] has been created", id);
  }

  @Override
  public List<String> deleteOutdatedTemplateEntriesAndGetExistingDashboardIds(
      final List<Long> hashesAllowed) throws IOException {
    final List<String> dashboardIdsToBeDeleted = new ArrayList<>();
    final SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(INSTANT_DASHBOARD_INDEX_NAME)
            .query(
                q ->
                    q.bool(
                        b ->
                            b.mustNot(
                                m ->
                                    m.terms(
                                        t ->
                                            t.field(InstantDashboardDataDto.Fields.templateHash)
                                                .terms(
                                                    tt ->
                                                        tt.value(
                                                            hashesAllowed.stream()
                                                                .map(FieldValue::of)
                                                                .toList()))))));

    final SearchResponse<InstantDashboardDataDto> searchResponse =
        osClient.search(
            requestBuilder,
            InstantDashboardDataDto.class,
            "Some errors occurred while deleting outdated instant dashboards.");

    final BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
    LOG.debug(
        "Deleting [{}] instant dashboard documents by id with bulk request.",
        searchResponse.hits().hits().size());
    searchResponse
        .hits()
        .hits()
        .forEach(
            hit -> {
              assert hit.source() != null;
              dashboardIdsToBeDeleted.add(hit.source().getDashboardId());
              bulkRequestBuilder.operations(
                  o ->
                      o.delete(
                          OptimizeDeleteOperationBuilderOS.of(
                              d ->
                                  d.optimizeIndex(osClient, INSTANT_DASHBOARD_INDEX_NAME)
                                      .id(hit.id()))));
            });
    osClient.bulk(
        bulkRequestBuilder, "Some errors occurred while deleting outdated instant dashboards.");
    return dashboardIdsToBeDeleted;
  }
}
