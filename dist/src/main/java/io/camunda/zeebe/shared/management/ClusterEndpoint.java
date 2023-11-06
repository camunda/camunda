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
import io.camunda.zeebe.management.cluster.Error;
import io.camunda.zeebe.management.cluster.GetTopologyResponse;
import io.camunda.zeebe.management.cluster.Operation;
import io.camunda.zeebe.management.cluster.Operation.OperationEnum;
import io.camunda.zeebe.management.cluster.PartitionState;
import io.camunda.zeebe.management.cluster.PartitionStateCode;
import io.camunda.zeebe.management.cluster.PostOperationResponse;
import io.camunda.zeebe.management.cluster.TopologyChange;
import io.camunda.zeebe.management.cluster.TopologyChange.StatusEnum;
import io.camunda.zeebe.management.cluster.TopologyChangeCompletedInner;
import io.camunda.zeebe.topology.api.ErrorResponse;
import io.camunda.zeebe.topology.api.TopologyChangeResponse;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.AddMembersRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.ScaleRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequestSender;
import io.camunda.zeebe.topology.state.ClusterChangePlan;
import io.camunda.zeebe.topology.state.ClusterChangePlan.CompletedOperation;
import io.camunda.zeebe.topology.state.ClusterChangePlan.Status;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.CompletedChange;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.PartitionState.State;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.util.Either;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Component
@RestControllerEndpoint(id = "cluster")
public class ClusterEndpoint {
  private final TopologyManagementRequestSender requestSender;

  @Autowired
  public ClusterEndpoint(final TopologyManagementRequestSender requestSender) {
    this.requestSender = requestSender;
  }

  @GetMapping(produces = "application/json")
  public ResponseEntity<?> clusterTopology() {
    try {
      final GetTopologyResponse response = mapClusterTopology(requestSender.getTopology().join());
      return new ResponseEntity<>(response, HttpStatusCode.valueOf(200));
    } catch (final Exception error) {
      return mapError(error);
    }
  }

  private ResponseEntity<Error> mapError(final Exception error) {
    // TODO: Map error to proper HTTP status code as defined in spec
    final var errorResponse = new Error();
    errorResponse.setMessage(error.getMessage());
    return ResponseEntity.status(500).body(errorResponse);
  }

  @PostMapping(path = "/{resource}/{id}")
  public ResponseEntity<?> add(
      @PathVariable("resource") final Resource resource, @PathVariable final int id) {
    return switch (resource) {
      case brokers -> mapOperationResponse(
          requestSender
              .addMembers(new AddMembersRequest(Set.of(new MemberId(String.valueOf(id)))))
              .join());
      case partitions -> ResponseEntity.status(501).body("Adding partitions is not supported");
    };
  }

  @DeleteMapping(path = "/{resource}/{id}")
  public ResponseEntity<?> remove(
      @PathVariable("resource") final Resource resource, @PathVariable final int id) {
    return switch (resource) {
      case brokers -> mapOperationResponse(
          requestSender
              .removeMembers(new RemoveMembersRequest(Set.of(new MemberId(String.valueOf(id)))))
              .join());
      case partitions -> ResponseEntity.status(501).body("Removing partitions is not supported");
    };
  }

  @PostMapping(path = "/{resource}", consumes = "application/json")
  public ResponseEntity<?> scale(
      @PathVariable("resource") final Resource resource, @RequestBody final List<Integer> ids) {
    return switch (resource) {
      case brokers -> scaleBrokers(ids);
      case partitions -> new ResponseEntity<>(
          "Scaling partitions is not supported", HttpStatusCode.valueOf(501));
    };
  }

  private ResponseEntity<?> scaleBrokers(final List<Integer> ids) {
    try {
      final var response =
          requestSender
              .scaleMembers(
                  new ScaleRequest(
                      ids.stream()
                          .map(String::valueOf)
                          .map(MemberId::from)
                          .collect(Collectors.toSet())))
              .join();
      return mapOperationResponse(response);
    } catch (final Exception error) {
      return mapError(error);
    }
  }

  @PostMapping(
      path = "/{resource}/{resourceId}/{subResource}/{subResourceId}",
      consumes = "application/json")
  public ResponseEntity<?> addSubResource(
      @PathVariable("resource") final Resource resource,
      @PathVariable final int resourceId,
      @PathVariable("subResource") final Resource subResource,
      @PathVariable final int subResourceId,
      @RequestBody final PartitionAddRequest request) {
    final int priority = request.priority();
    return switch (resource) {
      case brokers -> switch (subResource) {
          // POST /cluster/brokers/1/partitions/2
        case partitions -> mapOperationResponse(
            requestSender
                .joinPartition(
                    new JoinPartitionRequest(
                        MemberId.from(String.valueOf(resourceId)), subResourceId, priority))
                .join());
        case brokers -> new ResponseEntity<>(HttpStatusCode.valueOf(404));
      };
      case partitions -> switch (subResource) {
          // POST /cluster/partitions/2/brokers/1
        case brokers -> mapOperationResponse(
            requestSender
                .joinPartition(
                    new JoinPartitionRequest(
                        MemberId.from(String.valueOf(subResourceId)), resourceId, priority))
                .join());
        case partitions -> new ResponseEntity<>(HttpStatusCode.valueOf(404));
      };
    };
  }

  @DeleteMapping(
      path = "/{resource}/{resourceId}/{subResource}/{subResourceId}",
      consumes = "application/json")
  public ResponseEntity<?> removeSubResource(
      @PathVariable("resource") final Resource resource,
      @PathVariable final int resourceId,
      @PathVariable("subResource") final Resource subResource,
      @PathVariable final int subResourceId) {
    return switch (resource) {
      case brokers -> switch (subResource) {
        case partitions -> mapOperationResponse(
            requestSender
                .leavePartition(
                    new LeavePartitionRequest(
                        MemberId.from(String.valueOf(resourceId)), subResourceId))
                .join());
        case brokers -> new ResponseEntity<>(HttpStatusCode.valueOf(404));
      };
      case partitions -> switch (subResource) {
        case brokers -> mapOperationResponse(
            requestSender
                .leavePartition(
                    new LeavePartitionRequest(
                        MemberId.from(String.valueOf(subResourceId)), resourceId))
                .join());
        case partitions -> new ResponseEntity<>(HttpStatusCode.valueOf(404));
      };
    };
  }

  private ResponseEntity<?> mapOperationResponse(
      final Either<ErrorResponse, TopologyChangeResponse> response) {
    if (response.isRight()) {
      return ResponseEntity.status(202).body(mapResponseType(response.get()));
    } else {
      final var errorCode =
          switch (response.getLeft().code()) {
            case INVALID_REQUEST, OPERATION_NOT_ALLOWED -> 400;
            case CONCURRENT_MODIFICATION -> 409;
            case INTERNAL_ERROR -> 500;
          };
      final var error = new Error();
      error.setMessage(response.getLeft().message());
      return ResponseEntity.status(errorCode).body(error);
    }
  }

  private static PostOperationResponse mapResponseType(final TopologyChangeResponse response) {
    return new PostOperationResponse()
        .changeId(response.changeId())
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
                    .version(entry.getValue().version())
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

  private static GetTopologyResponse mapClusterTopology(final ClusterTopology topology) {
    final var response = new GetTopologyResponse();
    final List<BrokerState> brokers = mapBrokerStates(topology.members());
    final TopologyChange change;

    if (topology.pendingChanges().isPresent()) {
      change = mapOngoingChange(topology.pendingChanges().get());
    } else if (topology.lastChange().isPresent()) {
      change = mapCompletedChange(topology.lastChange().get());
    } else {
      change = new TopologyChange();
    }

    response.version(topology.version()).brokers(brokers).change(change);

    return response;
  }

  private static TopologyChange mapCompletedChange(final CompletedChange completedChange) {
    return new TopologyChange()
        .id(completedChange.id())
        .status(mapChangeStatus(completedChange.status()))
        .startedAt(completedChange.startedAt().atOffset(ZoneOffset.UTC))
        .completedAt(completedChange.completedAt().atOffset(ZoneOffset.UTC));
  }

  private static TopologyChange mapOngoingChange(final ClusterChangePlan clusterChangePlan) {
    return new TopologyChange()
        .id(clusterChangePlan.id())
        .status(mapChangeStatus(clusterChangePlan.status()))
        .pending(mapOperations(clusterChangePlan.pendingOperations()))
        .completed(mapCompletedOperations(clusterChangePlan.completedOperations()));
  }

  private static StatusEnum mapChangeStatus(final Status status) {
    return switch (status) {
      case IN_PROGRESS -> StatusEnum.IN_PROGRESS;
      case COMPLETED -> StatusEnum.COMPLETED;
      case FAILED -> StatusEnum.FAILED;
    };
  }

  private static List<TopologyChangeCompletedInner> mapCompletedOperations(
      final List<CompletedOperation> completedOperations) {
    return completedOperations.stream().map(ClusterEndpoint::mapCompletedOperation).toList();
  }

  private static TopologyChangeCompletedInner mapCompletedOperation(
      final CompletedOperation operation) {
    final var mappedOperation =
        switch (operation.operation()) {
          case final MemberJoinOperation join -> new TopologyChangeCompletedInner()
              .operation(TopologyChangeCompletedInner.OperationEnum.BROKER_ADD)
              .brokerId(Integer.parseInt(join.memberId().id()));
          case final MemberLeaveOperation leave -> new TopologyChangeCompletedInner()
              .operation(TopologyChangeCompletedInner.OperationEnum.BROKER_REMOVE)
              .brokerId(Integer.parseInt(leave.memberId().id()));
          case final PartitionJoinOperation join -> new TopologyChangeCompletedInner()
              .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_JOIN)
              .brokerId(Integer.parseInt(join.memberId().id()))
              .partitionId(join.partitionId())
              .priority(join.priority());
          case final PartitionLeaveOperation leave -> new TopologyChangeCompletedInner()
              .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_LEAVE)
              .brokerId(Integer.parseInt(leave.memberId().id()))
              .partitionId(leave.partitionId());
          case final PartitionReconfigurePriorityOperation
          reconfigure -> new TopologyChangeCompletedInner()
              .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_RECONFIGURE_PRIORITY)
              .brokerId(Integer.parseInt(reconfigure.memberId().id()))
              .partitionId(reconfigure.partitionId())
              .priority(reconfigure.priority());
        };

    mappedOperation.completedAt(operation.completedAt().atOffset(ZoneOffset.UTC));

    return mappedOperation;
  }

  public record PartitionAddRequest(int priority) {}

  public enum Resource {
    brokers,
    partitions;
  }
}
