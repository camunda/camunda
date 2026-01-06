/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.AddMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.BrokerScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.CancelChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterPatchRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ForceRemoveBrokersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.PurgeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdateRoutingStateRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.ActivePartitions;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestBrokers;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestPartitions;
import io.camunda.zeebe.management.cluster.Error;
import io.camunda.zeebe.management.cluster.MessageCorrelationHashMod;
import io.camunda.zeebe.management.cluster.RequestHandlingActivePartitions;
import io.camunda.zeebe.management.cluster.RequestHandlingAllPartitions;
import io.camunda.zeebe.management.cluster.RoutingState;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Component
@RestControllerEndpoint(id = "cluster")
public class ClusterEndpoint {
  private final ClusterConfigurationManagementRequestSender requestSender;

  @Autowired
  public ClusterEndpoint(final ClusterConfigurationManagementRequestSender requestSender) {
    this.requestSender = requestSender;
  }

  @GetMapping(produces = "application/json")
  public ResponseEntity<?> clusterTopology() {
    try {
      return ClusterApiUtils.mapClusterTopologyResponse(requestSender.getTopology().join());
    } catch (final Exception error) {
      return ClusterApiUtils.mapError(error);
    }
  }

  private ResponseEntity<Error> invalidRequest(final String message) {
    final var errorResponse = new Error();
    errorResponse.setMessage(message);
    return ResponseEntity.status(400).body(errorResponse);
  }

  @PostMapping(path = "/{resource}/{id}")
  public ResponseEntity<?> add(
      @PathVariable("resource") final Resource resource,
      @PathVariable final int id,
      @RequestParam(defaultValue = "false") final boolean dryRun) {
    return switch (resource) {
      case brokers ->
          ClusterApiUtils.mapOperationResponse(
              requestSender
                  .addMembers(
                      new AddMembersRequest(Set.of(new MemberId(String.valueOf(id))), dryRun))
                  .join());
      case partitions -> ResponseEntity.status(501).body("Adding partitions is not supported");
      case changes ->
          ResponseEntity.status(501)
              .body(
                  "Changing cluster directly is not supported. Use POST /cluster/brokers for scaling the cluster");
    };
  }

  @DeleteMapping(path = "/{resource}/{id}")
  public ResponseEntity<?> remove(
      @PathVariable("resource") final Resource resource,
      @PathVariable final String id,
      @RequestParam(defaultValue = "false") final boolean dryRun) {
    return switch (resource) {
      case brokers ->
          ClusterApiUtils.mapOperationResponse(
              requestSender
                  .removeMembers(new RemoveMembersRequest(Set.of(new MemberId(id)), dryRun))
                  .join());
      case partitions -> ResponseEntity.status(501).body("Removing partitions is not supported");
      case changes -> {
        if (dryRun) {
          yield ResponseEntity.status(501).body("Dry run is not supported for cancelling changes");
        } else {
          yield cancelChange(id);
        }
      }
    };
  }

  /**
   * Cancels a change with the given id. This is a dangerous operation and should only be used when
   * the change is stuck and cannot make progress on its own. Cancelling a change will not revert
   * already applied operations, so the cluster will be in an intermediate state with partially
   * applied changes. For example, a partition might have been added to a broker, but not removed
   * from another one; so it has a higher number of replicas than the configured value. In another
   * case, the configuration in raft might differ from what is reflected in the ClusterTopology. So
   * a manual intervention would be required to clean up the state.
   */
  private ResponseEntity<?> cancelChange(final String changeId) {
    try {
      return ClusterApiUtils.mapClusterTopologyResponse(
          requestSender
              .cancelTopologyChange(new CancelChangeRequest(Long.parseLong(changeId)))
              .join());
    } catch (final NumberFormatException ignore) {
      return invalidRequest("Change id must be a number");
    } catch (final Exception error) {
      return ClusterApiUtils.mapError(error);
    }
  }

  @PostMapping(path = "/{resource}", consumes = "application/json")
  public ResponseEntity<?> scale(
      @PathVariable("resource") final Resource resource,
      @RequestBody final List<Integer> ids,
      @RequestParam(defaultValue = "false") final boolean dryRun,
      @RequestParam(defaultValue = "false") final boolean force,
      @RequestParam final Optional<Integer> replicationFactor) {
    return switch (resource) {
      case brokers -> scaleBrokers(ids, dryRun, force, replicationFactor);
      case partitions ->
          new ResponseEntity<>("Scaling partitions is not supported", HttpStatusCode.valueOf(501));
      case changes ->
          ResponseEntity.status(501)
              .body(
                  "Changing cluster directly is not supported. Use POST /cluster/brokers for scaling the cluster");
    };
  }

  private ResponseEntity<?> scaleBrokers(
      final List<Integer> ids,
      final boolean dryRun,
      final boolean force,
      final Optional<Integer> replicationFactor) {
    try {
      final BrokerScaleRequest scaleRequest =
          new BrokerScaleRequest(
              ids.stream().map(String::valueOf).map(MemberId::from).collect(Collectors.toSet()),
              replicationFactor,
              dryRun);
      // here we assume, if it force request it is always force scale down. The coordinator will
      // reject the request if that is not the case.
      final var response =
          force
              ? requestSender.forceScaleDown(scaleRequest).join()
              : requestSender.scaleMembers(scaleRequest).join();
      return ClusterApiUtils.mapOperationResponse(response);
    } catch (final Exception error) {
      return ClusterApiUtils.mapError(error);
    }
  }

  @PatchMapping(consumes = "application/json", produces = "application/json")
  public ResponseEntity<?> updateClusterConfiguration(
      @RequestParam(defaultValue = "false") final boolean dryRun,
      @RequestParam(defaultValue = "false") final boolean force,
      @RequestBody final ClusterConfigPatchRequest request) {
    try {
      final var brokers = request.getBrokers();
      final var partitions = request.getPartitions();
      if (force) {
        return forceRemoveBrokers(dryRun, brokers, partitions);
      }

      final boolean isScale = brokers != null && brokers.getCount() != null;
      final boolean shouldAddBrokers =
          brokers != null && brokers.getAdd() != null && !brokers.getAdd().isEmpty();
      final boolean shouldRemoveBrokers =
          brokers != null && brokers.getRemove() != null && !brokers.getRemove().isEmpty();
      if (isScale && (shouldAddBrokers || shouldRemoveBrokers)) {
        return invalidRequest(
            "Cannot change brokers count and add/remove brokers at the same time. Specify either the new brokers count or brokers to add and remove.");
      }

      final Optional<Integer> newPartitionCount =
          Optional.ofNullable(partitions).map(ClusterConfigPatchRequestPartitions::getCount);
      final Optional<Integer> newReplicationFactor =
          Optional.ofNullable(partitions)
              .map(ClusterConfigPatchRequestPartitions::getReplicationFactor);

      if (isScale) {
        final var scaleRequest =
            new ClusterScaleRequest(
                Optional.of(brokers.getCount()), newPartitionCount, newReplicationFactor, dryRun);
        return ClusterApiUtils.mapOperationResponse(
            requestSender.scaleCluster(scaleRequest).join());
      } else {
        return patchCluster(dryRun, request, brokers, newPartitionCount, newReplicationFactor);
      }

    } catch (final Exception error) {
      return ClusterApiUtils.mapError(error);
    }
  }

  private ResponseEntity<?> patchCluster(
      final boolean dryRun,
      final ClusterConfigPatchRequest request,
      final ClusterConfigPatchRequestBrokers brokers,
      final Optional<Integer> newPartitionCount,
      final Optional<Integer> newReplicationFactor) {
    final Set<MemberId> brokersToAdd =
        brokers != null
            ? request.getBrokers().getAdd().stream()
                .map(String::valueOf)
                .map(MemberId::from)
                .collect(Collectors.toSet())
            : Set.of();
    final Set<MemberId> brokersToRemove =
        brokers != null
            ? request.getBrokers().getRemove().stream()
                .map(String::valueOf)
                .map(MemberId::from)
                .collect(Collectors.toSet())
            : Set.of();
    final var patchRequest =
        new ClusterPatchRequest(
            brokersToAdd, brokersToRemove, newPartitionCount, newReplicationFactor, dryRun);

    return ClusterApiUtils.mapOperationResponse(requestSender.patchCluster(patchRequest).join());
  }

  private ResponseEntity<?> forceRemoveBrokers(
      final boolean dryRun,
      final ClusterConfigPatchRequestBrokers brokers,
      final ClusterConfigPatchRequestPartitions partitions) {

    if (brokers == null) {
      return invalidRequest("Must provide a set of brokers to force remove.");
    }

    if (brokers.getCount() != null) {
      return invalidRequest("Cannot force change the broker count.");
    }
    if (brokers.getAdd() != null && !brokers.getAdd().isEmpty()) {
      return invalidRequest("Cannot force add brokers");
    }

    if (partitions != null) {
      if (partitions.getCount() != null) {
        return invalidRequest("Cannot force change the partition count.");
      }
      if (partitions.getReplicationFactor() != null) {
        return invalidRequest("Cannot force change the replication factor.");
      }
    }

    if (brokers.getRemove() == null || brokers.getRemove().isEmpty()) {
      return invalidRequest("Must provide a set of brokers to force remove.");
    }

    final var forceRemoveRequest =
        new ForceRemoveBrokersRequest(
            brokers.getRemove().stream()
                .map(String::valueOf)
                .map(MemberId::from)
                .collect(Collectors.toSet()),
            dryRun);
    return ClusterApiUtils.mapOperationResponse(
        requestSender.forceRemoveBrokers(forceRemoveRequest).join());
  }

  @PostMapping(
      path = "/{resource}/{resourceId}/{subResource}/{subResourceId}",
      consumes = "application/json")
  public ResponseEntity<?> addSubResource(
      @PathVariable("resource") final Resource resource,
      @PathVariable final int resourceId,
      @PathVariable("subResource") final Resource subResource,
      @PathVariable final int subResourceId,
      @RequestBody final PartitionAddRequest request,
      @RequestParam(defaultValue = "false") final boolean dryRun) {
    final int priority = request.priority();
    return switch (resource) {
      case brokers ->
          switch (subResource) {
            // POST /cluster/brokers/1/partitions/2
            case partitions ->
                ClusterApiUtils.mapOperationResponse(
                    requestSender
                        .joinPartition(
                            new JoinPartitionRequest(
                                MemberId.from(String.valueOf(resourceId)),
                                subResourceId,
                                priority,
                                dryRun))
                        .join());
            case brokers, changes -> new ResponseEntity<>(HttpStatusCode.valueOf(404));
          };
      case partitions ->
          switch (subResource) {
            // POST /cluster/partitions/2/brokers/1
            case brokers ->
                ClusterApiUtils.mapOperationResponse(
                    requestSender
                        .joinPartition(
                            new JoinPartitionRequest(
                                MemberId.from(String.valueOf(subResourceId)),
                                resourceId,
                                priority,
                                dryRun))
                        .join());
            case partitions, changes -> new ResponseEntity<>(HttpStatusCode.valueOf(404));
          };
      case changes -> new ResponseEntity<>(HttpStatusCode.valueOf(404));
    };
  }

  @DeleteMapping(
      path = "/{resource}/{resourceId}/{subResource}/{subResourceId}",
      consumes = "application/json")
  public ResponseEntity<?> removeSubResource(
      @PathVariable("resource") final Resource resource,
      @PathVariable final int resourceId,
      @PathVariable("subResource") final Resource subResource,
      @PathVariable final int subResourceId,
      @RequestParam(defaultValue = "false") final boolean dryRun) {
    return switch (resource) {
      case brokers ->
          switch (subResource) {
            case partitions ->
                ClusterApiUtils.mapOperationResponse(
                    requestSender
                        .leavePartition(
                            new LeavePartitionRequest(
                                MemberId.from(String.valueOf(resourceId)), subResourceId, dryRun))
                        .join());
            case brokers, changes -> new ResponseEntity<>(HttpStatusCode.valueOf(404));
          };
      case partitions ->
          switch (subResource) {
            case brokers ->
                ClusterApiUtils.mapOperationResponse(
                    requestSender
                        .leavePartition(
                            new LeavePartitionRequest(
                                MemberId.from(String.valueOf(subResourceId)), resourceId, dryRun))
                        .join());
            case partitions, changes -> new ResponseEntity<>(HttpStatusCode.valueOf(404));
          };
      case changes -> new ResponseEntity<>(HttpStatusCode.valueOf(404));
    };
  }

  @PostMapping(path = "/purge", produces = "application/json")
  public CompletableFuture<ResponseEntity<?>> purge(
      @RequestParam(defaultValue = "false") final boolean dryRun) {
    try {
      return requestSender
          .purge(new PurgeRequest(dryRun))
          .thenApply(ClusterApiUtils::mapOperationResponse)
          .exceptionally(ClusterApiUtils::mapError);
    } catch (final Exception error) {
      return CompletableFuture.completedFuture(ClusterApiUtils.mapError(error));
    }
  }

  @PatchMapping(path = "/routing-state", consumes = "application/json")
  public ResponseEntity<?> updateRoutingState(
      @RequestBody(required = false) final RoutingState routingState,
      @RequestParam(defaultValue = "false") final boolean dryRun) {
    try {
      Optional<io.camunda.zeebe.dynamic.config.state.RoutingState> internalRoutingState =
          Optional.empty();
      if (routingState != null) {
        final RequestHandling requestHandling =
            switch (routingState.getRequestHandling()) {
              case final RequestHandlingAllPartitions req ->
                  new AllPartitions(req.getPartitionCount());
              case final RequestHandlingActivePartitions req ->
                  new ActivePartitions(
                      req.getBasePartitionCount(),
                      new HashSet<>(req.getAdditionalActivePartitions()),
                      new HashSet<>(req.getInactivePartitions()));
              default ->
                  throw new IllegalStateException(
                      "Unexpected value: " + routingState.getRequestHandling());
            };
        final MessageCorrelation messageCorrelation =
            switch (routingState.getMessageCorrelation()) {
              case final MessageCorrelationHashMod req -> new HashMod(req.getPartitionCount());
              default ->
                  throw new IllegalStateException(
                      "Unexpected value: " + routingState.getMessageCorrelation());
            };
        internalRoutingState =
            Optional.of(
                new io.camunda.zeebe.dynamic.config.state.RoutingState(
                    Optional.ofNullable(routingState.getVersion()).orElse(0L),
                    requestHandling,
                    messageCorrelation));
      }
      final var updateRequest = new UpdateRoutingStateRequest(internalRoutingState, dryRun);
      return ClusterApiUtils.mapOperationResponse(
          requestSender.updateRoutingState(updateRequest).join());
    } catch (final Exception error) {
      return ClusterApiUtils.mapError(error);
    }
  }

  public record PartitionAddRequest(int priority) {}

  public enum Resource {
    brokers,
    partitions,
    changes
  }
}
