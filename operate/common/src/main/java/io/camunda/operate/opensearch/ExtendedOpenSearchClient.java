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
package io.camunda.operate.opensearch;

import static java.lang.String.format;
import static java.lang.String.join;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.stream.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    final boolean hasBody = !List.of("GET", "DELETE").contains(method.toUpperCase());
    return new SimpleEndpoint<>(
        request -> method, // Request method
        request -> path, // Request path
        request -> Map.of(), // Request parameters
        request -> Map.of(), // Headers
        hasBody, // Has body
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
  /*

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
    return transport.performRequest(request, endpoint, transport.options());
  }

  public Map<String, Object> arbitraryRequest(
      final String method, final String path, final String json)
      throws IOException, OpenSearchException {
    final JsonEndpoint<Map<String, Object>, HashMap, ErrorResponse> endpoint =
        arbitraryEndpoint(method, path, getDeserializer(HashMap.class));
    return arbitraryRequest(json, endpoint);
  }

  private <R> R arbitraryRequest(
      final String json, final JsonEndpoint<Map<String, Object>, R, ErrorResponse> endpoint)
      throws IOException, OpenSearchException {
    return transport.performRequest(jsonToMap(json), endpoint, transport.options());
  }
}
