/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.rest.BusinessValueOverviewRestService.BUSINESS_VALUE_PATH;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetResponseDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetUpsertRequestDto;
import io.camunda.optimize.service.businessvalue.BusinessValueTargetService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the BVD per-process target CRUD. GET pre-fills the target modal; PUT persists an
 * upsert and synchronously recomputes all four range presets on the {@code business-value-overview}
 * index so the caller's next {@code GET /business-value/overview} reflects the new target.
 *
 * <p>Both endpoints gate access to the tenant via {@link
 * io.camunda.optimize.service.tenant.TenantService#isAuthorizedToSeeTenant(String, String)} — the
 * same posture used by every read on Optimize's definition surface (see {@code
 * bvd-target-solution-design.md} §2.3a).
 *
 * <p>{@code tenantId} travels as a query parameter rather than a path segment (a deviation from
 * tech design §5.5/§5.6): Zeebe's canonical no-tenant sentinel is {@code <default>}, and embedded
 * Tomcat rejects {@code <} / {@code >} inside URI paths with a generic 400 before any Spring
 * handler runs. Query strings do not carry that restriction. Same posture as every existing
 * tenant-aware Optimize endpoint.
 */
@Validated
@RestController
@RequestMapping(REST_API_PATH + BUSINESS_VALUE_PATH)
public class BusinessValueTargetRestService {

  public static final String TARGETS_SUB_PATH = "/targets/{processDefinitionKey}";

  private final BusinessValueTargetService targetService;
  private final SessionService sessionService;

  public BusinessValueTargetRestService(
      final BusinessValueTargetService targetService, final SessionService sessionService) {
    this.targetService = targetService;
    this.sessionService = sessionService;
  }

  @GetMapping(TARGETS_SUB_PATH)
  public BusinessValueTargetResponseDto getTarget(
      @PathVariable("processDefinitionKey") final String processDefinitionKey,
      @RequestParam("tenantId") final String tenantId,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return targetService.readTarget(userId, tenantId, processDefinitionKey);
  }

  @PutMapping(TARGETS_SUB_PATH)
  public BusinessValueTargetResponseDto putTarget(
      @PathVariable("processDefinitionKey") final String processDefinitionKey,
      @RequestParam("tenantId") final String tenantId,
      @Valid @RequestBody final BusinessValueTargetUpsertRequestDto body,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return targetService.upsertTarget(userId, tenantId, processDefinitionKey, body);
  }
}
