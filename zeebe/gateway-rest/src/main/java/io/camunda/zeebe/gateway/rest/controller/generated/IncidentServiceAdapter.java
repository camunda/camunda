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

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentProcessInstanceStatisticsByDefinitionQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentProcessInstanceStatisticsByErrorQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentResolutionRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentSearchQueryRequestStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;

/**
 * Service adapter for Incident operations. Implements request mapping, service delegation, and
 * response construction.
 */
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public interface IncidentServiceAdapter {

  ResponseEntity<Object> searchIncidents(
      GeneratedIncidentSearchQueryRequestStrictContract incidentSearchQuery,
      CamundaAuthentication authentication);

  ResponseEntity<Object> getIncident(Long incidentKey, CamundaAuthentication authentication);

  ResponseEntity<Void> resolveIncident(
      Long incidentKey,
      GeneratedIncidentResolutionRequestStrictContract incidentResolutionRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Object> getProcessInstanceStatisticsByError(
      GeneratedIncidentProcessInstanceStatisticsByErrorQueryStrictContract
          incidentProcessInstanceStatisticsByErrorQuery,
      CamundaAuthentication authentication);

  ResponseEntity<Object> getProcessInstanceStatisticsByDefinition(
      GeneratedIncidentProcessInstanceStatisticsByDefinitionQueryStrictContract
          incidentProcessInstanceStatisticsByDefinitionQuery,
      CamundaAuthentication authentication);
}
