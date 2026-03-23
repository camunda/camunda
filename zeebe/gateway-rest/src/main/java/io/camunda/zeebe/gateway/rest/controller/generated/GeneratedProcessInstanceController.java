/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

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
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@CamundaRestController
@RequestMapping("/v2")
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public class GeneratedProcessInstanceController {

  private final ProcessInstanceServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedProcessInstanceController(
      final ProcessInstanceServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/process-instances",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> createProcessInstance(
      @RequestBody final GeneratedProcessInstanceCreationInstructionStrictContract processInstanceCreationInstruction
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.createProcessInstance(processInstanceCreationInstruction, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/process-instances/{processInstanceKey}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> getProcessInstance(
      @PathVariable("processInstanceKey") final Long processInstanceKey
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getProcessInstance(processInstanceKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/process-instances/{processInstanceKey}/sequence-flows",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> getProcessInstanceSequenceFlows(
      @PathVariable("processInstanceKey") final Long processInstanceKey
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getProcessInstanceSequenceFlows(processInstanceKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/process-instances/{processInstanceKey}/statistics/element-instances",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> getProcessInstanceStatistics(
      @PathVariable("processInstanceKey") final Long processInstanceKey
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getProcessInstanceStatistics(processInstanceKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/process-instances/search",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> searchProcessInstances(
      @RequestBody(required = false) final GeneratedProcessInstanceSearchQueryRequestStrictContract processInstanceSearchQuery
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchProcessInstances(processInstanceSearchQuery, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/process-instances/{processInstanceKey}/incidents/search",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> searchProcessInstanceIncidents(
      @PathVariable("processInstanceKey") final Long processInstanceKey,
      @RequestBody(required = false) final GeneratedIncidentSearchQueryRequestStrictContract incidentSearchQuery
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchProcessInstanceIncidents(processInstanceKey, incidentSearchQuery, authentication);
  }

  @RequiresSecondaryStorage
  @RequestMapping(
      method = RequestMethod.POST,
      value = "/process-instances/{processInstanceKey}/incident-resolution",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> resolveProcessInstanceIncidents(
      @PathVariable("processInstanceKey") final Long processInstanceKey
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.resolveProcessInstanceIncidents(processInstanceKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/process-instances/{processInstanceKey}/cancellation",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> cancelProcessInstance(
      @PathVariable("processInstanceKey") final Long processInstanceKey,
      @RequestBody(required = false) final GeneratedCancelProcessInstanceRequestStrictContract cancelProcessInstanceRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.cancelProcessInstance(processInstanceKey, cancelProcessInstanceRequest, authentication);
  }

  @RequiresSecondaryStorage
  @RequestMapping(
      method = RequestMethod.POST,
      value = "/process-instances/cancellation",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> cancelProcessInstancesBatchOperation(
      @RequestBody final GeneratedProcessInstanceCancellationBatchOperationRequestStrictContract processInstanceCancellationBatchOperationRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.cancelProcessInstancesBatchOperation(processInstanceCancellationBatchOperationRequest, authentication);
  }

  @RequiresSecondaryStorage
  @RequestMapping(
      method = RequestMethod.POST,
      value = "/process-instances/incident-resolution",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> resolveIncidentsBatchOperation(
      @RequestBody(required = false) final GeneratedProcessInstanceIncidentResolutionBatchOperationRequestStrictContract processInstanceIncidentResolutionBatchOperationRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.resolveIncidentsBatchOperation(processInstanceIncidentResolutionBatchOperationRequest, authentication);
  }

  @RequiresSecondaryStorage
  @RequestMapping(
      method = RequestMethod.POST,
      value = "/process-instances/migration",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> migrateProcessInstancesBatchOperation(
      @RequestBody final GeneratedProcessInstanceMigrationBatchOperationRequestStrictContract processInstanceMigrationBatchOperationRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.migrateProcessInstancesBatchOperation(processInstanceMigrationBatchOperationRequest, authentication);
  }

  @RequiresSecondaryStorage
  @RequestMapping(
      method = RequestMethod.POST,
      value = "/process-instances/modification",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> modifyProcessInstancesBatchOperation(
      @RequestBody final GeneratedProcessInstanceModificationBatchOperationRequestStrictContract processInstanceModificationBatchOperationRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.modifyProcessInstancesBatchOperation(processInstanceModificationBatchOperationRequest, authentication);
  }

  @RequiresSecondaryStorage
  @RequestMapping(
      method = RequestMethod.POST,
      value = "/process-instances/{processInstanceKey}/deletion",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> deleteProcessInstance(
      @PathVariable("processInstanceKey") final Long processInstanceKey,
      @RequestBody(required = false) final GeneratedDeleteProcessInstanceRequestStrictContract deleteProcessInstanceRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.deleteProcessInstance(processInstanceKey, deleteProcessInstanceRequest, authentication);
  }

  @RequiresSecondaryStorage
  @RequestMapping(
      method = RequestMethod.POST,
      value = "/process-instances/deletion",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> deleteProcessInstancesBatchOperation(
      @RequestBody final GeneratedProcessInstanceDeletionBatchOperationRequestStrictContract processInstanceDeletionBatchOperationRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.deleteProcessInstancesBatchOperation(processInstanceDeletionBatchOperationRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/process-instances/{processInstanceKey}/migration",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> migrateProcessInstance(
      @PathVariable("processInstanceKey") final Long processInstanceKey,
      @RequestBody final GeneratedProcessInstanceMigrationInstructionStrictContract processInstanceMigrationInstruction
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.migrateProcessInstance(processInstanceKey, processInstanceMigrationInstruction, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/process-instances/{processInstanceKey}/modification",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> modifyProcessInstance(
      @PathVariable("processInstanceKey") final Long processInstanceKey,
      @RequestBody final GeneratedProcessInstanceModificationInstructionStrictContract processInstanceModificationInstruction
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.modifyProcessInstance(processInstanceKey, processInstanceModificationInstruction, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/process-instances/{processInstanceKey}/call-hierarchy",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> getProcessInstanceCallHierarchy(
      @PathVariable("processInstanceKey") final Long processInstanceKey
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getProcessInstanceCallHierarchy(processInstanceKey, authentication);
  }
}
