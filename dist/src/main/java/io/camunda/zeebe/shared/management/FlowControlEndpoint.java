/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

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
          .body(flowControlService.set(flowControlCfg).join());
    } catch (final Exception e) {
      return ResponseEntity.internalServerError().body(e);
    }
  }

  @GetMapping
  public ResponseEntity<?> get() {
    try {
      return ResponseEntity.status(WebEndpointResponse.STATUS_OK)
          .body(flowControlService.get().join());
    } catch (final Exception e) {
      return ResponseEntity.internalServerError().body(e);
    }
  }

  interface FlowControlService {
    CompletableFuture<Map<Integer, JsonNode>> get();

    CompletableFuture<Map<Integer, JsonNode>> set(FlowControlCfg flowControlCfg);
  }
}
