/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch.dsl;

import io.camunda.operate.schema.templates.TemplateDescriptor;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch.cluster.PutComponentTemplateRequest;
import org.opensearch.client.opensearch.core.ClearScrollRequest;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.indices.GetIndexRequest;

import static io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations.SCROLL_KEEP_ALIVE_MS;

public interface RequestDSL {
  enum QueryType {
    ONLY_RUNTIME,
    ALL
  }

  private static String whereToSearch(TemplateDescriptor template, QueryType queryType) {
    return switch (queryType) {
      case ONLY_RUNTIME -> template.getFullQualifiedName();
      case ALL -> template.getAlias();
    };
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

  static SearchRequest.Builder searchRequestBuilder(String index) {
    return new SearchRequest.Builder().index(index);
  }

  static SearchRequest.Builder searchRequestBuilder(TemplateDescriptor template, QueryType queryType) {
    final SearchRequest.Builder builder = new SearchRequest.Builder();
    builder.index(whereToSearch(template, queryType));
    return builder;
  }

  static SearchRequest.Builder searchRequestBuilder(TemplateDescriptor template) {
    return searchRequestBuilder(template, QueryType.ALL);
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
      .scroll(Time.of(t -> t.time(time)))
      .build();
  }

  static ScrollRequest scrollRequest(String scrollId) {
    return scrollRequest(scrollId, SCROLL_KEEP_ALIVE_MS);
  }

  static ClearScrollRequest clearScrollRequest(String scrollId) {
    return new ClearScrollRequest.Builder().scrollId(scrollId).build();
  }
}
