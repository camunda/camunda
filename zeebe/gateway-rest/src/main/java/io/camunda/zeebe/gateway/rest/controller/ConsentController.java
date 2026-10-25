/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.ConsentServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.protocol.impl.record.value.consent.ConsentRecord;
import io.camunda.zeebe.protocol.record.intent.ConsentIntent;
import io.camunda.zeebe.protocol.record.value.ConsentDecision;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The consent commands of the design: answer a consent request, grant consent in advance, and
 * revoke a grant. All three are strongly consistent commands against engine state; the engine
 * decides who may give them, from the authenticated caller.
 */
@CamundaRestController
@RequestMapping("/v2")
public class ConsentController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ConsentController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
  }

  /**
   * Answers the consent request of a parked job: {@code APPROVE_ONCE}, {@code GRANT} or {@code
   * DECLINE}.
   */
  @CamundaPostMapping(path = "/consent-requests/{jobKey}/resolution")
  public CompletableFuture<ResponseEntity<Object>> resolve(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("jobKey") final long jobKey,
      @RequestBody final Map<String, Object> body) {
    final var record =
        new ConsentRecord()
            .setJobKey(jobKey)
            .setDecision(ConsentDecision.valueOf(String.valueOf(body.get("decision"))));
    return send(physicalTenantId, ConsentIntent.RESOLVE, jobKey, record);
  }

  /** Grants consent in advance, for the caller, for one operation of one connection. */
  @CamundaPostMapping(path = "/consents")
  public CompletableFuture<ResponseEntity<Object>> grant(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final Map<String, Object> body) {
    final var record =
        new ConsentRecord()
            .setConnection(String.valueOf(body.get("connection")))
            .setOperation(String.valueOf(body.get("operation")));
    return send(physicalTenantId, ConsentIntent.GRANT, -1L, record);
  }

  /** Revokes a grant. The subject revokes their own; an admin gives a reason. */
  @CamundaPostMapping(path = "/consents/{consentKey}/revocation")
  public CompletableFuture<ResponseEntity<Object>> revoke(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("consentKey") final long consentKey,
      @RequestBody(required = false) final Map<String, Object> body) {
    final var reason = body == null ? "" : String.valueOf(body.getOrDefault("reason", ""));
    final var record = new ConsentRecord().setConsentKey(consentKey).setReason(reason);
    return send(physicalTenantId, ConsentIntent.REVOKE, consentKey, record);
  }

  private CompletableFuture<ResponseEntity<Object>> send(
      final String physicalTenantId,
      final ConsentIntent intent,
      final long key,
      final ConsentRecord record) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    final var services = new ConsentServices(serviceRegistry.clockServices(physicalTenantId));
    return RequestExecutor.executeServiceMethod(
        () -> services.send(new BrokerConsentRequest(intent, key, record), authentication),
        ConsentController::toResponse,
        HttpStatus.OK);
  }

  private static Map<String, Object> toResponse(final ConsentRecord record) {
    final Map<String, Object> response = new LinkedHashMap<>();
    response.put("consentKey", record.getConsentKey());
    response.put("decision", record.getDecision().name());
    response.put("subject", record.getSubject());
    response.put("requester", record.getRequester());
    response.put("connection", record.getConnection());
    response.put("operation", record.getOperation());
    response.put("change", record.getChange());
    response.put("jobKey", record.getJobKey());
    response.put("processInstanceKey", record.getProcessInstanceKey());
    return response;
  }
}
