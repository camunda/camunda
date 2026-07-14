/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/cluster/v2")
public class DummyClusterTopologyController {

  @CamundaGetMapping(path = "/topology")
  public ResponseEntity<Void> getClusterTopology() {
    // Placeholder response; the aggregated cluster topology is implemented in a follow-up.
    return ResponseEntity.ok().build();
  }
}
