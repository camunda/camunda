/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch;

import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.cluster.PutComponentTemplateRequest;
import org.opensearch.client.opensearch.core.ClearScrollRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.GetIndexRequest;
import org.opensearch.client.opensearch.indices.IndexState;
import org.opensearch.client.opensearch.snapshot.CreateSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.DeleteSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.GetRepositoryRequest;
import org.opensearch.client.opensearch.snapshot.GetSnapshotRequest;
import org.springframework.stereotype.Component;

/**
 * Wrapper class around the static RequestDSL interface. Enhances testability by allowing classes to
 * utilize the RequestDSL class without static calls, enabling unit tests to mock this out and
 * reduce test complexity
 */
@Component
public class OpensearchRequestDSLWrapper {

  public CreateIndexRequest.Builder createIndexRequestBuilder(
      final String index, final IndexState patternIndex) {
    return RequestDSL.createIndexRequestBuilder(index, patternIndex);
  }

  public CreateSnapshotRequest.Builder createSnapshotRequestBuilder(
      final String repository, final String snapshot, final List<String> indices) {
    return RequestDSL.createSnapshotRequestBuilder(repository, snapshot, indices);
  }

  public DeleteRequest.Builder deleteRequestBuilder(final String index, final String id) {
    return RequestDSL.deleteRequestBuilder(index, id);
  }

  public DeleteByQueryRequest.Builder deleteByQueryRequestBuilder(final String index) {
    return RequestDSL.deleteByQueryRequestBuilder(index);
  }

  public DeleteSnapshotRequest.Builder deleteSnapshotRequestBuilder(
      final String repositoryName, final String snapshotName) {
    return RequestDSL.deleteSnapshotRequestBuilder(repositoryName, snapshotName);
  }

  public <R> IndexRequest.Builder<R> indexRequestBuilder(final String index) {
    return RequestDSL.indexRequestBuilder(index);
  }

  public GetIndexRequest.Builder getIndexRequestBuilder(final String index) {
    return RequestDSL.getIndexRequestBuilder(index);
  }

  public PutComponentTemplateRequest.Builder componentTemplateRequestBuilder(final String name) {
    return RequestDSL.componentTemplateRequestBuilder(name);
  }

  public ReindexRequest.Builder reindexRequestBuilder(
      final String srcIndex, final Query srcQuery, final String dstIndex) {
    return RequestDSL.reindexRequestBuilder(srcIndex, srcQuery, dstIndex);
  }

  public ReindexRequest.Builder reindexRequestBuilder(
      final String srcIndex,
      final String dstIndex,
      final String script,
      final Map<String, Object> scriptParams) {
    return RequestDSL.reindexRequestBuilder(srcIndex, dstIndex, script, scriptParams);
  }

  public GetRepositoryRequest.Builder repositoryRequestBuilder(final String name) {
    return RequestDSL.repositoryRequestBuilder(name);
  }

  public SearchRequest.Builder searchRequestBuilder(final String index) {
    return RequestDSL.searchRequestBuilder(index);
  }

  public SearchRequest.Builder searchRequestBuilder(
      final IndexTemplateDescriptor template, final RequestDSL.QueryType queryType) {
    return RequestDSL.searchRequestBuilder(template, queryType);
  }

  public SearchRequest.Builder searchRequestBuilder(final IndexTemplateDescriptor template) {
    return RequestDSL.searchRequestBuilder(template);
  }

  public GetSnapshotRequest.Builder getSnapshotRequestBuilder(
      final String repository, final String snapshot) {
    return RequestDSL.getSnapshotRequestBuilder(repository, snapshot);
  }

  public <A, R> UpdateRequest.Builder<R, A> updateRequestBuilder(final String index) {
    return RequestDSL.updateRequestBuilder(index);
  }

  public GetRequest.Builder getRequestBuilder(final String index) {
    return RequestDSL.getRequestBuilder(index);
  }

  public GetRequest getRequest(final String index, final String id) {
    return RequestDSL.getRequest(index, id);
  }

  public ScrollRequest scrollRequest(final String scrollId, final String time) {
    return RequestDSL.scrollRequest(scrollId, time);
  }

  public ScrollRequest scrollRequest(final String scrollId) {
    return RequestDSL.scrollRequest(scrollId);
  }

  public ClearScrollRequest clearScrollRequest(final String scrollId) {
    return RequestDSL.clearScrollRequest(scrollId);
  }

  public Time time(final String value) {
    return RequestDSL.time(value);
  }
}
