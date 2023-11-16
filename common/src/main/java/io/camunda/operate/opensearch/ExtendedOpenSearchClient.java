/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.opensearch;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.transport.JsonEndpoint;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.endpoints.EndpointWithResponseMapperAttr;

import java.io.IOException;

public class ExtendedOpenSearchClient extends OpenSearchClient {

  public ExtendedOpenSearchClient(OpenSearchTransport transport) {
    super(transport);
  }

  public <TDocument> SearchResponse<TDocument> fixedSearch(SearchRequest request, Class<TDocument> tDocumentClass)
    throws IOException, OpenSearchException {

    JsonEndpoint<CamundaPatchedSearchRequest, SearchResponse<TDocument>, ErrorResponse> endpoint =
      (JsonEndpoint<CamundaPatchedSearchRequest, SearchResponse<TDocument>, ErrorResponse>) CamundaPatchedSearchRequest._ENDPOINT;
    endpoint = new EndpointWithResponseMapperAttr<>(endpoint,
      "org.opensearch.client:Deserializer:_global.search.TDocument", getDeserializer(tDocumentClass));

    return transport.performRequest(CamundaPatchedSearchRequest.from(request), endpoint, null);
  }
}
