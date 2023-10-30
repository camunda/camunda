/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.management;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.management.cluster.BrokerState;
import io.camunda.zeebe.management.cluster.BrokerStateCode;
import io.camunda.zeebe.management.cluster.Operation;
import io.camunda.zeebe.management.cluster.Operation.OperationEnum;
import io.camunda.zeebe.management.cluster.PartitionState;
import io.camunda.zeebe.management.cluster.PartitionStateCode;
import io.camunda.zeebe.management.cluster.PostOperationResponse;
import io.camunda.zeebe.topology.api.TopologyChangeResponse;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequestSender;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.PartitionState.State;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
  public WebEndpointResponse<PostOperationResponse> addSubResource(
      @Selector final Resource resource,
      @Selector final String resourceId,
      @Selector final Resource subResource,
      @Selector final String subResourceId,
      final int priority) {
    return switch (resource) {
      case BROKERS -> switch (subResource) {
        case PARTITIONS -> new WebEndpointResponse<>(
            // POST /cluster/brokers/1/partitions/2
            mapResponseType(
                requestSender
                    .joinPartition(
                        new JoinPartitionRequest(
                            MemberId.from(resourceId), Integer.parseInt(subResourceId), priority))
                    .join()));
        case BROKERS -> new WebEndpointResponse<>(404);
      };
      case PARTITIONS -> switch (subResource) {
        case BROKERS -> new WebEndpointResponse<>(
            // POST /cluster/partitions/1/brokers/2
            mapResponseType(
                requestSender
                    .joinPartition(
                        new JoinPartitionRequest(
                            MemberId.from(subResourceId), Integer.parseInt(resourceId), priority))
                    .join()));
        case PARTITIONS -> new WebEndpointResponse<>(404);
      };
    };
  }

  @DeleteOperation
  public WebEndpointResponse<PostOperationResponse> removeSubResource(
      @Selector final Resource resource,
      @Selector final String resourceId,
      @Selector final Resource subResource,
      @Selector final String subResourceId) {
    return switch (resource) {
      case BROKERS -> switch (subResource) {
        case PARTITIONS -> new WebEndpointResponse<>(
            // DELETE /cluster/brokers/1/partitions/2
            mapResponseType(
                requestSender
                    .leavePartition(
                        new LeavePartitionRequest(
                            MemberId.from(resourceId), Integer.parseInt(subResourceId)))
                    .join()));
        case BROKERS -> new WebEndpointResponse<>(404);
      };
      case PARTITIONS -> switch (subResource) {
        case BROKERS -> new WebEndpointResponse<>(
            // DELETE /cluster/partitions/1/brokers/2
            mapResponseType(
                requestSender
                    .leavePartition(
                        new LeavePartitionRequest(
                            MemberId.from(subResourceId), Integer.parseInt(resourceId)))
                    .join()));
        case PARTITIONS -> new WebEndpointResponse<>(404);
      };
    };
  }

  private static PostOperationResponse mapResponseType(final TopologyChangeResponse response) {
    return new PostOperationResponse()
        .changeId((int) response.changeId())
        .currentTopology(mapBrokerStates(response.currentTopology()))
        .expectedTopology(mapBrokerStates(response.expectedTopology()))
        .plannedChanges(mapOperations(response.plannedChanges()));
  }

  private static List<Operation> mapOperations(final List<TopologyChangeOperation> operations) {
    return operations.stream().map(ClusterEndpoint::mapOperation).toList();
  }

  private static Operation mapOperation(final TopologyChangeOperation operation) {
    return switch (operation) {
      case final MemberJoinOperation join -> new Operation()
          .operation(OperationEnum.BROKER_ADD)
          .brokerId(Integer.parseInt(join.memberId().id()));
      case final MemberLeaveOperation leave -> new Operation()
          .operation(OperationEnum.BROKER_REMOVE)
          .brokerId(Integer.parseInt(leave.memberId().id()));
      case final PartitionJoinOperation join -> new Operation()
          .operation(OperationEnum.PARTITION_JOIN)
          .brokerId(Integer.parseInt(join.memberId().id()))
          .partitionId(join.partitionId())
          .priority(join.priority());
      case final PartitionLeaveOperation leave -> new Operation()
          .operation(OperationEnum.PARTITION_LEAVE)
          .brokerId(Integer.parseInt(leave.memberId().id()))
          .partitionId(leave.partitionId());
      case final PartitionReconfigurePriorityOperation reconfigure -> new Operation()
          .operation(OperationEnum.PARTITION_RECONFIGURE_PRIORITY)
          .brokerId(Integer.parseInt(reconfigure.memberId().id()))
          .partitionId(reconfigure.partitionId())
          .priority(reconfigure.priority());
    };
  }

  private static List<BrokerState> mapBrokerStates(final Map<MemberId, MemberState> topology) {
    return topology.entrySet().stream()
        .map(
            entry ->
                new BrokerState()
                    .id(Integer.parseInt(entry.getKey().id()))
                    .state(mapBrokerState(entry.getValue().state()))
                    .lastUpdatedAt(entry.getValue().lastUpdated().atOffset(ZoneOffset.UTC))
                    .version((int) entry.getValue().version())
                    .partitions(mapPartitionStates(entry.getValue().partitions())))
        .toList();
  }

  private static BrokerStateCode mapBrokerState(final MemberState.State state) {
    return switch (state) {
      case ACTIVE -> BrokerStateCode.ACTIVE;
      case JOINING -> BrokerStateCode.JOINING;
      case LEAVING -> BrokerStateCode.LEAVING;
      case LEFT -> BrokerStateCode.LEFT;
      case UNINITIALIZED -> BrokerStateCode.UNKNOWN;
    };
  }

  private static List<PartitionState> mapPartitionStates(
      final Map<Integer, io.camunda.zeebe.topology.state.PartitionState> partitions) {
    return partitions.entrySet().stream()
        .map(
            entry ->
                new PartitionState()
                    .id(entry.getKey())
                    .priority(entry.getValue().priority())
                    .state(mapPartitionState(entry.getValue().state())))
        .toList();
  }

  private static PartitionStateCode mapPartitionState(final State state) {
    return switch (state) {
      case JOINING -> PartitionStateCode.JOINING;
      case ACTIVE -> PartitionStateCode.ACTIVE;
      case LEAVING -> PartitionStateCode.LEAVING;
      case UNKNOWN -> PartitionStateCode.UNKNOWN;
    };
  }

  public enum Resource {
    BROKERS,
    PARTITIONS,
  }
}
