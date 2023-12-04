/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.opensearch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.transport.JsonEndpoint;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.endpoints.EndpointWithResponseMapperAttr;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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

  public Map<String, Object> searchAsMap(SearchRequest request) throws IOException, OpenSearchException {
    JsonEndpoint<SearchRequest, HashMap, ErrorResponse> endpoint =
      SearchRequest._ENDPOINT.withResponseDeserializer(getDeserializer(HashMap.class));

    return transport.performRequest(request, endpoint, null);
  }

  public Map<String, Object> arbitraryRequest(String method, String path, String jsonBody) throws IOException, OpenSearchException {
    JsonEndpoint<Map<String, Object>, HashMap, ErrorResponse> endpoint = arbitraryEndpoint(method, path, this.getDeserializer(HashMap.class));

    ObjectMapper objectMapper = ((JacksonJsonpMapper) transport.jsonpMapper()).objectMapper();
    Map<String, Object> map = objectMapper.readValue(jsonBody, new TypeReference<>(){});

    return transport.performRequest(map, endpoint, null);
  }

  private static <R> SimpleEndpoint<Map<String, Object>, R> arbitraryEndpoint(String method, String path, JsonpDeserializer<R> responseParser) {
    return new SimpleEndpoint<>(
      request -> method, // Request method
      request -> path, // Request path
      request -> Map.of(), // Request parameters
      request -> Map.of(), // Headers
      true, // Has body
      responseParser);
  }
}
