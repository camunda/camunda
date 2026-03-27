/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedCancelProcessInstanceRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDeleteProcessInstanceRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceCancellationBatchOperationRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceCreationInstructionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceDeletionBatchOperationRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceIncidentResolutionBatchOperationRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceMigrationBatchOperationRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceMigrationInstructionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceModificationBatchOperationRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceModificationInstructionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceSearchQueryRequestStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.zeebe.gateway.rest.controller.generated.ProcessInstanceServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultProcessInstanceServiceAdapter implements ProcessInstanceServiceAdapter {

  private final ProcessInstanceServices processInstanceServices;
  private final MultiTenancyConfiguration multiTenancyCfg;

  public DefaultProcessInstanceServiceAdapter(
      final ProcessInstanceServices processInstanceServices,
      final MultiTenancyConfiguration multiTenancyCfg) {
    this.processInstanceServices = processInstanceServices;
    this.multiTenancyCfg = multiTenancyCfg;
  }

  @Override
  public ResponseEntity<Object> createProcessInstance(
      final GeneratedProcessInstanceCreationInstructionStrictContract
          processInstanceCreationInstruction,
      final CamundaAuthentication authentication) {
    return RequestMapper.toCreateProcessInstance(
            processInstanceCreationInstruction, multiTenancyCfg.isChecksEnabled())
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request -> createProcessInstance(request, authentication));
  }

  @Override
  public ResponseEntity<Object> getProcessInstance(
      final Long processInstanceKey, final CamundaAuthentication authentication) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toProcessInstance(
                  processInstanceServices.getByKey(processInstanceKey, authentication)));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  @Override
  public ResponseEntity<Object> getProcessInstanceSequenceFlows(
      final Long processInstanceKey, final CamundaAuthentication authentication) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toSequenceFlowsResult(
                  processInstanceServices.sequenceFlows(processInstanceKey, authentication)));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  @Override
  public ResponseEntity<Object> getProcessInstanceStatistics(
      final Long processInstanceKey, final CamundaAuthentication authentication) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toProcessInstanceElementStatisticsResult(
                  processInstanceServices.elementStatistics(processInstanceKey, authentication)));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  @Override
  public ResponseEntity<Object> searchProcessInstances(
      final GeneratedProcessInstanceSearchQueryRequestStrictContract
          processInstanceSearchQueryStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toProcessInstanceQueryStrict(processInstanceSearchQueryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = processInstanceServices.search(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toProcessInstanceSearchQueryResponse(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> searchProcessInstanceIncidents(
      final Long processInstanceKey,
      final GeneratedIncidentSearchQueryRequestStrictContract incidentSearchQueryStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toIncidentQueryStrict(incidentSearchQueryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result =
                    processInstanceServices.searchIncidents(
                        processInstanceKey, query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toIncidentSearchQueryResponse(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> resolveProcessInstanceIncidents(
      final Long processInstanceKey, final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(
        () ->
            processInstanceServices.resolveProcessInstanceIncidents(
                processInstanceKey, authentication),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> cancelProcessInstance(
      final Long processInstanceKey,
      final GeneratedCancelProcessInstanceRequestStrictContract cancelProcessInstanceRequestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toCancelProcessInstance(
            processInstanceKey, cancelProcessInstanceRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> processInstanceServices.cancelProcessInstance(request, authentication)));
  }

  @Override
  public ResponseEntity<Object> cancelProcessInstancesBatchOperation(
      final GeneratedProcessInstanceCancellationBatchOperationRequestStrictContract requestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toRequiredProcessInstanceFilter(requestStrict.filter())
        .fold(
            RestErrorMapper::mapProblemToResponse,
            filter ->
                RequestExecutor.executeSync(
                    () ->
                        processInstanceServices.cancelProcessInstanceBatchOperationWithResult(
                            filter, authentication),
                    ResponseMapper::toBatchOperationCreatedWithResultResponse,
                    HttpStatus.OK));
  }

  @Override
  public ResponseEntity<Object> resolveIncidentsBatchOperation(
      final GeneratedProcessInstanceIncidentResolutionBatchOperationRequestStrictContract
          requestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toRequiredProcessInstanceFilter(
            requestStrict != null ? requestStrict.filter() : null)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            filter ->
                RequestExecutor.executeSync(
                    () ->
                        processInstanceServices.resolveIncidentsBatchOperationWithResult(
                            filter, authentication),
                    ResponseMapper::toBatchOperationCreatedWithResultResponse,
                    HttpStatus.OK));
  }

  @Override
  public ResponseEntity<Object> migrateProcessInstancesBatchOperation(
      final GeneratedProcessInstanceMigrationBatchOperationRequestStrictContract requestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toProcessInstanceMigrationBatchOperationRequest(requestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            migrationRequest ->
                RequestExecutor.executeSync(
                    () ->
                        processInstanceServices.migrateProcessInstancesBatchOperation(
                            migrationRequest, authentication),
                    ResponseMapper::toBatchOperationCreatedWithResultResponse,
                    HttpStatus.OK));
  }

  @Override
  public ResponseEntity<Object> modifyProcessInstancesBatchOperation(
      final GeneratedProcessInstanceModificationBatchOperationRequestStrictContract requestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toProcessInstanceModifyBatchOperationRequest(requestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            modifyRequest ->
                RequestExecutor.executeSync(
                    () ->
                        processInstanceServices.modifyProcessInstancesBatchOperation(
                            modifyRequest, authentication),
                    ResponseMapper::toBatchOperationCreatedWithResultResponse,
                    HttpStatus.OK));
  }

  @Override
  public ResponseEntity<Void> deleteProcessInstance(
      final Long processInstanceKey,
      final GeneratedDeleteProcessInstanceRequestStrictContract deleteProcessInstanceRequestStrict,
      final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(
        () ->
            processInstanceServices.deleteProcessInstance(
                processInstanceKey,
                Objects.nonNull(deleteProcessInstanceRequestStrict)
                    ? deleteProcessInstanceRequestStrict.operationReference()
                    : null,
                authentication));
  }

  @Override
  public ResponseEntity<Object> deleteProcessInstancesBatchOperation(
      final GeneratedProcessInstanceDeletionBatchOperationRequestStrictContract requestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toRequiredProcessInstanceFilter(requestStrict.filter())
        .fold(
            RestErrorMapper::mapProblemToResponse,
            filter ->
                RequestExecutor.executeSync(
                    () ->
                        processInstanceServices.deleteProcessInstancesBatchOperation(
                            filter, authentication),
                    ResponseMapper::toBatchOperationCreatedWithResultResponse,
                    HttpStatus.OK));
  }

  @Override
  public ResponseEntity<Void> migrateProcessInstance(
      final Long processInstanceKey,
      final GeneratedProcessInstanceMigrationInstructionStrictContract
          processInstanceMigrationInstructionStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toMigrateProcessInstance(
            processInstanceKey, processInstanceMigrationInstructionStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            requestStrict ->
                RequestExecutor.executeSync(
                    () ->
                        processInstanceServices.migrateProcessInstance(
                            requestStrict, authentication)));
  }

  @Override
  public ResponseEntity<Void> modifyProcessInstance(
      final Long processInstanceKey,
      final GeneratedProcessInstanceModificationInstructionStrictContract
          processInstanceModificationInstructionStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toModifyProcessInstance(
            processInstanceKey, processInstanceModificationInstructionStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            requestStrict ->
                RequestExecutor.executeSync(
                    () ->
                        processInstanceServices.modifyProcessInstance(
                            requestStrict, authentication)));
  }

  @SuppressWarnings("unchecked")
  @Override
  public ResponseEntity<Object> getProcessInstanceCallHierarchy(
      final Long processInstanceKey, final CamundaAuthentication authentication) {
    try {
      return (ResponseEntity<Object>)
          (ResponseEntity<?>)
              ResponseEntity.ok()
                  .body(
                      SearchQueryResponseMapper.toProcessInstanceCallHierarchyEntries(
                          processInstanceServices.callHierarchy(
                              processInstanceKey, authentication)));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  private ResponseEntity<Object> createProcessInstance(
      final ProcessInstanceCreateRequest requestStrict,
      final CamundaAuthentication authentication) {
    if (Boolean.TRUE.equals(requestStrict.awaitCompletion())) {
      return RequestExecutor.executeSync(
          () ->
              processInstanceServices.createProcessInstanceWithResult(
                  requestStrict, authentication),
          ResponseMapper::toCreateProcessInstanceWithResultResponse,
          HttpStatus.OK);
    }
    return RequestExecutor.executeSync(
        () -> processInstanceServices.createProcessInstance(requestStrict, authentication),
        ResponseMapper::toCreateProcessInstanceResponse,
        HttpStatus.OK);
  }
}
