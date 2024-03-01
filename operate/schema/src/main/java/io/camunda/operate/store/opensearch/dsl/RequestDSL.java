/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.store.opensearch.dsl;

import static io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations.SCROLL_KEEP_ALIVE_MS;

import io.camunda.operate.schema.templates.TemplateDescriptor;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonData;
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
import org.opensearch.client.opensearch.core.reindex.Destination;
import org.opensearch.client.opensearch.core.reindex.Source;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.GetIndexRequest;
import org.opensearch.client.opensearch.indices.IndexState;
import org.opensearch.client.opensearch.snapshot.CreateSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.DeleteSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.GetRepositoryRequest;
import org.opensearch.client.opensearch.snapshot.GetSnapshotRequest;

public interface RequestDSL {
  private static String whereToSearch(TemplateDescriptor template, QueryType queryType) {
    return switch (queryType) {
      case ONLY_RUNTIME -> template.getFullQualifiedName();
      case ALL -> template.getAlias();
    };
  }

  static CreateIndexRequest.Builder createIndexRequestBuilder(
      String index, IndexState patternIndex) {
    return new CreateIndexRequest.Builder()
        .index(index)
        .aliases(patternIndex.aliases())
        .mappings(patternIndex.mappings())
        .settings(
            s ->
                s.index(
                    i ->
                        i.numberOfReplicas(patternIndex.settings().index().numberOfReplicas())
                            .numberOfShards(patternIndex.settings().index().numberOfShards())
                            .analysis(patternIndex.settings().index().analysis())));
  }

  static CreateSnapshotRequest.Builder createSnapshotRequestBuilder(
      String repository, String snapshot, List<String> indices) {
    return new CreateSnapshotRequest.Builder()
        .repository(repository)
        .snapshot(snapshot)
        .indices(indices);
  }

  static DeleteRequest.Builder deleteRequestBuilder(String index, String id) {
    return new DeleteRequest.Builder().index(index).id(id);
  }

  static DeleteByQueryRequest.Builder deleteByQueryRequestBuilder(String index) {
    return new DeleteByQueryRequest.Builder().index(index);
  }

  static DeleteSnapshotRequest.Builder deleteSnapshotRequestBuilder(
      String repositoryName, String snapshotName) {
    return new DeleteSnapshotRequest.Builder().repository(repositoryName).snapshot(snapshotName);
  }

  static <R> IndexRequest.Builder<R> indexRequestBuilder(String index) {
    return new IndexRequest.Builder<R>().index(index);
  }

  static GetIndexRequest.Builder getIndexRequestBuilder(String index) {
    return new GetIndexRequest.Builder().index(index);
  }

  static PutComponentTemplateRequest.Builder componentTemplateRequestBuilder(String name) {
    return new PutComponentTemplateRequest.Builder().name(name);
  }

  static ReindexRequest.Builder reindexRequestBuilder(
      String srcIndex, Query srcQuery, String dstIndex) {
    return new ReindexRequest.Builder()
        .source(Source.of(b -> b.index(srcIndex).query(srcQuery)))
        .dest(Destination.of(b -> b.index(dstIndex)));
  }

  static ReindexRequest.Builder reindexRequestBuilder(
      String srcIndex, String dstIndex, String script, Map<String, Object> scriptParams) {
    var jsonParams =
        scriptParams.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> JsonData.of(e.getValue())));

    return new ReindexRequest.Builder()
        .source(Source.of(b -> b.index(srcIndex)))
        .dest(Destination.of(b -> b.index(dstIndex)))
        .script(b -> b.inline(i -> i.source(script).params(jsonParams)));
  }

  static GetRepositoryRequest.Builder repositoryRequestBuilder(String name) {
    return new GetRepositoryRequest.Builder().name(name);
  }

  static SearchRequest.Builder searchRequestBuilder(String index) {
    return new SearchRequest.Builder().index(index);
  }

  static SearchRequest.Builder searchRequestBuilder(
      TemplateDescriptor template, QueryType queryType) {
    final SearchRequest.Builder builder = new SearchRequest.Builder();
    builder.index(whereToSearch(template, queryType));
    return builder;
  }

  static SearchRequest.Builder searchRequestBuilder(TemplateDescriptor template) {
    return searchRequestBuilder(template, QueryType.ALL);
  }

  static GetSnapshotRequest.Builder getSnapshotRequestBuilder(String repository, String snapshot) {
    return new GetSnapshotRequest.Builder().repository(repository).snapshot(snapshot);
  }

  static <A, R> UpdateRequest.Builder<R, A> updateRequestBuilder(String index) {
    return new UpdateRequest.Builder<R, A>().index(index);
  }

  static GetRequest.Builder getRequestBuilder(String index) {
    return new GetRequest.Builder().index(index);
  }

  static GetRequest getRequest(String index, String id) {
    return new GetRequest.Builder().index(index).id(id).build();
  }

  static ScrollRequest scrollRequest(String scrollId, String time) {
    return new ScrollRequest.Builder().scrollId(scrollId).scroll(time(time)).build();
  }

  static ScrollRequest scrollRequest(String scrollId) {
    return scrollRequest(scrollId, SCROLL_KEEP_ALIVE_MS);
  }

  static ClearScrollRequest clearScrollRequest(String scrollId) {
    return new ClearScrollRequest.Builder().scrollId(scrollId).build();
  }

  static Time time(String value) {
    return Time.of(b -> b.time(value));
  }

  enum QueryType {
    ONLY_RUNTIME,
    ALL
  }
}
