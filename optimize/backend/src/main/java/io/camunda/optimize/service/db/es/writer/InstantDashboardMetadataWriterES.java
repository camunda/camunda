/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.INSTANT_DASHBOARD_INDEX_NAME;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.dashboard.InstantDashboardDataDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeDeleteOperationBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.writer.InstantDashboardMetadataWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class InstantDashboardMetadataWriterES implements InstantDashboardMetadataWriter {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(InstantDashboardMetadataWriterES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public InstantDashboardMetadataWriterES(
      final OptimizeElasticsearchClient esClient,
      final @Qualifier("optimizeObjectMapper") ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public void saveInstantDashboard(final InstantDashboardDataDto dashboardDataDto) {
    LOG.debug("Writing new Instant preview dashboard to Elasticsearch");
    final String id = dashboardDataDto.getInstantDashboardId();
    try {
      final IndexResponse indexResponse =
          esClient.index(
              OptimizeIndexRequestBuilderES.of(
                  i ->
                      i.optimizeIndex(esClient, INSTANT_DASHBOARD_INDEX_NAME)
                          .id(id)
                          .document(dashboardDataDto)
                          .refresh(Refresh.True)));

      if (!indexResponse.result().equals(Result.Created)
          && !indexResponse.result().equals(Result.Updated)) {
        final String message =
            "Could not write Instant preview dashboard data to Elasticsearch. "
                + "Maybe the connection to Elasticsearch got lost?";
        LOG.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (final IOException e) {
      final String errorMessage =
          "Could not write Instant preview dashboard data to Elasticsearch.";
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    LOG.debug("Instant preview dashboard information with id [{}] has been created", id);
  }

  @Override
  public List<String> deleteOutdatedTemplateEntriesAndGetExistingDashboardIds(
      final List<Long> hashesAllowed) throws IOException {
    final List<String> dashboardIdsToBeDeleted = new ArrayList<>();
    final SearchResponse<Map> searchResponse =
        esClient.search(
            OptimizeSearchRequestBuilderES.of(
                s ->
                    s.optimizeIndex(esClient, INSTANT_DASHBOARD_INDEX_NAME)
                        .query(
                            q ->
                                q.bool(
                                    b ->
                                        b.mustNot(
                                            m ->
                                                m.terms(
                                                    t ->
                                                        t.field(
                                                                InstantDashboardDataDto.Fields
                                                                    .templateHash)
                                                            .terms(
                                                                tt ->
                                                                    tt.value(
                                                                        hashesAllowed.stream()
                                                                            .map(FieldValue::of)
                                                                            .toList()))))))),
            Map.class);
    final BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
    LOG.debug(
        "Deleting [{}] instant dashboard documents by id with bulk request.",
        searchResponse.hits().hits().size());
    searchResponse
        .hits()
        .hits()
        .forEach(
            hit -> {
              dashboardIdsToBeDeleted.add(
                  (String) hit.source().get(InstantDashboardDataDto.Fields.dashboardId));
              bulkRequestBuilder.operations(
                  o ->
                      o.delete(
                          OptimizeDeleteOperationBuilderES.of(
                              d ->
                                  d.optimizeIndex(esClient, INSTANT_DASHBOARD_INDEX_NAME)
                                      .id(hit.id()))));
            });
    esClient.doBulkRequest(
        searchResponse.hits().hits().isEmpty() ? null : bulkRequestBuilder.build(),
        INSTANT_DASHBOARD_INDEX_NAME,
        false);
    return dashboardIdsToBeDeleted;
  }
}
