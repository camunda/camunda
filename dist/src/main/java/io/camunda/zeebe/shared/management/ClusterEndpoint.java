/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.management;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequestSender;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
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
  public WebEndpointResponse<?> scale(@Selector final Resource resource, final List<String> ids) {
    return switch (resource) {
      case BROKERS -> new WebEndpointResponse<>("Scaling brokers is not supported", 501);
      case PARTITIONS -> new WebEndpointResponse<>("Scaling partitions is not supported", 501);
    };
  }

  @WriteOperation
  public WebEndpointResponse<?> add(@Selector final Resource resource, @Selector final String id) {
    return switch (resource) {
      case BROKERS -> new WebEndpointResponse<>("Adding brokers is not supported", 501);
      case PARTITIONS -> new WebEndpointResponse<>("Adding partitions is not supported", 501);
    };
  }

  @DeleteOperation
  public WebEndpointResponse<?> remove(
      @Selector final Resource resource, @Selector final String id) {
    return switch (resource) {
      case BROKERS -> new WebEndpointResponse<>("Removing brokers is not supported", 501);
      case PARTITIONS -> new WebEndpointResponse<>("Removing partitions is not supported", 501);
    };
  }

  @WriteOperation
  public WebEndpointResponse<?> addSubResource(
      @Selector final Resource resource,
      @Selector final String resourceId,
      @Selector final Resource subResource,
      @Selector final String subResourceId,
      final int priority) {
    return switch (resource) {
      case BROKERS -> switch (subResource) {
        case PARTITIONS -> new WebEndpointResponse<>(
            // POST /cluster/brokers/1/partitions/2
            requestSender
                .joinPartition(
                    new JoinPartitionRequest(
                        MemberId.from(resourceId), Integer.parseInt(subResourceId), priority))
                .join());
        case BROKERS -> new WebEndpointResponse<>(404);
      };
      case PARTITIONS -> switch (subResource) {
        case BROKERS -> new WebEndpointResponse<>(
            // POST /cluster/partitions/1/brokers/2
            requestSender
                .joinPartition(
                    new JoinPartitionRequest(
                        MemberId.from(subResourceId), Integer.parseInt(resourceId), priority))
                .join());
        case PARTITIONS -> new WebEndpointResponse<>(404);
      };
    };
  }

  @DeleteOperation
  public WebEndpointResponse<?> removeSubResource(
      @Selector final Resource resource,
      @Selector final String resourceId,
      @Selector final Resource subResource,
      @Selector final String subResourceId) {
    return switch (resource) {
      case BROKERS -> switch (subResource) {
        case PARTITIONS -> new WebEndpointResponse<>(
            // DELETE /cluster/brokers/1/partitions/2
            requestSender
                .leavePartition(
                    new LeavePartitionRequest(
                        MemberId.from(resourceId), Integer.parseInt(subResourceId)))
                .join());
        case BROKERS -> new WebEndpointResponse<>(404);
      };
      case PARTITIONS -> switch (subResource) {
        case BROKERS -> new WebEndpointResponse<>(
            // DELETE /cluster/partitions/1/brokers/2
            requestSender
                .leavePartition(
                    new LeavePartitionRequest(
                        MemberId.from(subResourceId), Integer.parseInt(resourceId)))
                .join());
        case PARTITIONS -> new WebEndpointResponse<>(404);
      };
    };
  }

  public enum Resource {
    BROKERS,
    PARTITIONS,
  }
}
