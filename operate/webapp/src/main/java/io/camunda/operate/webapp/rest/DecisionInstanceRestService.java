/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest;

import static io.camunda.operate.webapp.rest.DecisionInstanceRestService.DECISION_INSTANCE_URL;

import io.camunda.operate.webapp.InternalAPIErrorController;
import io.camunda.operate.webapp.reader.DecisionInstanceReader;
import io.camunda.operate.webapp.rest.dto.dmn.DRDDataEntryDto;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListRequestDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListResponseDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Decision instances")
@RestController
@RequestMapping(value = DECISION_INSTANCE_URL)
@Validated
public class DecisionInstanceRestService extends InternalAPIErrorController {

  public static final String DECISION_INSTANCE_URL = "/api/decision-instances";

  @Autowired private PermissionsService permissionsService;
  @Autowired private DecisionInstanceReader decisionInstanceReader;

  @Operation(summary = "Query decision instances by different parameters")
  @PostMapping
  public DecisionInstanceListResponseDto queryDecisionInstances(
      @RequestBody final DecisionInstanceListRequestDto decisionInstanceRequest) {
    if (decisionInstanceRequest.getQuery() == null) {
      throw new InvalidRequestException("Query must be provided.");
    }
    return decisionInstanceReader.queryDecisionInstances(decisionInstanceRequest);
  }

  @Operation(summary = "Get decision instance by id")
  @GetMapping("/{decisionInstanceId}")
  public DecisionInstanceDto queryDecisionInstanceById(
      @PathVariable final String decisionInstanceId) {
    checkIdentityReadPermission(decisionInstanceId);
    return decisionInstanceReader.getDecisionInstance(decisionInstanceId);
  }

  @Operation(summary = "Get DRD data for decision instance")
  @GetMapping("/{decisionInstanceId}/drd-data")
  public Map<String, List<DRDDataEntryDto>> queryDecisionInstanceDRDData(
      @PathVariable final String decisionInstanceId) {
    checkIdentityReadPermission(decisionInstanceId);
    final Map<String, List<DRDDataEntryDto>> result =
        decisionInstanceReader.getDecisionInstanceDRDData(decisionInstanceId);
    if (result.isEmpty()) {
      throw new NotFoundException("Decision instance not found: " + decisionInstanceId);
    }
    return result;
  }

  private void checkIdentityReadPermission(final String decisionInstanceId) {
    if (permissionsService.permissionsEnabled()) {
      checkIdentityReadPermission(decisionInstanceReader.getDecisionInstance(decisionInstanceId));
    }
  }

  private void checkIdentityReadPermission(final DecisionInstanceDto decisionInstance) {
    if (!permissionsService.hasPermissionForDecision(
        decisionInstance.getDecisionId(), PermissionType.READ_DECISION_INSTANCE)) {
      throw new NotAuthorizedException(
          String.format(
              "No read permission for decision instance %s", decisionInstance.getDecisionId()));
    }
  }
}
