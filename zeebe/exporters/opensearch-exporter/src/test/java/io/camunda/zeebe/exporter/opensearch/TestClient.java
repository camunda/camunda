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
import io.camunda.zeebe.exporter.opensearch.dto.GetIndexStateManagementPolicyResponse;
import io.camunda.zeebe.exporter.opensearch.dto.Template;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.Metrics;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.agrona.CloseHelper;
import org.opensearch.client.Request;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.indices.GetIndexTemplateRequest;
import org.opensearch.client.opensearch.indices.GetIndexTemplateResponse;
import org.opensearch.client.opensearch.indices.get_index_template.IndexTemplateItem;
import org.opensearch.client.opensearch.ism.Action;
import org.opensearch.client.opensearch.ism.ActionDelete;
import org.opensearch.client.opensearch.ism.IsmTemplate;
import org.opensearch.client.opensearch.ism.Policy;
import org.opensearch.client.opensearch.ism.PutPolicyRequest;
import org.opensearch.client.opensearch.ism.States;
import org.opensearch.client.opensearch.ism.Transition;

/**
 * A thin client to verify properties from Opensearch. Wraps both the low and high level clients
 * from Opensearch in a closeable resource.
 */
final class TestClient implements CloseableSilently {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new ZeebeProtocolModule());
  final OpensearchClient opensearchClient;
  private final OpensearchExporterConfiguration config;
  private final RestClient restClient;
  private final OpenSearchClient osClient;
  private final RecordIndexRouter indexRouter;

  TestClient(final OpensearchExporterConfiguration config, final RecordIndexRouter indexRouter) {
    this(
        config,
        indexRouter,
        RestClientFactory.of(config, true),
        OpensearchConnector.of(config.withObjectMapper(MAPPER)).createClient());
  }

  TestClient(
      final OpensearchExporterConfiguration config,
      final RecordIndexRouter indexRouter,
      final RestClient restClient,
      final OpenSearchClient osClient) {
    this.config = config;
    this.indexRouter = indexRouter;
    this.restClient = restClient;
    this.osClient = osClient;

    opensearchClient = new OpensearchClient(config, Metrics.globalRegistry, osClient, restClient);
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

  Optional<IndexTemplateItem> getIndexTemplate(final ValueType valueType, final String version) {
    try {
      final GetIndexTemplateRequest request =
          GetIndexTemplateRequest.builder()
              .name(indexRouter.indexPrefixForValueType(valueType, version))
              .build();
      final GetIndexTemplateResponse response = osClient.indices().getIndexTemplate(request);
      return response.indexTemplates().stream().findFirst();
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

  Optional<GetIndexStateManagementPolicyResponse> getIndexStateManagementPolicy() {
    return opensearchClient.getIndexStateManagementPolicy();
  }

  void putIndexStateManagementPolicy(final String minimumAge) {
    try {
      final PutPolicyRequest request =
          PutPolicyRequest.of(
              req -> {
                req.policyId(config.retention.getPolicyName());
                req.policy(getIsmPolicy(minimumAge));
                decoratePolicyVersionSeq(req);
                return req;
              });

      opensearchClient.putIndexStateManagementPolicy(request);
    } catch (final IOException e) {
      throw new OpensearchExporterException("Failed to put index state management policy", e);
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

  private void decoratePolicyVersionSeq(final PutPolicyRequest.Builder builder) {
    try {
      final Optional<GetIndexStateManagementPolicyResponse> maybePolicy =
          getIndexStateManagementPolicy();
      if (maybePolicy.isEmpty()) {
        return;
      }
      final GetIndexStateManagementPolicyResponse policy = maybePolicy.get();
      builder.ifSeqNo(policy.seqNo().longValue());
      builder.ifPrimaryTerm(policy.primaryTerm());
    } catch (final Exception e) {
      if (!(e.getCause() instanceof final ResponseException responseException
          && responseException.getResponse().getStatusLine().getStatusCode() == 404)) {
        throw e;
      }
    }
  }

  private Policy getIsmPolicy(final String minimumAge) {
    final States initialState =
        States.builder()
            .name(ISM_INITIAL_STATE)
            .transitions(
                Transition.builder()
                    .stateName(ISM_DELETE_STATE)
                    .conditions("min_index_age", JsonData.of(minimumAge))
                    .build())
            .build();
    final States deleteState =
        States.builder()
            .name(ISM_DELETE_STATE)
            .actions(Action.builder().delete(ActionDelete._INSTANCE).build())
            .build();

    final IsmTemplate ismTemplate =
        IsmTemplate.builder().indexPatterns(config.index.prefix + "*").priority(1).build();

    return Policy.builder()
        .policyId(config.retention.getPolicyName())
        .description(config.retention.getPolicyDescription())
        .defaultState(ISM_INITIAL_STATE)
        .states(List.of(initialState, deleteState))
        .ismTemplate(ismTemplate)
        .build();
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
