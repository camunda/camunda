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
import io.camunda.gateway.protocol.model.CancelProcessInstanceRequest;
import io.camunda.gateway.protocol.model.DeleteProcessInstanceRequest;
import io.camunda.gateway.protocol.model.IncidentSearchQuery;
import io.camunda.gateway.protocol.model.ProcessInstanceCancellationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceDeletionBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceIncidentResolutionBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceSearchQuery;
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
      final ProcessInstanceCreationInstruction processInstanceCreationInstruction,
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
      final ProcessInstanceSearchQuery processInstanceSearchQueryStrict,
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
      final IncidentSearchQuery incidentSearchQueryStrict,
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
      final CancelProcessInstanceRequest cancelProcessInstanceRequestStrict,
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
      final ProcessInstanceCancellationBatchOperationRequest requestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toRequiredProcessInstanceFilter(requestStrict.getFilter())
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
      final ProcessInstanceIncidentResolutionBatchOperationRequest requestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toRequiredProcessInstanceFilter(
            requestStrict != null ? requestStrict.getFilter() : null)
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
      final ProcessInstanceMigrationBatchOperationRequest requestStrict,
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
      final ProcessInstanceModificationBatchOperationRequest requestStrict,
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
      final DeleteProcessInstanceRequest deleteProcessInstanceRequestStrict,
      final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(
        () ->
            processInstanceServices.deleteProcessInstance(
                processInstanceKey,
                Objects.nonNull(deleteProcessInstanceRequestStrict)
                    ? deleteProcessInstanceRequestStrict.getOperationReference().orElse(null)
                    : null,
                authentication));
  }

  @Override
  public ResponseEntity<Object> deleteProcessInstancesBatchOperation(
      final ProcessInstanceDeletionBatchOperationRequest requestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toRequiredProcessInstanceFilter(requestStrict.getFilter())
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
      final ProcessInstanceMigrationInstruction processInstanceMigrationInstructionStrict,
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
      final ProcessInstanceModificationInstruction processInstanceModificationInstructionStrict,
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
