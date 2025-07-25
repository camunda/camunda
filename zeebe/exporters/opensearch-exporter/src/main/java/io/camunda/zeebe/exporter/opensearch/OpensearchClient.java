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
import io.camunda.zeebe.exporter.opensearch.dto.DeleteStateManagementPolicyResponse;
import io.camunda.zeebe.exporter.opensearch.dto.GetIndexStateManagementPolicyResponse;
import io.camunda.zeebe.exporter.opensearch.dto.IndexPolicyResponse;
import io.camunda.zeebe.exporter.opensearch.dto.PutIndexStateManagementPolicyRequest;
import io.camunda.zeebe.exporter.opensearch.dto.PutIndexStateManagementPolicyRequest.Policy;
import io.camunda.zeebe.exporter.opensearch.dto.PutIndexStateManagementPolicyRequest.Policy.IsmTemplate;
import io.camunda.zeebe.exporter.opensearch.dto.PutIndexStateManagementPolicyRequest.Policy.State;
import io.camunda.zeebe.exporter.opensearch.dto.PutIndexStateManagementPolicyRequest.Policy.State.Action;
import io.camunda.zeebe.exporter.opensearch.dto.PutIndexStateManagementPolicyRequest.Policy.State.DeleteAction;
import io.camunda.zeebe.exporter.opensearch.dto.PutIndexStateManagementPolicyRequest.Policy.State.Transition;
import io.camunda.zeebe.exporter.opensearch.dto.PutIndexStateManagementPolicyRequest.Policy.State.Transition.Conditions;
import io.camunda.zeebe.exporter.opensearch.dto.PutIndexStateManagementPolicyResponse;
import io.camunda.zeebe.exporter.opensearch.dto.PutIndexTemplateResponse;
import io.camunda.zeebe.exporter.opensearch.dto.Template;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.http.entity.EntityTemplate;
import org.opensearch.client.Request;
import org.opensearch.client.RestClient;

public class OpensearchClient implements AutoCloseable {
  public static final String ISM_INITIAL_STATE = "initial";
  public static final String ISM_DELETE_STATE = "delete";
  private static final ObjectMapper MAPPER = new ObjectMapper();
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
        restClient,
        new RecordIndexRouter(configuration.index),
        new TemplateReader(configuration.index),
        new OpensearchMetrics(meterRegistry));
  }

  OpensearchClient(
      final OpensearchExporterConfiguration configuration,
      final BulkIndexRequest bulkIndexRequest,
      final RestClient client,
      final RecordIndexRouter indexRouter,
      final TemplateReader templateReader,
      final OpensearchMetrics metrics) {
    this.configuration = configuration;
    this.bulkIndexRequest = bulkIndexRequest;
    this.client = client;
    this.indexRouter = indexRouter;
    this.templateReader = templateReader;
    this.metrics = metrics;
  }

  @Override
  public void close() throws IOException {
    client.close();
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
    final Template template =
        templateReader.readIndexTemplate(
            valueType,
            indexRouter.searchPatternForValueType(valueType, version),
            indexRouter.aliasNameForValueType(valueType));

    return putIndexTemplate(templateName, template);
  }

  /**
   * Creates or updates the component template on the target Opensearch. The template is read from
   * {@link TemplateReader#readComponentTemplate()}.
   */
  public boolean putComponentTemplate() {
    final Template template = templateReader.readComponentTemplate();
    return putComponentTemplate(template);
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

  private boolean putIndexTemplate(final String templateName, final Template template) {
    try {
      final var request = new Request("PUT", "/_index_template/" + templateName);
      request.setJsonEntity(MAPPER.writeValueAsString(template));

      final var response = sendRequest(request, PutIndexTemplateResponse.class);
      return response.acknowledged();
    } catch (final IOException e) {
      throw new OpensearchExporterException("Failed to put index template", e);
    }
  }

  private boolean putComponentTemplate(final Template template) {
    try {
      final var request =
          new Request(
              "PUT",
              "/_component_template/"
                  + configuration.index.prefix
                  + "-"
                  + VersionUtil.getVersionLowerCase());
      request.setJsonEntity(MAPPER.writeValueAsString(template));

      final var response = sendRequest(request, PutIndexTemplateResponse.class);
      return response.acknowledged();
    } catch (final IOException e) {
      throw new OpensearchExporterException("Failed to put component template", e);
    }
  }

  Optional<GetIndexStateManagementPolicyResponse> getIndexStateManagementPolicy() {
    try {
      final var request =
          new Request("GET", "/_plugins/_ism/policies/" + configuration.retention.getPolicyName());
      return Optional.of(sendRequest(request, GetIndexStateManagementPolicyResponse.class));
    } catch (final IOException e) {
      return Optional.empty();
    }
  }

  public boolean createIndexStateManagementPolicy() {
    return putIndexStateManagementPolicy(Collections.emptyMap());
  }

  public boolean updateIndexStateManagementPolicy(final Integer seqNo, final Integer primaryTerm) {
    final var queryParameters =
        Map.of("if_seq_no", seqNo.toString(), "if_primary_term", primaryTerm.toString());
    return putIndexStateManagementPolicy(queryParameters);
  }

  public boolean deleteIndexStateManagementPolicy() {
    try {
      final var request =
          new Request(
              "DELETE", "/_plugins/_ism/policies/" + configuration.retention.getPolicyName());

      final var response = sendRequest(request, DeleteStateManagementPolicyResponse.class);
      return response.result().equals(DeleteStateManagementPolicyResponse.DELETED);
    } catch (final IOException e) {
      throw new OpensearchExporterException("Failed to delete index state management policy", e);
    }
  }

  private boolean putIndexStateManagementPolicy(final Map<String, String> queryParameters) {
    try {
      final var request =
          new Request("PUT", "/_plugins/_ism/policies/" + configuration.retention.getPolicyName());

      queryParameters.forEach(request::addParameter);

      final var requestEntity = createPutIndexManagementPolicyRequest();
      request.setJsonEntity(MAPPER.writeValueAsString(requestEntity));

      final var response = sendRequest(request, PutIndexStateManagementPolicyResponse.class);
      return response.policy() != null;
    } catch (final IOException e) {
      throw new OpensearchExporterException("Failed to put index state management policy", e);
    }
  }

  public boolean bulkAddISMPolicyToAllZeebeIndices() {
    try {
      final var request =
          new Request("POST", "/_plugins/_ism/add/" + configuration.index.prefix + "*");
      final var requestEntity = new AddPolicyRequest(configuration.retention.getPolicyName());
      request.setJsonEntity(MAPPER.writeValueAsString(requestEntity));
      final var response = sendRequest(request, IndexPolicyResponse.class);
      return !response.failures();
    } catch (final IOException e) {
      throw new OpensearchExporterException("Failed to add policy to indices", e);
    }
  }

  public boolean bulkRemoveISMPolicyToAllZeebeIndices() {
    try {
      final var request =
          new Request("POST", "/_plugins/_ism/remove/" + configuration.index.prefix + "*");
      final var response = sendRequest(request, IndexPolicyResponse.class);
      return !response.failures();
    } catch (final IOException e) {
      throw new OpensearchExporterException("Failed to remove policy from indices", e);
    }
  }

  private PutIndexStateManagementPolicyRequest createPutIndexManagementPolicyRequest() {
    final var initialState =
        new State(
            ISM_INITIAL_STATE,
            Collections.emptyList(),
            List.of(
                new Transition(
                    ISM_DELETE_STATE, new Conditions(configuration.retention.getMinimumAge()))));
    final var deleteState =
        new State(
            ISM_DELETE_STATE, List.of(new Action(new DeleteAction())), Collections.emptyList());
    final var policy =
        new Policy(
            configuration.retention.getPolicyDescription(),
            ISM_INITIAL_STATE,
            List.of(initialState, deleteState),
            new IsmTemplate(List.of(configuration.index.prefix + "*"), 1));
    return new PutIndexStateManagementPolicyRequest(policy);
  }

  private <T> T sendRequest(final Request request, final Class<T> responseType) throws IOException {
    final var response = client.performRequest(request);
    // buffer the complete response in memory before parsing it; this will give us a better error
    // message which contains the raw response should the deserialization fail
    final var responseBody = response.getEntity().getContent().readAllBytes();
    return MAPPER.readValue(responseBody, responseType);
  }
}
