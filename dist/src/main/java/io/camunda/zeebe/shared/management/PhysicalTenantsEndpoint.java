/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.broker.PhysicalTenantConfigRejectedException;
import io.camunda.zeebe.broker.PhysicalTenantConfigService;
import io.camunda.zeebe.broker.PhysicalTenantConfigService.PhysicalTenantStatus;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Manages the configurations of physical tenants on the management port of any broker: lists every
 * tenant with its owner, version and the version each member applied, and creates, updates and
 * deletes tenants owned by the API. A request body is the tenant's whole configuration subtree as a
 * flat map, with property names relative to {@code camunda.physical-tenants.<id>}.
 *
 * <p>A change is accepted once the coordinator started rolling it out ({@code 202}); its progress
 * shows in {@code rolledOut}, {@code appliedVersions} and {@code pendingMembers}. {@code POST
 * <id>/cancel} cancels the rollout of an update and rolls the tenant back to the configuration it
 * had before.
 */
@NullMarked
@Component
@Profile("broker")
@RestControllerEndpoint(id = "physicalTenants")
public class PhysicalTenantsEndpoint {

  private final PhysicalTenantConfigService service;

  public PhysicalTenantsEndpoint(final PhysicalTenantConfigService service) {
    this.service = service;
  }

  @GetMapping
  public List<PhysicalTenantStatus> list() {
    return service.list();
  }

  @GetMapping("/{physicalTenantId}")
  public ResponseEntity<PhysicalTenantStatus> get(@PathVariable final String physicalTenantId) {
    return ResponseEntity.of(service.get(physicalTenantId));
  }

  @PostMapping("/{physicalTenantId}")
  public ResponseEntity<PhysicalTenantStatus> create(
      @PathVariable final String physicalTenantId,
      @RequestBody final Map<String, String> configuration) {
    return ResponseEntity.accepted().body(service.create(physicalTenantId, configuration));
  }

  @PutMapping("/{physicalTenantId}")
  public ResponseEntity<PhysicalTenantStatus> update(
      @PathVariable final String physicalTenantId,
      @RequestBody final Map<String, String> configuration) {
    return ResponseEntity.accepted().body(service.update(physicalTenantId, configuration));
  }

  @PostMapping("/{physicalTenantId}/cancel")
  public ResponseEntity<PhysicalTenantStatus> cancel(@PathVariable final String physicalTenantId) {
    return ResponseEntity.accepted().body(service.cancel(physicalTenantId));
  }

  @DeleteMapping("/{physicalTenantId}")
  public ResponseEntity<Void> delete(@PathVariable final String physicalTenantId) {
    service.delete(physicalTenantId);
    return ResponseEntity.accepted().build();
  }

  @ExceptionHandler(PhysicalTenantConfigRejectedException.class)
  public ResponseEntity<Rejection> rejected(final PhysicalTenantConfigRejectedException e) {
    final var status =
        switch (e.status()) {
          case INVALID -> HttpStatus.BAD_REQUEST;
          case NOT_FOUND -> HttpStatus.NOT_FOUND;
          case IN_PROGRESS, NOT_OWNER -> HttpStatus.CONFLICT;
          case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    return ResponseEntity.status(status)
        .body(new Rejection(e.status().name(), String.valueOf(e.getMessage()), e.violations()));
  }

  public record Rejection(String status, String message, List<String> violations) {}
}
