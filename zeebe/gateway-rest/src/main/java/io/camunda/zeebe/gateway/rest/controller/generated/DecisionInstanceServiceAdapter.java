/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionInstanceDeletionBatchOperationRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionInstanceSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDeleteDecisionInstanceRequestStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;

/**
 * Service adapter for DecisionInstance operations. Implements request mapping, service delegation,
 * and response construction.
 */
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public interface DecisionInstanceServiceAdapter {

  ResponseEntity<Object> searchDecisionInstances(
      GeneratedDecisionInstanceSearchQueryRequestStrictContract decisionInstanceSearchQuery,
      CamundaAuthentication authentication);

  ResponseEntity<Object> getDecisionInstance(
      String decisionEvaluationInstanceKey, CamundaAuthentication authentication);

  ResponseEntity<Void> deleteDecisionInstance(
      Long decisionInstanceKey,
      GeneratedDeleteDecisionInstanceRequestStrictContract deleteDecisionInstanceRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Object> deleteDecisionInstancesBatchOperation(
      GeneratedDecisionInstanceDeletionBatchOperationRequestStrictContract
          decisionInstanceDeletionBatchOperationRequest,
      CamundaAuthentication authentication);
}
