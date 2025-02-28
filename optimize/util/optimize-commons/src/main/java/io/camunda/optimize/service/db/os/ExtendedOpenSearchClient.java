/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os;

import static java.lang.String.format;
import static java.lang.String.join;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.CountResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.snapshot.GetSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.GetSnapshotResponse;
import org.opensearch.client.opensearch.snapshot.GetSnapshotResponse.Builder;
import org.opensearch.client.opensearch.snapshot.SnapshotInfo;
import org.opensearch.client.transport.JsonEndpoint;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.endpoints.EndpointWithResponseMapperAttr;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;

public class ExtendedOpenSearchClient extends OpenSearchClient {
  private static final Pattern SEARCH_AFTER_PATTERN =
      Pattern.compile("(\"search_after\":\\[[^\\]]*\\])");
  private static final String DOCUMENT_ATTR =
      "org.opensearch.client:Deserializer:_global.search.TDocument";

  public ExtendedOpenSearchClient(final OpenSearchTransport transport) {
    super(transport);
  }

  private static <R> SimpleEndpoint<Map<String, Object>, R> arbitraryEndpoint(
      final String method, final String path, final JsonpDeserializer<R> responseParser) {
    return new SimpleEndpoint<>(
        request -> method, // Request method
        request -> path, // Request path
        request -> Map.of(), // Request parameters
        request -> Map.of(), // Headers
        true, // Has body
        responseParser);
  }

  private ObjectMapper objectMapper() {
    return ((JacksonJsonpMapper) transport.jsonpMapper()).objectMapper();
  }

  private Map<String, Object> jsonToMap(final String json) throws JsonProcessingException {
    return objectMapper().readValue(json, new TypeReference<>() {});
  }

  private String json(final SearchRequest request) {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final JsonGenerator generator = transport.jsonpMapper().jsonProvider().createGenerator(baos);
    request.serialize(generator, transport.jsonpMapper());
    generator.close();
    return baos.toString();
  }

  /**
   * Fixes searchAfter block in SearchRequest request by replacing minimum long values represented
   * as String with Long representations.
   *
   * @param json SearchRequest json
   * @return fixed json
   */
  private String fixSearchAfter(final String json) {
    final Matcher m = SEARCH_AFTER_PATTERN.matcher(json);
    if (m.find()) {
      final var searchAfter = m.group(1); // Find "searchAfter" block in search request
      // Replace all occurrences of minimum long value string representations with long
      // representations inside searchAfter
      final var fixedSearchAfter =
          searchAfter.replaceAll(format("\"%s\"", Long.MIN_VALUE), String.valueOf(Long.MIN_VALUE));
      return json.replace(searchAfter, fixedSearchAfter);
    } else {
      return json;
    }
  }

  public <TDocument> SearchResponse<TDocument> fixedSearch(
      final SearchRequest request, final Class<TDocument> tDocumentClass)
      throws IOException, OpenSearchException {
    final var path = format("/%s/_search", join(",", request.index()));
    JsonEndpoint<Map<String, Object>, SearchResponse<Object>, ErrorResponse> endpoint =
        arbitraryEndpoint("POST", path, SearchResponse._DESERIALIZER);
    endpoint =
        new EndpointWithResponseMapperAttr<>(
            endpoint, DOCUMENT_ATTR, getDeserializer(tDocumentClass));
    String requestJson = json(request);
    requestJson = fixSearchAfter(requestJson);

    return (SearchResponse<TDocument>) arbitraryRequest(requestJson, endpoint);
  }

  public Map<String, Object> searchAsMap(final SearchRequest request)
      throws IOException, OpenSearchException {
    final JsonEndpoint<SearchRequest, HashMap, ErrorResponse> endpoint =
        SearchRequest._ENDPOINT.withResponseDeserializer(getDeserializer(HashMap.class));

    return transport.performRequest(request, endpoint, null);
  }

  public Map<String, Object> arbitraryRequest(
      final String method, final String path, final String json)
      throws IOException, OpenSearchException {
    final JsonEndpoint<Map<String, Object>, HashMap, ErrorResponse> endpoint =
        arbitraryEndpoint(method, path, getDeserializer(HashMap.class));
    return arbitraryRequest(json, endpoint);
  }

  public CountResponse countFromJson(final String method, final String path, final String json)
      throws IOException, OpenSearchException {
    final JsonEndpoint<Map<String, Object>, CountResponse, ErrorResponse> endpoint =
        arbitraryEndpoint(method, path, getDeserializer(CountResponse.class));
    return arbitraryRequest(json, endpoint);
  }

  /**
   * Standard opensearch GetSnapshotResponse builder considers fields "total" and "remaining" to be
   * mandatory in response. However, OS server doesn't provide them, so workarounding the
   * getSnapshots request by setting them to 0.
   *
   * @param getSnapshotRequest
   * @return
   * @throws IOException
   * @throws OpenSearchException
   */
  public GetSnapshotResponse getSnapshots(final GetSnapshotRequest getSnapshotRequest)
      throws IOException, OpenSearchException {
    final JsonpMapper jsonpMapper = transport.jsonpMapper();
    final String snapshots = String.join(",", getSnapshotRequest.snapshot());
    final JsonpDeserializer<GetSnapshotResponse> deserializer =
        ObjectBuilderDeserializer.lazy(
            () -> new Builder().total(0).remaining(0),
            op ->
                op.add(
                    Builder::snapshots,
                    JsonpDeserializer.arrayDeserializer(SnapshotInfo._DESERIALIZER),
                    "snapshots"));
    final String json =
        arbitraryRequestAsString(
            "GET", format("/_snapshot/%s/%s", getSnapshotRequest.repository(), snapshots), "{}");
    final InputStream is = new ByteArrayInputStream(json.getBytes());
    try (final JsonParser parser = jsonpMapper.jsonProvider().createParser(is)) {
      return deserializer.deserialize(parser, jsonpMapper);
    }
  }

  private <R> R arbitraryRequest(
      final String json, final JsonEndpoint<Map<String, Object>, R, ErrorResponse> endpoint)
      throws IOException, OpenSearchException {
    return transport.performRequest(jsonToMap(json), endpoint, null);
  }

  public Response performRequest(final Request request) throws IOException {
    final JsonEndpoint<Map<String, Object>, Response, ErrorResponse> endpoint =
        arbitraryEndpoint(
            request.getMethod(), request.getEndpoint(), getDeserializer(Response.class));
    final Map<String, Object> entityContent =
        Optional.ofNullable(request.getEntity())
            .map(
                e -> {
                  try {
                    return jsonToMap(new String(e.getContent().readAllBytes()));
                  } catch (final IOException ex) {
                    return new HashMap<String, Object>();
                  }
                })
            .orElse(Map.of());
    return transport.performRequest(entityContent, endpoint, null);
  }

  public String arbitraryRequestAsString(final String method, final String path, final String json)
      throws IOException, OpenSearchException {
    return mapToJson(arbitraryRequest(method, path, json));
  }

  private String mapToJson(final Map<String, Object> map) throws JsonProcessingException {
    final ObjectMapper mapper = objectMapper();
    return mapper.writeValueAsString(map);
  }
}
