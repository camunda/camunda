/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.TopologyServices;
import io.camunda.service.TopologyServices.ClusterStatus;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.mapper.RequestMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2")
public class StatusController {

  private final TopologyServices topologyServices;

  public StatusController(final TopologyServices topologyServices) {
    this.topologyServices = topologyServices;
  }

  @CamundaGetMapping(path = "/status")
  public CompletableFuture<ResponseEntity<Object>> getStatus() {
    return RequestMapper.executeServiceMethod(
        () -> topologyServices.withAuthentication(CamundaAuthentication.none()).getStatus(),
        StatusController::getStatusResponse);
  }

  private static ResponseEntity<Object> getStatusResponse(final ClusterStatus clusterStatus) {
    return ClusterStatus.HEALTHY.equals(clusterStatus)
        ? ResponseEntity.noContent().build()
        : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
  }
}
