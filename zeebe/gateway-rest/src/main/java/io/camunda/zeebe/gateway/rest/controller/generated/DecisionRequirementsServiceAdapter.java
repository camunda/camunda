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

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionRequirementsSearchQueryRequestStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;

/**
 * Service adapter for DecisionRequirements operations.
 * Implements request mapping, service delegation, and response construction.
 */
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public interface DecisionRequirementsServiceAdapter {

  ResponseEntity<Object> searchDecisionRequirements(
      GeneratedDecisionRequirementsSearchQueryRequestStrictContract decisionRequirementsSearchQuery,
      CamundaAuthentication authentication
  );

  ResponseEntity<Object> getDecisionRequirements(
      Long decisionRequirementsKey,
      CamundaAuthentication authentication
  );

  ResponseEntity<Void> getDecisionRequirementsXML(
      Long decisionRequirementsKey,
      CamundaAuthentication authentication
  );
}
