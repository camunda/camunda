/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import io.camunda.analytics.report.ReportDefinition;
import io.camunda.analytics.serving.spi.ReportSpecStore;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.DocumentBasedWriteClient;
import io.camunda.search.clients.core.RequestBuilders;
import io.camunda.search.clients.core.SearchGetResponse;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.query.SearchQueryBuilders;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The document-backed {@link ReportSpecStore}: one implementation over OC's neutral {@link
 * DocumentBasedSearchClient}/{@link DocumentBasedWriteClient}, serving both Elasticsearch and
 * OpenSearch (the backend is whichever client is injected — no per-backend code here). The {@link
 * ReportDefinition} is stored <b>directly</b> as the document (the client's mapper serializes it);
 * {@code create} allocates the next {@code reportId} (highest stored + 1, mirroring how datasets
 * allocate {@code cubeId}) and indexes it by that id, {@code read} gets it by id, {@code search}
 * returns all reports, and {@code delete} removes one by id.
 */
public final class DocumentReportSpecStore implements ReportSpecStore {

  private static final int MAX_HITS = 10_000;

  private final DocumentBasedSearchClient searchClient;
  private final DocumentBasedWriteClient writeClient;
  private final String index;

  public DocumentReportSpecStore(
      final DocumentBasedSearchClient searchClient,
      final DocumentBasedWriteClient writeClient,
      final String index) {
    this.searchClient = searchClient;
    this.writeClient = writeClient;
    this.index = index;
  }

  @Override
  public ReportDefinition create(final ReportDefinition report) {
    final long reportId = nextReportId();
    final ReportDefinition stored =
        new ReportDefinition(
            reportId,
            report.name(),
            report.sources(),
            report.groupBy(),
            report.granularityMs(),
            report.combination(),
            report.viz());
    writeClient.index(
        RequestBuilders.<ReportDefinition>indexRequest(
            r -> r.index(index).id(Long.toString(reportId)).document(stored)));
    return stored;
  }

  @Override
  public Optional<ReportDefinition> read(final long reportId) {
    final SearchGetResponse<ReportDefinition> response =
        searchClient.get(
            RequestBuilders.getRequest(r -> r.index(index).id(Long.toString(reportId))),
            ReportDefinition.class);
    return response.found() ? Optional.of(response.source()) : Optional.empty();
  }

  @Override
  public List<ReportDefinition> search() {
    final SearchQueryResponse<ReportDefinition> response =
        searchClient.search(
            RequestBuilders.searchRequest(
                r -> r.index(index).query(SearchQueryBuilders.matchAll()).size(MAX_HITS)),
            ReportDefinition.class);
    final List<ReportDefinition> reports = new ArrayList<>(response.hits().size());
    response.hits().forEach(hit -> reports.add(hit.source()));
    return reports;
  }

  @Override
  public void delete(final long reportId) {
    writeClient.delete(
        RequestBuilders.deleteRequest(r -> r.index(index).id(Long.toString(reportId))));
  }

  /** The next id after the highest stored {@code reportId} (1 for an empty index). */
  private long nextReportId() {
    long max = 0;
    for (final ReportDefinition report : search()) {
      max = Math.max(max, report.reportId());
    }
    return max + 1;
  }
}
