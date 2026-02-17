/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.opensearch.dto.AddPolicyRequest;
import io.camunda.zeebe.exporter.opensearch.dto.BulkIndexAction;
import io.camunda.zeebe.exporter.opensearch.dto.BulkIndexResponse;
import io.camunda.zeebe.exporter.opensearch.dto.BulkIndexResponse.Error;
import io.camunda.zeebe.exporter.opensearch.dto.GetIndexStateManagementPolicyResponse;
import io.camunda.zeebe.exporter.opensearch.dto.IndexPolicyResponse;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.http.entity.EntityTemplate;
import org.opensearch.client.Request;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch.cluster.GetComponentTemplateRequest;
import org.opensearch.client.opensearch.cluster.GetComponentTemplateResponse;
import org.opensearch.client.opensearch.cluster.PutComponentTemplateRequest;
import org.opensearch.client.opensearch.cluster.PutComponentTemplateResponse;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;
import org.opensearch.client.opensearch.ism.Action;
import org.opensearch.client.opensearch.ism.ActionDelete;
import org.opensearch.client.opensearch.ism.DeletePolicyRequest;
import org.opensearch.client.opensearch.ism.DeletePolicyResponse;
import org.opensearch.client.opensearch.ism.GetPolicyRequest;
import org.opensearch.client.opensearch.ism.IsmTemplate;
import org.opensearch.client.opensearch.ism.OpenSearchIsmClient;
import org.opensearch.client.opensearch.ism.Policy;
import org.opensearch.client.opensearch.ism.PutPolicyRequest;
import org.opensearch.client.opensearch.ism.States;
import org.opensearch.client.opensearch.ism.Transition;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;

public class OpensearchClient implements AutoCloseable {
  public static final String ISM_INITIAL_STATE = "initial";
  public static final String ISM_DELETE_STATE = "delete";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final OpenSearchClient openSearchClient;
  private final RestClient client;
  private final OpensearchExporterConfiguration configuration;
  private final TemplateReader templateReader;
  private final RecordIndexRouter indexRouter;
  private final BulkIndexRequest bulkIndexRequest;

  private final OpensearchMetrics metrics;

  OpensearchClient(
      final OpensearchExporterConfiguration configuration, final MeterRegistry meterRegistry) {
    this(
        configuration,
        new BulkIndexRequest(),
        OpensearchConnector.of(configuration).createClient(),
        RestClientFactory.of(configuration),
        new RecordIndexRouter(configuration.index),
        new TemplateReader(configuration.index),
        new OpensearchMetrics(meterRegistry));
  }

  OpensearchClient(
      final OpensearchExporterConfiguration configuration,
      final MeterRegistry meterRegistry,
      final RestClient restClient) {
    this(
        configuration,
        new BulkIndexRequest(),
        OpensearchConnector.of(configuration).createClient(),
        restClient,
        new RecordIndexRouter(configuration.index),
        new TemplateReader(configuration.index),
        new OpensearchMetrics(meterRegistry));
  }

  OpensearchClient(
      final OpensearchExporterConfiguration configuration,
      final MeterRegistry meterRegistry,
      final OpenSearchClient openSearchClient,
      final RestClient restClient) {
    this(
        configuration,
        new BulkIndexRequest(),
        openSearchClient,
        restClient,
        new RecordIndexRouter(configuration.index),
        new TemplateReader(configuration.index),
        new OpensearchMetrics(meterRegistry));
  }

  OpensearchClient(
      final OpensearchExporterConfiguration configuration,
      final BulkIndexRequest bulkIndexRequest,
      final org.opensearch.client.opensearch.OpenSearchClient openSearchClient,
      final RestClient client,
      final RecordIndexRouter indexRouter,
      final TemplateReader templateReader,
      final OpensearchMetrics metrics) {
    this.configuration = configuration;
    this.bulkIndexRequest = bulkIndexRequest;
    this.openSearchClient = openSearchClient;
    this.client = client;
    this.indexRouter = indexRouter;
    this.templateReader = templateReader;
    this.metrics = metrics;
  }

  @Override
  public void close() throws IOException {
    client.close();
    openSearchClient._transport().close();
  }

  /**
   * Indexes a record to the batch of records that will be sent to Elasticsearch
   *
   * @param record the record that will be the source of the document
   * @param recordSequence the sequence number of the record
   * @return true if the record was appended to the batch, false if the record is already indexed in
   *     the batch because only one copy of the record is allowed in the batch
   */
  public boolean index(final Record<?> record, final RecordSequence recordSequence) {
    final BulkIndexAction action =
        new BulkIndexAction(
            indexRouter.indexFor(record),
            indexRouter.idFor(record),
            indexRouter.routingFor(record));
    return bulkIndexRequest.index(action, record, recordSequence);
  }

  /**
   * Flushes the bulk request to Opensearch, unless it's currently empty.
   *
   * @throws OpensearchExporterException if not all items of the bulk were flushed successfully
   */
  public void flush() {
    if (bulkIndexRequest.isEmpty()) {
      return;
    }

    metrics.recordBulkSize(bulkIndexRequest.size());
    metrics.recordBulkMemorySize(bulkIndexRequest.memoryUsageBytes());

    try (final var ignored = metrics.measureFlushDuration()) {
      exportBulk();

      // all records where flushed, create new bulk request, otherwise retry next time
      bulkIndexRequest.clear();
    } catch (final OpensearchExporterException e) {
      metrics.recordFailedFlush();
      throw e;
    }
  }

  /**
   * Returns whether the exporter should call {@link #flush()} or not.
   *
   * @return true if {@link #flush()} should be called, false otherwise
   */
  public boolean shouldFlush() {
    return bulkIndexRequest.memoryUsageBytes() >= configuration.bulk.memoryLimit
        || bulkIndexRequest.size() >= configuration.bulk.size;
  }

  /**
   * Creates an index template for the given value type, read from the resources.
   *
   * @return true if request was acknowledged
   */
  public boolean putIndexTemplate(final ValueType valueType) {
    return putIndexTemplate(valueType, VersionUtil.getVersionLowerCase());
  }

  public boolean putIndexTemplate(final ValueType valueType, final String version) {
    final String templateName = indexRouter.indexPrefixForValueType(valueType, version);
    final PutIndexTemplateRequest request =
        templateReader.getPutIndexTemplateRequest(
            templateName,
            valueType,
            indexRouter.searchPatternForValueType(valueType, version),
            indexRouter.aliasNameForValueType(valueType));

    try {
      final org.opensearch.client.opensearch.indices.PutIndexTemplateResponse response =
          openSearchClient.indices().putIndexTemplate(request);
      return response.acknowledged();
    } catch (final OpenSearchException | IOException e) {
      throw new OpensearchExporterException("Failed to put index template", e);
    }
  }

  /**
   * Creates or updates the component template on the target Opensearch. The put template request is
   * read and initialized in {@link TemplateReader#getComponentTemplatePutRequest(String)}.
   */
  public boolean putComponentTemplate() {
    final String templateName =
        String.format("%s-%s", configuration.index.prefix, VersionUtil.getVersionLowerCase());

    final PutComponentTemplateRequest request =
        templateReader.getComponentTemplatePutRequest(templateName);

    try {
      final PutComponentTemplateResponse response =
          openSearchClient.cluster().putComponentTemplate(request);
      return response.acknowledged();
    } catch (final OpenSearchException | IOException e) {
      throw new OpensearchExporterException("Failed to put component template", e);
    }
  }

  private void exportBulk() {
    final BulkIndexResponse response;
    try {
      final var request = new Request("POST", "/_bulk");
      final var body = new EntityTemplate(bulkIndexRequest);
      request.setJsonEntity(new String(body.getContent().readAllBytes()));

      response = sendRequest(request, BulkIndexResponse.class);
    } catch (final IOException e) {
      throw new OpensearchExporterException("Failed to flush bulk", e);
    }

    if (response.errors()) {
      throwCollectedBulkError(response);
    }
  }

  private void throwCollectedBulkError(final BulkIndexResponse bulkResponse) {
    final var collectedErrors = new ArrayList<String>();
    bulkResponse.items().stream()
        .flatMap(item -> Optional.ofNullable(item.index()).stream())
        .flatMap(index -> Optional.ofNullable(index.error()).stream())
        .collect(Collectors.groupingBy(Error::type))
        .forEach(
            (errorType, errors) ->
                collectedErrors.add(
                    String.format(
                        "Failed to flush %d item(s) of bulk request [type: %s, reason: %s]",
                        errors.size(), errorType, errors.get(0).reason())));

    throw new OpensearchExporterException("Failed to flush bulk request: " + collectedErrors);
  }

  Optional<GetIndexStateManagementPolicyResponse> maybeGetIndexStateManagementPolicy() {
    try {
      final GetPolicyRequest request =
          GetPolicyRequest.builder().policyId(configuration.retention.getPolicyName()).build();
      final GetIndexStateManagementPolicyResponse response =
          OpensearchIsmPolicyClientWrapper.getIsmPolicyResponse(openSearchClient.ism(), request);
      return Optional.ofNullable(response);
    } catch (final OpenSearchException | IOException e) {
      return Optional.empty();
    }
  }

  Optional<GetComponentTemplateResponse> maybeGetComponentTemplate() {
    try {
      final GetComponentTemplateRequest request =
          GetComponentTemplateRequest.builder()
              .name(
                  String.format(
                      "%s-%s", configuration.index.prefix, VersionUtil.getVersionLowerCase()))
              .build();
      final GetComponentTemplateResponse response =
          openSearchClient.cluster().getComponentTemplate(request);
      return Optional.ofNullable(response);
    } catch (final OpenSearchException | IOException e) {
      return Optional.empty();
    }
  }

  public boolean createIndexStateManagementPolicy() {
    return putIndexStateManagementPolicy(null, null);
  }

  public boolean updateIndexStateManagementPolicy(final Integer seqNo, final Integer primaryTerm) {
    return putIndexStateManagementPolicy(seqNo, primaryTerm);
  }

  public boolean deleteIndexStateManagementPolicy() {
    try {
      final DeletePolicyRequest request =
          DeletePolicyRequest.builder().policyId(configuration.retention.getPolicyName()).build();

      final DeletePolicyResponse response = openSearchClient.ism().deletePolicy(request);
      return response.result().equals(Result.Deleted);
    } catch (final IOException e) {
      throw new OpensearchExporterException("Failed to delete index state management policy", e);
    }
  }

  public boolean bulkAddISMPolicyToAllZeebeIndices() {
    try {
      final var request =
          new Request(
              "POST",
              "/_plugins/_ism/add/"
                  + configuration.index.prefix
                  + RecordIndexRouter.INDEX_DELIMITER
                  + "*");
      final var requestEntity = new AddPolicyRequest(configuration.retention.getPolicyName());
      request.setJsonEntity(MAPPER.writeValueAsString(requestEntity));
      final var response = sendRequest(request, IndexPolicyResponse.class);
      return !response.failures();
    } catch (final IOException e) {
      throw new OpensearchExporterException("Failed to add policy to indices", e);
    }
  }

  public boolean bulkRemoveISMPolicyFromAllZeebeIndices() {
    try {
      final var request =
          new Request(
              "POST",
              "/_plugins/_ism/remove/"
                  + configuration.index.prefix
                  + RecordIndexRouter.INDEX_DELIMITER
                  + "*");
      final var response = sendRequest(request, IndexPolicyResponse.class);
      return !response.failures();
    } catch (final IOException e) {
      throw new OpensearchExporterException("Failed to remove policy from indices", e);
    }
  }

  private <T> T sendRequest(final Request request, final Class<T> responseType) throws IOException {
    final var response = client.performRequest(request);
    // buffer the complete response in memory before parsing it; this will give us a better error
    // message which contains the raw response should the deserialization fail
    final var responseBody = response.getEntity().getContent().readAllBytes();
    return MAPPER.readValue(responseBody, responseType);
  }

  private boolean putIndexStateManagementPolicy(final Integer seqNo, final Integer primaryTerm) {
    try {
      final PutPolicyRequest request =
          PutPolicyRequest.of(
              req -> {
                req.policyId(configuration.retention.getPolicyName());
                req.policy(getIsmPolicy());

                if (seqNo != null) {
                  req.ifSeqNo(seqNo.longValue());
                }

                if (primaryTerm != null) {
                  req.ifPrimaryTerm(primaryTerm);
                }

                return req;
              });

      return putIndexStateManagementPolicy(request);
    } catch (final OpenSearchException | IOException e) {
      throw new OpensearchExporterException("Failed to put index state management policy", e);
    }
  }

  boolean putIndexStateManagementPolicy(final PutPolicyRequest request) throws IOException {
    // the response of a PUT request is the same as for a GET request, so we can reuse the
    // response
    final GetIndexStateManagementPolicyResponse response =
        OpensearchIsmPolicyClientWrapper.putIsmPolicyResponse(openSearchClient.ism(), request);
    return response.policy() != null;
  }

  private Policy getIsmPolicy() {
    final States initialState =
        States.builder()
            .name(ISM_INITIAL_STATE)
            .transitions(
                Transition.builder()
                    .stateName(ISM_DELETE_STATE)
                    .conditions(
                        "min_index_age", JsonData.of(configuration.retention.getMinimumAge()))
                    .build())
            .build();
    final States deleteState =
        States.builder()
            .name(ISM_DELETE_STATE)
            .actions(Action.builder().delete(ActionDelete._INSTANCE).build())
            .build();

    final IsmTemplate ismTemplate =
        IsmTemplate.builder().indexPatterns(configuration.index.prefix + RecordIndexRouter.INDEX_DELIMITER +"*").priority(1).build();

    return Policy.builder()
        .policyId(configuration.retention.getPolicyName())
        .description(configuration.retention.getPolicyDescription())
        .defaultState(ISM_INITIAL_STATE)
        .states(List.of(initialState, deleteState))
        .ismTemplate(ismTemplate)
        .build();
  }

  private static final class OpensearchIsmPolicyClientWrapper {

    public static final Endpoint<
            GetPolicyRequest, GetIndexStateManagementPolicyResponse, ErrorResponse>
        GET_ENDPOINT =
            new SimpleEndpoint<>(
                // Request method
                request -> "GET",
                // Request path
                request -> {
                  final StringBuilder buf = new StringBuilder();
                  buf.append("/_plugins/_ism/policies/");
                  SimpleEndpoint.pathEncode(request.policyId(), buf);
                  return buf.toString();
                },
                SimpleEndpoint.emptyMap(), // query parameters, none used
                SimpleEndpoint.emptyMap(), // headers, none used
                false,
                GetIndexStateManagementPolicyResponse.DESERIALIZER);

    public static final Endpoint<
            PutPolicyRequest, GetIndexStateManagementPolicyResponse, ErrorResponse>
        PUT_ENDPOINT =
            new SimpleEndpoint<>(
                // Request method
                request -> "PUT",
                // Request path
                request -> {
                  final StringBuilder buf = new StringBuilder();
                  buf.append("/_plugins/_ism/policies/");
                  SimpleEndpoint.pathEncode(request.policyId(), buf);
                  return buf.toString();
                },
                request -> {
                  // add seq_no and primary_term as query parameters
                  final var params = new HashMap<String, String>();
                  if (request.ifSeqNo() != null) {
                    params.put("if_seq_no", request.ifSeqNo().toString());
                  }
                  if (request.ifPrimaryTerm() != null) {
                    params.put("if_primary_term", request.ifPrimaryTerm().toString());
                  }
                  return params;
                },
                SimpleEndpoint.emptyMap(), // headers, none used
                true, // include request body in request
                // the response is the same as for the GET request, so we can reuse the deserializer
                GetIndexStateManagementPolicyResponse.DESERIALIZER);

    public static GetIndexStateManagementPolicyResponse getIsmPolicyResponse(
        final OpenSearchIsmClient openSearchIsmClient, final GetPolicyRequest request)
        throws IOException {
      return openSearchIsmClient
          ._transport()
          .performRequest(request, GET_ENDPOINT, openSearchIsmClient._transportOptions());
    }

    public static GetIndexStateManagementPolicyResponse putIsmPolicyResponse(
        final OpenSearchIsmClient openSearchIsmClient, final PutPolicyRequest request)
        throws IOException {
      return openSearchIsmClient
          ._transport()
          .performRequest(request, PUT_ENDPOINT, openSearchIsmClient._transportOptions());
    }
  }
}
