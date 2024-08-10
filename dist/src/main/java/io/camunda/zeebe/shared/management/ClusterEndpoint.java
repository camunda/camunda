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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.CancelChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.management.cluster.Error;
import java.util.List;
import java.util.Optional;
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
      final ScaleRequest scaleRequest =
          new ScaleRequest(
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

  public record PartitionAddRequest(int priority) {}

  public enum Resource {
    brokers,
    partitions,
    changes
  }
}
