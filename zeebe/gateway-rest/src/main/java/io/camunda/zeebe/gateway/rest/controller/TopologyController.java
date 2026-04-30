/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.service.TopologyServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.ClusterScoped;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@ClusterScoped
@RequestMapping(path = {"/v1", "/v2"})
public final class TopologyController {

  private final TopologyServices topologyServices;

  public TopologyController(final TopologyServices topologyServices) {
    this.topologyServices = topologyServices;
  }

  @CamundaGetMapping(path = "/topology")
  public CompletableFuture<ResponseEntity<Object>> getTopology() {
    return RequestExecutor.executeServiceMethod(
        topologyServices::getTopology, ResponseMapper::toTopologyResponse, HttpStatus.OK);
  }
}
