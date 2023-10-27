/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.topology.api.TopologyManagementRequestSender;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.stereotype.Component;

@Component
@WebEndpoint(id = "cluster")
public class ClusterEndpoint {
  private final TopologyManagementRequestSender requestSender;

  @Autowired
  public ClusterEndpoint(final TopologyManagementRequestSender requestSender) {
    this.requestSender = requestSender;
  }

  @WriteOperation
  public WebEndpointResponse<?> scale(
      @Selector final ClusterComponent clusterComponent, final List<String> ids) {
    return switch (clusterComponent) {
      case BROKERS -> new WebEndpointResponse<>("Scaling brokers is not supported", 400);
    };
  }

  @WriteOperation
  public WebEndpointResponse<?> addMember(
      @Selector final ClusterComponent clusterComponent, @Selector final String id) {
    return switch (clusterComponent) {
      case ClusterComponent.BROKERS -> new WebEndpointResponse<>(
          "Adding brokers is not supported", 400);
    };
  }

  public enum ClusterComponent {
    BROKERS,
  }
}
