/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.CancelProcessInstanceRequest;
import io.camunda.gateway.protocol.model.DeleteProcessInstanceRequest;
import io.camunda.gateway.protocol.model.IncidentSearchQuery;
import io.camunda.gateway.protocol.model.IncidentSearchQueryResult;
import io.camunda.gateway.protocol.model.ProcessInstanceCancellationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceDeletionBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceIncidentResolutionBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceSearchQuery;
import io.camunda.gateway.protocol.model.ProcessInstanceSearchQueryResult;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.IncidentServices;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCancelRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateBatchOperationRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyBatchOperationRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyRequest;
import io.camunda.service.VariableServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@CamundaRestController
@RequestMapping("/v2/process-instances")
public class ProcessInstanceController {

  static final String EXPORT_TRUNCATED_HEADER = "X-Camunda-Export-Truncated";
  private static final String CSV_CONTENT_TYPE = "text/csv; charset=UTF-8";
  private static final DateTimeFormatter FILENAME_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC);
  private static final Logger LOG = LoggerFactory.getLogger(ProcessInstanceController.class);
  private static final ObjectMapper EXPORT_JSON = new ObjectMapper();

  private final ProcessInstanceServices processInstanceServices;
  private final IncidentServices incidentServices;
  private final VariableServices variableServices;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final GatewayRestConfiguration gatewayRestConfiguration;

  public ProcessInstanceController(
      final ProcessInstanceServices processInstanceServices,
      final IncidentServices incidentServices,
      final VariableServices variableServices,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider,
      final GatewayRestConfiguration gatewayRestConfiguration) {
    this.processInstanceServices = processInstanceServices;
    this.incidentServices = incidentServices;
    this.variableServices = variableServices;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
    this.gatewayRestConfiguration = gatewayRestConfiguration;
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createProcessInstance(
      @RequestBody final ProcessInstanceCreationInstruction request) {
    return RequestMapper.toCreateProcessInstance(request, multiTenancyCfg.isChecksEnabled())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createProcessInstance);
  }

  @CamundaPostMapping(path = "/{processInstanceKey}/cancellation")
  public CompletableFuture<ResponseEntity<Object>> cancelProcessInstance(
      @PathVariable final long processInstanceKey,
      @RequestBody(required = false) final CancelProcessInstanceRequest cancelRequest) {
    return RequestMapper.toCancelProcessInstance(processInstanceKey, cancelRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::cancelProcessInstance);
  }

  @CamundaPostMapping(path = "/{processInstanceKey}/migration")
  public CompletableFuture<ResponseEntity<Object>> migrateProcessInstance(
      @PathVariable final long processInstanceKey,
      @RequestBody final ProcessInstanceMigrationInstruction migrationRequest) {
    return RequestMapper.toMigrateProcessInstance(processInstanceKey, migrationRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::migrateProcessInstance);
  }

  @CamundaPostMapping(path = "/{processInstanceKey}/modification")
  public CompletableFuture<ResponseEntity<Object>> modifyProcessInstance(
      @PathVariable final long processInstanceKey,
      @RequestBody final ProcessInstanceModificationInstruction modifyRequest) {
    return RequestMapper.toModifyProcessInstance(processInstanceKey, modifyRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::modifyProcessInstance);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{processInstanceKey}/incident-resolution")
  public CompletableFuture<ResponseEntity<Object>> resolveProcessInstanceIncidents(
      @PathVariable final long processInstanceKey) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices.resolveProcessInstanceIncidents(
                processInstanceKey, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<ProcessInstanceSearchQueryResult> searchProcessInstances(
      @RequestBody(required = false) final ProcessInstanceSearchQuery query) {
    return SearchQueryRequestMapper.toProcessInstanceQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  /**
   * Streams the process-instance set matching {@code query} as a UTF-8 CSV file. Designed for
   * business reporting (Excel, Power BI, BO) — the request body is identical to {@code /search} so
   * clients reuse their existing filter/sort code. Pagination is server-driven; the {@code
   * page.limit} field on the request body is ignored. The hard row cap is configurable via {@code
   * camunda.rest.process-instance-export.max-rows}; when reached, the response body still contains
   * a complete CSV but the {@code X-Camunda-Export-Truncated: true} header is set so clients can
   * surface a refine-your-filter hint.
   */
  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search.csv", produces = "text/csv")
  public ResponseEntity<StreamingResponseBody> exportProcessInstancesCsv(
      @RequestBody(required = false) final ProcessInstanceSearchQuery query) {
    return SearchQueryRequestMapper.toProcessInstanceQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::exportCsv);
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{processInstanceKey}")
  public ResponseEntity<Object> getByKey(
      @PathVariable("processInstanceKey") final Long processInstanceKey) {
    try {
      // Success case: Return the left side with the ProcessInstanceItem wrapped in ResponseEntity
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toProcessInstance(
                  processInstanceServices.getByKey(
                      processInstanceKey, authenticationProvider.getCamundaAuthentication())));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{processInstanceKey}/deletion")
  public CompletableFuture<ResponseEntity<Object>> deleteProcessInstance(
      @PathVariable("processInstanceKey") final Long processInstanceKey,
      @RequestBody(required = false) final DeleteProcessInstanceRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices.deleteProcessInstance(
                processInstanceKey,
                Objects.nonNull(request) ? request.getOperationReference() : null,
                authenticationProvider.getCamundaAuthentication()));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{processInstanceKey}/call-hierarchy")
  public ResponseEntity<Object> getCallHierarchy(
      @PathVariable("processInstanceKey") final Long processInstanceKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toProcessInstanceCallHierarchyEntries(
                  processInstanceServices.callHierarchy(
                      processInstanceKey, authenticationProvider.getCamundaAuthentication())));

    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{processInstanceKey}/statistics/element-instances")
  public ResponseEntity<Object> elementStatistics(
      @PathVariable("processInstanceKey") final Long processInstanceKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toProcessInstanceElementStatisticsResult(
                  processInstanceServices.elementStatistics(
                      processInstanceKey, authenticationProvider.getCamundaAuthentication())));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{processInstanceKey}/sequence-flows")
  public ResponseEntity<Object> sequenceFlows(
      @PathVariable("processInstanceKey") final Long processInstanceKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toSequenceFlowsResult(
                  processInstanceServices.sequenceFlows(
                      processInstanceKey, authenticationProvider.getCamundaAuthentication())));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/cancellation")
  public CompletableFuture<ResponseEntity<Object>> cancelProcessInstancesBatchOperation(
      @RequestBody final ProcessInstanceCancellationBatchOperationRequest request) {
    return RequestMapper.toRequiredProcessInstanceFilter(request.getFilter())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::batchOperationCancellation);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/incident-resolution")
  public CompletableFuture<ResponseEntity<Object>> resolveIncidentsBatchOperation(
      @RequestBody final ProcessInstanceIncidentResolutionBatchOperationRequest request) {
    return RequestMapper.toRequiredProcessInstanceFilter(request.getFilter())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::batchOperationResolveIncidents);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/migration")
  public CompletableFuture<ResponseEntity<Object>> migrateProcessInstancesBatchOperation(
      @RequestBody final ProcessInstanceMigrationBatchOperationRequest request) {
    return RequestMapper.toProcessInstanceMigrationBatchOperationRequest(request)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::batchOperationMigrate);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/modification")
  public CompletableFuture<ResponseEntity<Object>> modifyProcessInstancesBatchOperation(
      @RequestBody final ProcessInstanceModificationBatchOperationRequest request) {
    return RequestMapper.toProcessInstanceModifyBatchOperationRequest(request)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::batchOperationModify);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/deletion")
  public CompletableFuture<ResponseEntity<Object>> deleteProcessInstancesBatchOperation(
      @RequestBody final ProcessInstanceDeletionBatchOperationRequest request) {
    return RequestMapper.toRequiredProcessInstanceFilter(request.getFilter())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::batchOperationDeletion);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{processInstanceKey}/incidents/search")
  public ResponseEntity<IncidentSearchQueryResult> searchIncidents(
      @PathVariable("processInstanceKey") final long processInstanceKey,
      @RequestBody(required = false) final IncidentSearchQuery query) {
    return SearchQueryRequestMapper.toIncidentQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            incidentQuery -> searchIncidents(processInstanceKey, incidentQuery));
  }

  private ResponseEntity<ProcessInstanceSearchQueryResult> search(
      final ProcessInstanceQuery query) {
    try {
      final var result =
          processInstanceServices.search(query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toProcessInstanceSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<StreamingResponseBody> exportCsv(final ProcessInstanceQuery query) {
    final var auth = authenticationProvider.getCamundaAuthentication();
    final var exportCfg = gatewayRestConfiguration.getProcessInstanceExport();
    final int maxRows = exportCfg.getMaxRows();
    final int pageSize = exportCfg.getPageSize();
    final boolean tenantColumn = multiTenancyCfg.isChecksEnabled();

    // Probe total upfront so the truncation header can be set before the response body starts
    // streaming. HTTP headers cannot be added once StreamingResponseBody has begun writing.
    final boolean truncated;
    try {
      final var probe =
          processInstanceServices.search(
              new ProcessInstanceQuery(
                  query.filter(),
                  query.sort(),
                  SearchQueryPage.NO_ENTITIES_QUERY,
                  query.resultConfig()),
              auth);
      truncated = probe.hasMoreTotalItems() || probe.total() > maxRows;
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }

    final String filename =
        "process-instances-" + FILENAME_TIMESTAMP.format(Instant.now()) + ".csv";
    final StreamingResponseBody body =
        out -> {
          try (var writer = ProcessInstanceCsvWriter.open(out, tenantColumn)) {
            writer.writeHeader();
            processInstanceServices.streamSearch(
                query, auth, page -> writeEnrichedPage(writer, page, auth), maxRows, pageSize);
          }
        };

    final var builder =
        ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, CSV_CONTENT_TYPE)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
    if (truncated) {
      builder.header(EXPORT_TRUNCATED_HEADER, "true");
    }
    return builder.body(body);
  }

  private void writeEnrichedPage(
      final ProcessInstanceCsvWriter writer,
      final List<ProcessInstanceEntity> page,
      final CamundaAuthentication auth) {
    final List<Long> keys =
        page.stream().map(ProcessInstanceEntity::processInstanceKey).toList();
    final Map<Long, String> incidentMessages = fetchActiveIncidentMessages(keys, auth);
    final Map<Long, String> variablesJson = fetchVariablesAsJson(keys, auth);
    for (final var entity : page) {
      final Long key = entity.processInstanceKey();
      writer.writeRow(
          entity, incidentMessages.getOrDefault(key, ""), variablesJson.getOrDefault(key, ""));
    }
  }

  private Map<Long, String> fetchActiveIncidentMessages(
      final List<Long> keys, final CamundaAuthentication auth) {
    if (keys.isEmpty()) {
      return Map.of();
    }
    try {
      final var result =
          incidentServices.search(
              IncidentQuery.of(
                  b ->
                      b.filter(
                              f ->
                                  f.processInstanceKeyOperations(Operation.in(keys))
                                      .states(IncidentState.ACTIVE.name()))
                          .unlimited()),
              auth);
      // Multiple incidents per instance are possible — surface the most recent one's message.
      return result.items().stream()
          .sorted(Comparator.comparing(IncidentEntity::creationTime).reversed())
          .collect(
              Collectors.toMap(
                  IncidentEntity::processInstanceKey,
                  IncidentEntity::errorMessage,
                  (first, ignored) -> first));
    } catch (final Exception e) {
      // Don't fail the whole export if the user lacks INCIDENT_READ or the search hiccups —
      // the row just gets a blank Incident Message column.
      LOG.warn("Could not enrich CSV export with incident messages: {}", e.getMessage());
      return Map.of();
    }
  }

  private Map<Long, String> fetchVariablesAsJson(
      final List<Long> keys, final CamundaAuthentication auth) {
    if (keys.isEmpty()) {
      return Map.of();
    }
    try {
      final var result =
          variableServices.search(
              VariableQuery.of(
                  b ->
                      b.filter(f -> f.processInstanceKeyOperations(Operation.in(keys)))
                          .unlimited()),
              auth);
      final Map<Long, ObjectNode> grouped = new HashMap<>();
      for (final var v : result.items()) {
        final var node =
            grouped.computeIfAbsent(v.processInstanceKey(), k -> EXPORT_JSON.createObjectNode());
        // value may be a truncated preview — prefer fullValue so the export contains complete
        // data even for large variables.
        final String resolved =
            (Boolean.TRUE.equals(v.isPreview()) && v.fullValue() != null)
                ? v.fullValue()
                : v.value();
        try {
          node.set(v.name(), EXPORT_JSON.readTree(resolved));
        } catch (final Exception parseError) {
          // Camunda variables are stored as JSON, but defensively fall back to a string cell
          // when a value is somehow malformed.
          node.put(v.name(), resolved);
        }
      }
      final Map<Long, String> out = new HashMap<>(grouped.size());
      for (final var entry : grouped.entrySet()) {
        out.put(entry.getKey(), entry.getValue().toString());
      }
      return out;
    } catch (final Exception e) {
      LOG.warn("Could not enrich CSV export with variables: {}", e.getMessage());
      return Map.of();
    }
  }

  private ResponseEntity<IncidentSearchQueryResult> searchIncidents(
      final long processInstanceKey, final IncidentQuery query) {
    try {
      final var result =
          processInstanceServices.searchIncidents(
              processInstanceKey, query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(SearchQueryResponseMapper.toIncidentSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationCancellation(
      final io.camunda.search.filter.ProcessInstanceFilter filter) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices.cancelProcessInstanceBatchOperationWithResult(
                filter, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationResolveIncidents(
      final io.camunda.search.filter.ProcessInstanceFilter filter) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices.resolveIncidentsBatchOperationWithResult(
                filter, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationMigrate(
      final ProcessInstanceMigrateBatchOperationRequest request) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices.migrateProcessInstancesBatchOperation(
                request, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationModify(
      final ProcessInstanceModifyBatchOperationRequest request) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices.modifyProcessInstancesBatchOperation(
                request, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationDeletion(
      final io.camunda.search.filter.ProcessInstanceFilter filter) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices.deleteProcessInstancesBatchOperation(
                filter, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> createProcessInstance(
      final ProcessInstanceCreateRequest request) {
    if (request.awaitCompletion()) {
      return RequestExecutor.executeServiceMethod(
          () ->
              processInstanceServices.createProcessInstanceWithResult(
                  request, authenticationProvider.getCamundaAuthentication()),
          ResponseMapper::toCreateProcessInstanceWithResultResponse,
          HttpStatus.OK);
    }
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices.createProcessInstance(
                request, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toCreateProcessInstanceResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> cancelProcessInstance(
      final ProcessInstanceCancelRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices.cancelProcessInstance(
                request, authenticationProvider.getCamundaAuthentication()));
  }

  private CompletableFuture<ResponseEntity<Object>> migrateProcessInstance(
      final ProcessInstanceMigrateRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices.migrateProcessInstance(
                request, authenticationProvider.getCamundaAuthentication()));
  }

  private CompletableFuture<ResponseEntity<Object>> modifyProcessInstance(
      final ProcessInstanceModifyRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices.modifyProcessInstance(
                request, authenticationProvider.getCamundaAuthentication()));
  }
}
