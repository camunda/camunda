/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.opensearch;

import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
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

import java.util.List;
import java.util.Map;

/**
 * Wrapper class around the static RequestDSL interface. Enhances testability by allowing classes to utilize the
 * RequestDSL class without static calls, enabling unit tests to mock this out and reduce test complexity
 */
@Component
public class OpensearchRequestDSLWrapper {

    public CreateIndexRequest.Builder createIndexRequestBuilder(String index, IndexState patternIndex) {
        return RequestDSL.createIndexRequestBuilder(index, patternIndex);
    }

    public CreateSnapshotRequest.Builder createSnapshotRequestBuilder(String repository, String snapshot, List<String> indices) {
        return RequestDSL.createSnapshotRequestBuilder(repository, snapshot, indices);
    }

    public DeleteRequest.Builder deleteRequestBuilder(String index, String id) {
        return RequestDSL.deleteRequestBuilder(index, id);
    }

    public DeleteByQueryRequest.Builder deleteByQueryRequestBuilder(String index) {
        return RequestDSL.deleteByQueryRequestBuilder(index);
    }

    public DeleteSnapshotRequest.Builder deleteSnapshotRequestBuilder(String repositoryName, String snapshotName) {
        return RequestDSL.deleteSnapshotRequestBuilder(repositoryName, snapshotName);
    }

    public <R> IndexRequest.Builder<R> indexRequestBuilder(String index) {
        return RequestDSL.indexRequestBuilder(index);
    }

    public GetIndexRequest.Builder getIndexRequestBuilder(String index) {
        return RequestDSL.getIndexRequestBuilder(index);
    }

    public PutComponentTemplateRequest.Builder componentTemplateRequestBuilder(String name) {
        return RequestDSL.componentTemplateRequestBuilder(name);
    }

    public ReindexRequest.Builder reindexRequestBuilder(String srcIndex, Query srcQuery, String dstIndex) {
        return RequestDSL.reindexRequestBuilder(srcIndex, srcQuery, dstIndex);
    }

    public ReindexRequest.Builder reindexRequestBuilder(String srcIndex, String dstIndex, String script, Map<String, Object> scriptParams) {
        return RequestDSL.reindexRequestBuilder(srcIndex, dstIndex, script, scriptParams);
    }

    public GetRepositoryRequest.Builder repositoryRequestBuilder(String name) {
        return RequestDSL.repositoryRequestBuilder(name);
    }

    public SearchRequest.Builder searchRequestBuilder(String index) {
        return RequestDSL.searchRequestBuilder(index);
    }

    public SearchRequest.Builder searchRequestBuilder(TemplateDescriptor template, RequestDSL.QueryType queryType) {
        return RequestDSL.searchRequestBuilder(template, queryType);
    }

    public SearchRequest.Builder searchRequestBuilder(TemplateDescriptor template) {
        return RequestDSL.searchRequestBuilder(template);
    }

    public GetSnapshotRequest.Builder getSnapshotRequestBuilder(String repository, String snapshot) {
        return RequestDSL.getSnapshotRequestBuilder(repository, snapshot);
    }

    public <A, R> UpdateRequest.Builder<R, A> updateRequestBuilder(String index) {
        return RequestDSL.updateRequestBuilder(index);
    }

    public GetRequest.Builder getRequestBuilder(String index) {
        return RequestDSL.getRequestBuilder(index);
    }

    public GetRequest getRequest(String index, String id) {
        return RequestDSL.getRequest(index, id);
    }

    public ScrollRequest scrollRequest(String scrollId, String time) {
        return RequestDSL.scrollRequest(scrollId, time);
    }

    public ScrollRequest scrollRequest(String scrollId) {
        return RequestDSL.scrollRequest(scrollId);
    }

    public ClearScrollRequest clearScrollRequest(String scrollId) {
        return RequestDSL.clearScrollRequest(scrollId);
    }

    public Time time(String value) {
        return RequestDSL.time(value);
    }
}
