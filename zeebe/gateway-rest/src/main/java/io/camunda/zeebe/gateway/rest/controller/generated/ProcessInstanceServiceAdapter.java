/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import io.camunda.security.auth.CamundaAuthentication;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;

/**
 * Service adapter for ProcessInstance operations. Implements request mapping, service delegation,
 * and response construction.
 */
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public interface ProcessInstanceServiceAdapter {

  ResponseEntity<Object> createProcessInstance(
      GeneratedProcessInstanceCreationInstructionStrictContract processInstanceCreationInstruction,
      CamundaAuthentication authentication);

  ResponseEntity<Object> getProcessInstance(
      String processInstanceKey, CamundaAuthentication authentication);

  ResponseEntity<Object> getProcessInstanceSequenceFlows(
      String processInstanceKey, CamundaAuthentication authentication);

  ResponseEntity<Object> getProcessInstanceStatistics(
      String processInstanceKey, CamundaAuthentication authentication);

  ResponseEntity<Object> searchProcessInstances(
      GeneratedProcessInstanceSearchQueryRequestStrictContract processInstanceSearchQuery,
      CamundaAuthentication authentication);

  ResponseEntity<Object> searchProcessInstanceIncidents(
      String processInstanceKey,
      GeneratedIncidentSearchQueryRequestStrictContract incidentSearchQuery,
      CamundaAuthentication authentication);

  ResponseEntity<Object> resolveProcessInstanceIncidents(
      String processInstanceKey, CamundaAuthentication authentication);

  ResponseEntity<Void> cancelProcessInstance(
      String processInstanceKey,
      GeneratedCancelProcessInstanceRequestStrictContract cancelProcessInstanceRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Object> cancelProcessInstancesBatchOperation(
      GeneratedProcessInstanceCancellationBatchOperationRequestStrictContract
          processInstanceCancellationBatchOperationRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Object> resolveIncidentsBatchOperation(
      GeneratedProcessInstanceIncidentResolutionBatchOperationRequestStrictContract
          processInstanceIncidentResolutionBatchOperationRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Object> migrateProcessInstancesBatchOperation(
      GeneratedProcessInstanceMigrationBatchOperationRequestStrictContract
          processInstanceMigrationBatchOperationRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Object> modifyProcessInstancesBatchOperation(
      GeneratedProcessInstanceModificationBatchOperationRequestStrictContract
          processInstanceModificationBatchOperationRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Void> deleteProcessInstance(
      String processInstanceKey,
      GeneratedDeleteProcessInstanceRequestStrictContract deleteProcessInstanceRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Object> deleteProcessInstancesBatchOperation(
      GeneratedProcessInstanceDeletionBatchOperationRequestStrictContract
          processInstanceDeletionBatchOperationRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Void> migrateProcessInstance(
      String processInstanceKey,
      GeneratedProcessInstanceMigrationInstructionStrictContract
          processInstanceMigrationInstruction,
      CamundaAuthentication authentication);

  ResponseEntity<Void> modifyProcessInstance(
      String processInstanceKey,
      GeneratedProcessInstanceModificationInstructionStrictContract
          processInstanceModificationInstruction,
      CamundaAuthentication authentication);

  ResponseEntity<Object> getProcessInstanceCallHierarchy(
      String processInstanceKey, CamundaAuthentication authentication);
}
