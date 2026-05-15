/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.SecretServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Resolves {@code camunda.secrets.*} references against the configured secret store(s).
 *
 * <p>PoC: no authorization. Returns only successfully resolved entries; missing or malformed
 * references are silently omitted from the response.
 */
@CamundaRestController
@RequestMapping("/v2/secrets")
public class SecretController {

  private final SecretServices secretServices;

  public SecretController(final SecretServices secretServices) {
    this.secretServices = secretServices;
  }

  @CamundaPostMapping(path = "/resolve")
  public ResponseEntity<ResolveSecretsResponse> resolve(
      @RequestBody final ResolveSecretsRequest request) {
    final var references = request == null ? List.<String>of() : request.references();
    final var resolved = secretServices.resolve(references);
    return ResponseEntity.ok(new ResolveSecretsResponse(resolved));
  }

  public record ResolveSecretsRequest(List<String> references) {}

  public record ResolveSecretsResponse(Map<String, String> resolved) {}
}
