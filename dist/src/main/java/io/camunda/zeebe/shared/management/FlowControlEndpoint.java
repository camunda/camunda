/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.zeebe.broker.system.configuration.FlowControlCfg;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Component
@RestControllerEndpoint(id = "flowControl")
public class FlowControlEndpoint {

  final FlowControlService flowControlService;

  @Autowired
  public FlowControlEndpoint(final FlowControlService flowControlService) {
    this.flowControlService = flowControlService;
  }

  @PostMapping()
  public ResponseEntity<?> post(@RequestBody final FlowControlCfg flowControlCfg) {

    try {
      return ResponseEntity.status(WebEndpointResponse.STATUS_OK)
          .body(flowControlService.set(flowControlCfg, DEFAULT_PHYSICAL_TENANT_ID).join());
    } catch (final Exception e) {
      return ResponseEntity.internalServerError().body(e);
    }
  }

  @GetMapping
  public ResponseEntity<?> get() {
    try {
      return ResponseEntity.status(WebEndpointResponse.STATUS_OK)
          .body(flowControlService.get(DEFAULT_PHYSICAL_TENANT_ID).join());
    } catch (final Exception e) {
      return ResponseEntity.internalServerError().body(e);
    }
  }

  interface FlowControlService {

    /**
     * Returns the flow control configuration of every partition of the given physical tenant, keyed
     * by partition id.
     */
    CompletableFuture<Map<Integer, JsonNode>> get(String physicalTenantId);

    /**
     * Applies the given configuration to every partition of the given physical tenant and returns
     * the resulting configuration, keyed by partition id.
     */
    CompletableFuture<Map<Integer, JsonNode>> set(
        FlowControlCfg flowControlCfg, String physicalTenantId);
  }
}
