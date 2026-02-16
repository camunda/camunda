/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import static io.camunda.zeebe.exporter.opensearch.OpensearchClient.ISM_DELETE_STATE;
import static io.camunda.zeebe.exporter.opensearch.OpensearchClient.ISM_INITIAL_STATE;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.opensearch.TestClient.ComponentTemplatesDto.ComponentTemplateWrapper;
import io.camunda.zeebe.exporter.opensearch.TestClient.IndexTemplatesDto.IndexTemplateWrapper;
import io.camunda.zeebe.exporter.opensearch.dto.GetIndexStateManagementPolicyResponse;
import io.camunda.zeebe.exporter.opensearch.dto.PutIndexStateManagementPolicyRequest;
import io.camunda.zeebe.exporter.opensearch.dto.PutIndexStateManagementPolicyRequest.Policy;
import io.camunda.zeebe.exporter.opensearch.dto.PutIndexStateManagementPolicyRequest.Policy.IsmTemplate;
import io.camunda.zeebe.exporter.opensearch.dto.PutIndexStateManagementPolicyRequest.Policy.State;
import io.camunda.zeebe.exporter.opensearch.dto.PutIndexStateManagementPolicyRequest.Policy.State.Action;
import io.camunda.zeebe.exporter.opensearch.dto.PutIndexStateManagementPolicyRequest.Policy.State.DeleteAction;
import io.camunda.zeebe.exporter.opensearch.dto.PutIndexStateManagementPolicyRequest.Policy.State.Transition;
import io.camunda.zeebe.exporter.opensearch.dto.PutIndexStateManagementPolicyRequest.Policy.State.Transition.Conditions;
import io.camunda.zeebe.exporter.opensearch.dto.Template;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import org.agrona.CloseHelper;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.opensearch.client.Request;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

/**
 * A thin client to verify properties from Opensearch. Wraps both the low and high level clients
 * from Opensearch in a closeable resource.
 */
final class TestClient implements CloseableSilently {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new ZeebeProtocolModule());

  private final OpensearchExporterConfiguration config;
  private final RestClient restClient;
  private final OpenSearchClient osClient;
  private final RecordIndexRouter indexRouter;

  TestClient(final OpensearchExporterConfiguration config, final RecordIndexRouter indexRouter) {
    this(config, indexRouter, RestClientFactory.of(config, true));
  }

  TestClient(
      final OpensearchExporterConfiguration config,
      final RecordIndexRouter indexRouter,
      final RestClient restClient) {
    this.config = config;
    this.indexRouter = indexRouter;
    this.restClient = restClient;
    osClient = new OpenSearchClient(createTransport(config));
  }

  @SuppressWarnings("rawtypes")
  GetResponse<Record> getExportedDocumentFor(final Record<?> record) {
    final var indexName = indexRouter.indexFor(record);

    try {
      osClient.indices().refresh(b -> b.index(indexName)); // ensure latest data is visible
      return osClient.get(b -> b.id(indexRouter.idFor(record)).index(indexName), Record.class);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  Optional<IndexTemplateWrapper> getIndexTemplate(final ValueType valueType, final String version) {
    try {
      final var request =
          new Request(
              "GET", "/_index_template/" + indexRouter.indexPrefixForValueType(valueType, version));
      final var response = restClient.performRequest(request);
      final var templates =
          MAPPER.readValue(response.getEntity().getContent(), IndexTemplatesDto.class);
      return templates.wrappers().stream().findFirst();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  Optional<ComponentTemplateWrapper> getComponentTemplate() {
    try {
      final var request =
          new Request(
              "GET",
              "/_component_template/"
                  + config.index.prefix
                  + "-"
                  + VersionUtil.getVersionLowerCase());
      final var response = restClient.performRequest(request);
      final var templates =
          MAPPER.readValue(response.getEntity().getContent(), ComponentTemplatesDto.class);
      return templates.wrappers().stream().findFirst();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  GetIndexStateManagementPolicyResponse getIndexStateManagementPolicy() {
    try {
      final var request =
          new Request("GET", "/_plugins/_ism/policies/" + config.retention.getPolicyName());
      final var response = restClient.performRequest(request);
      return MAPPER.readValue(
          response.getEntity().getContent(), GetIndexStateManagementPolicyResponse.class);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  void putIndexStateManagementPolicy(final String minimumAge) {
    try {
      final var request =
          new Request("PUT", "/_plugins/_ism/policies/" + config.retention.getPolicyName());
      decoratePolicyVersionSeq(request);
      final var requestEntity = createPutIndexManagementPolicyRequest(minimumAge);
      request.setJsonEntity(MAPPER.writeValueAsString(requestEntity));
      restClient.performRequest(request);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  void putUser(final String username, final String password, final List<String> roles) {
    try {
      final var request = new Request("PUT", "/_plugins/_security/api/internalusers/" + username);
      final var putUserRequest = new PutUserRequest(password, roles);
      request.setJsonEntity(MAPPER.writeValueAsString(putUserRequest));
      restClient.performRequest(request);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  OpenSearchClient getOsClient() {
    return osClient;
  }

  @Override
  public void close() {
    CloseHelper.quietCloseAll(osClient._transport());
  }

  private void decoratePolicyVersionSeq(final Request request) {
    try {
      final GetIndexStateManagementPolicyResponse policy = getIndexStateManagementPolicy();
      request.addParameter("if_seq_no", String.valueOf(policy.seqNo()));
      request.addParameter("if_primary_term", String.valueOf(policy.primaryTerm()));
    } catch (final Exception e) {
      if (!(e.getCause() instanceof final ResponseException responseException
          && responseException.getResponse().getStatusLine().getStatusCode() == 404)) {
        throw e;
      }
    }
  }

  private PutIndexStateManagementPolicyRequest createPutIndexManagementPolicyRequest(
      final String minimumAge) {
    final var initialState =
        new State(
            ISM_INITIAL_STATE,
            Collections.emptyList(),
            List.of(new Transition(ISM_DELETE_STATE, new Conditions(minimumAge))));
    final var deleteState =
        new State(
            ISM_DELETE_STATE, List.of(new Action(new DeleteAction())), Collections.emptyList());
    final var policy =
        new Policy(
            config.retention.getPolicyDescription(),
            ISM_INITIAL_STATE,
            List.of(initialState, deleteState),
            new IsmTemplate(List.of(config.index.prefix + "*"), 1));
    return new PutIndexStateManagementPolicyRequest(policy);
  }

  public Optional<IndexISMPolicyDto> explainIndex(final String index) {
    try {
      final var request = new Request("GET", "_plugins/_ism/explain/" + index);
      final var response = restClient.performRequest(request);
      final TypeReference<Map<String, Object>> mapTypeReference = new TypeReference<>() {};
      final Map<String, Object> output =
          MAPPER.readValue(response.getEntity().getContent(), mapTypeReference);
      final var policy =
          output.values().stream()
              .filter(Map.class::isInstance)
              .map(v -> (Map<String, String>) v)
              .findFirst();

      return policy
          .map(p -> p.get("index.opendistro.index_state_management.policy_id"))
          .map(IndexISMPolicyDto::new);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  void deleteIndices() {
    try {
      final var request = new Request("DELETE", config.index.prefix + "*?expand_wildcards=all");
      restClient.performRequest(request);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  void deleteIndexTemplates() {
    try {
      final var request =
          new Request("DELETE", "/_index_template/%s*".formatted(config.index.prefix));
      restClient.performRequest(request);
    } catch (final IOException e) {
      if (e.getMessage() != null && e.getMessage().contains("404 Not Found")) {
        // Ignore 404 errors - no templates to delete
        return;
      }
      throw new UncheckedIOException(e);
    }
  }

  void deleteComponentTemplates() {
    try {
      final var request =
          new Request("DELETE", "/_component_template/%s*".formatted(config.index.prefix));
      restClient.performRequest(request);
    } catch (final IOException e) {
      if (e.getMessage() != null && e.getMessage().contains("404 Not Found")) {
        // Ignore 404 errors - no templates to delete
        return;
      }
      throw new UncheckedIOException(e);
    }
  }

  void deleteIndicesByPattern(final String pattern) {
    try {
      final var request = new Request("DELETE", pattern + "?expand_wildcards=all");
      restClient.performRequest(request);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private OpenSearchTransport createTransport(final OpensearchExporterConfiguration config) {
    try {
      final SSLContext sslContext =
          SSLContextBuilder.create().loadTrustMaterial(null, new TrustAllStrategy()).build();
      final TlsStrategy tlsStrategy =
          ClientTlsStrategyBuilder.create().setSslContext(sslContext).buildAsync();
      final var credentialsProvider =
          new org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider();
      if (config.hasAuthenticationPresent()) {
        credentialsProvider.setCredentials(
            new org.apache.hc.client5.http.auth.AuthScope(null, -1),
            new org.apache.hc.client5.http.auth.UsernamePasswordCredentials(
                config.getAuthentication().getUsername(),
                config.getAuthentication().getPassword().toCharArray()));
      }
      final PoolingAsyncClientConnectionManager connectionManager =
          PoolingAsyncClientConnectionManagerBuilder.create().setTlsStrategy(tlsStrategy).build();
      return ApacheHttpClient5TransportBuilder.builder(HttpHost.create(config.url))
          .setMapper(new JacksonJsonpMapper(MAPPER))
          .setHttpClientConfigCallback(
              httpClientBuilder ->
                  httpClientBuilder
                      .setConnectionManager(connectionManager)
                      .setDefaultCredentialsProvider(credentialsProvider))
          .build();
    } catch (final URISyntaxException
        | NoSuchAlgorithmException
        | KeyStoreException
        | KeyManagementException e) {
      throw new RuntimeException(e);
    }
  }

  record IndexTemplatesDto(@JsonProperty("index_templates") List<IndexTemplateWrapper> wrappers) {
    record IndexTemplateWrapper(String name, @JsonProperty("index_template") Template template) {}
  }

  record ComponentTemplatesDto(
      @JsonProperty("component_templates") List<ComponentTemplateWrapper> wrappers) {
    record ComponentTemplateWrapper(
        String name, @JsonProperty("component_template") Template template) {}
  }

  record IndexISMPolicyDto(String policyId) {}

  @JsonInclude(Include.NON_EMPTY)
  private record PutUserRequest(
      String password, @JsonProperty("backend_roles") List<String> roles) {}
}
