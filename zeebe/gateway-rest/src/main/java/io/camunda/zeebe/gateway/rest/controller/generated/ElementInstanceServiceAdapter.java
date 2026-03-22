/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedElementInstanceSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedSetVariableRequestStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;

/**
 * Service adapter for ElementInstance operations. Implements request mapping, service delegation,
 * and response construction.
 */
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public interface ElementInstanceServiceAdapter {

  ResponseEntity<Object> searchElementInstances(
      GeneratedElementInstanceSearchQueryRequestStrictContract elementInstanceSearchQuery,
      CamundaAuthentication authentication);

  ResponseEntity<Object> getElementInstance(
      Long elementInstanceKey, CamundaAuthentication authentication);

  ResponseEntity<Void> createElementInstanceVariables(
      Long elementInstanceKey,
      GeneratedSetVariableRequestStrictContract setVariableRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Object> searchElementInstanceIncidents(
      Long elementInstanceKey,
      GeneratedIncidentSearchQueryRequestStrictContract incidentSearchQuery,
      CamundaAuthentication authentication);
}
