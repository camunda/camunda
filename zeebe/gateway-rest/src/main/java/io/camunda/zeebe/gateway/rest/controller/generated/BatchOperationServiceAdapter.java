/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedBatchOperationItemSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedBatchOperationSearchQueryRequestStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;

/**
 * Service adapter for BatchOperation operations. Implements request mapping, service delegation,
 * and response construction.
 */
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public interface BatchOperationServiceAdapter {

  ResponseEntity<Object> getBatchOperation(
      String batchOperationKey, CamundaAuthentication authentication);

  ResponseEntity<Object> searchBatchOperations(
      GeneratedBatchOperationSearchQueryRequestStrictContract batchOperationSearchQuery,
      CamundaAuthentication authentication);

  ResponseEntity<Void> cancelBatchOperation(
      String batchOperationKey, CamundaAuthentication authentication);

  ResponseEntity<Void> suspendBatchOperation(
      String batchOperationKey, CamundaAuthentication authentication);

  ResponseEntity<Void> resumeBatchOperation(
      String batchOperationKey, CamundaAuthentication authentication);

  ResponseEntity<Object> searchBatchOperationItems(
      GeneratedBatchOperationItemSearchQueryRequestStrictContract batchOperationItemSearchQuery,
      CamundaAuthentication authentication);
}
