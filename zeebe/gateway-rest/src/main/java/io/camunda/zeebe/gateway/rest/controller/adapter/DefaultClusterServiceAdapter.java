/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.TopologyServices;
import io.camunda.service.TopologyServices.ClusterStatus;
import io.camunda.zeebe.gateway.rest.controller.generated.ClusterServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import java.util.concurrent.CompletionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultClusterServiceAdapter implements ClusterServiceAdapter {

  private final TopologyServices topologyServices;

  public DefaultClusterServiceAdapter(final TopologyServices topologyServices) {
    this.topologyServices = topologyServices;
  }

  @Override
  public ResponseEntity<Object> getTopology(final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(
        topologyServices::getTopology, ResponseMapper::toTopologyResponse, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> getStatus(final CamundaAuthentication authentication) {
    try {
      final var clusterStatus = topologyServices.getStatus().join();
      return ClusterStatus.HEALTHY.equals(clusterStatus)
          ? ResponseEntity.noContent().build()
          : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    } catch (final CompletionException e) {
      throw e.getCause() instanceof RuntimeException re ? re : new RuntimeException(e.getCause());
    }
  }
}
