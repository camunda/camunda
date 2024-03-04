/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.opensearch;

import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
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
      String index, IndexState patternIndex) {
    return RequestDSL.createIndexRequestBuilder(index, patternIndex);
  }

  public CreateSnapshotRequest.Builder createSnapshotRequestBuilder(
      String repository, String snapshot, List<String> indices) {
    return RequestDSL.createSnapshotRequestBuilder(repository, snapshot, indices);
  }

  public DeleteRequest.Builder deleteRequestBuilder(String index, String id) {
    return RequestDSL.deleteRequestBuilder(index, id);
  }

  public DeleteByQueryRequest.Builder deleteByQueryRequestBuilder(String index) {
    return RequestDSL.deleteByQueryRequestBuilder(index);
  }

  public DeleteSnapshotRequest.Builder deleteSnapshotRequestBuilder(
      String repositoryName, String snapshotName) {
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

  public ReindexRequest.Builder reindexRequestBuilder(
      String srcIndex, Query srcQuery, String dstIndex) {
    return RequestDSL.reindexRequestBuilder(srcIndex, srcQuery, dstIndex);
  }

  public ReindexRequest.Builder reindexRequestBuilder(
      String srcIndex, String dstIndex, String script, Map<String, Object> scriptParams) {
    return RequestDSL.reindexRequestBuilder(srcIndex, dstIndex, script, scriptParams);
  }

  public GetRepositoryRequest.Builder repositoryRequestBuilder(String name) {
    return RequestDSL.repositoryRequestBuilder(name);
  }

  public SearchRequest.Builder searchRequestBuilder(String index) {
    return RequestDSL.searchRequestBuilder(index);
  }

  public SearchRequest.Builder searchRequestBuilder(
      TemplateDescriptor template, RequestDSL.QueryType queryType) {
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
