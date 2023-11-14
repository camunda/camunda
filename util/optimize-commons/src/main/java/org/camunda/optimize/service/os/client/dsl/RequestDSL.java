/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.client.dsl;

import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.cluster.PutComponentTemplateRequest;
import org.opensearch.client.opensearch.core.ClearScrollRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.reindex.Destination;
import org.opensearch.client.opensearch.core.reindex.Source;
import org.opensearch.client.opensearch.indices.GetIndexRequest;

import static org.camunda.optimize.service.os.client.sync.OpenSearchDocumentOperations.SCROLL_KEEP_ALIVE_MS;

public interface RequestDSL {
  enum QueryType {
    ONLY_RUNTIME,
    ALL
  }

  static DeleteByQueryRequest.Builder deleteByQueryRequestBuilder(String index) {
    return new DeleteByQueryRequest.Builder().index(index);
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

  static ReindexRequest.Builder reindexRequestBuilder(String srcIndex, Query srcQuery, String dstIndex) {
    return new ReindexRequest.Builder()
      .source(Source.of(b -> b.index(srcIndex).query(srcQuery)))
      .dest(Destination.of(b -> b.index(dstIndex)));
  }

  static SearchRequest.Builder searchRequestBuilder(String index) {
    return new SearchRequest.Builder().index(index);
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
    return new ScrollRequest.Builder()
      .scrollId(scrollId)
      .scroll(time(time))
      .build();
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
}
