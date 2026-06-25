/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.membership;

import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.INVALID_GROUP_ID;
import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.NONE;

import io.camunda.eventbridge.consumergroups.state.group.ConsumerGroupQueryService;
import io.camunda.eventbridge.consumergroups.state.group.GroupSnapshot;
import io.camunda.eventbridge.consumergroups.state.offset.OffsetQueryService;
import io.camunda.eventbridge.protocol.request.coordination.DescribeGroupsRequest;
import io.camunda.eventbridge.protocol.request.coordination.DescribeGroupsResponse;
import io.camunda.eventbridge.protocol.request.coordination.OffsetFetchRequest;
import io.camunda.eventbridge.protocol.request.coordination.OffsetFetchResponse;
import io.camunda.eventbridge.protocol.transport.CoordinationResponseEncoder;
import io.camunda.zeebe.scheduler.Actor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Serves the read-only consumer-group requests — offset fetch and describe-groups — on its own
 * actor, separate from the {@link ConsumerGroupCoordinator}'s write/heartbeat path, so a
 * potentially heavy scan (all groups, or a group's whole offset map) never blocks the command
 * actor. It reads replicated state through its <em>own</em> {@link ConsumerGroupQueryService} /
 * {@link OffsetQueryService} (each on a private {@link io.camunda.zeebe.db.ZeebeDb} context), so it
 * shares no flyweights with the coordinator. Neither request writes to the log.
 *
 * <p>Like the coordinator, it returns the raw serialized response payload; the {@code
 * CoordinationRequestHandler} frames it for the broker client.
 */
public final class ConsumerGroupQueryHandler extends Actor {

  private final int partitionId;
  private final ConsumerGroupQueryService groupQuery;
  private final OffsetQueryService offsetQuery;

  public ConsumerGroupQueryHandler(
      final int partitionId,
      final ConsumerGroupQueryService groupQuery,
      final OffsetQueryService offsetQuery) {
    this.partitionId = partitionId;
    this.groupQuery = groupQuery;
    this.offsetQuery = offsetQuery;
  }

  @Override
  public String getName() {
    return "ConsumerGroupQueryHandler-" + partitionId;
  }

  /**
   * Serves an offset fetch: reads the group's committed offsets from state and returns the
   * serialized reply. With no partition filter the whole group is returned; with one, only those
   * partitions are (uncommitted ones as {@code -1}). An empty group id is rejected in the payload.
   */
  public CompletableFuture<byte[]> handleOffsetFetch(final OffsetFetchRequest request) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () -> {
          try {
            final var groupId = request.getGroupId();
            final var response = new OffsetFetchResponse();
            if (groupId == null || groupId.isEmpty()) {
              response.setErrorCode(INVALID_GROUP_ID);
            } else {
              response
                  .setErrorCode(NONE)
                  .setCommittedOffsets(
                      offsetQuery.committedOffsets(groupId, request.getPartitions()));
            }
            result.complete(CoordinationResponseEncoder.serialize(response));
          } catch (final RuntimeException e) {
            result.completeExceptionally(e);
          }
        });
    return result;
  }

  /**
   * Serves a describe-groups read: an empty {@code groupId} returns all groups on this shard; a set
   * one narrows to that group (empty if it lives on another shard or does not exist).
   */
  public CompletableFuture<byte[]> handleDescribeGroups(final DescribeGroupsRequest request) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () -> {
          try {
            final var groupId = request.getGroupId();
            final var response = new DescribeGroupsResponse();
            final List<GroupSnapshot> groups;
            if (groupId == null || groupId.isEmpty()) {
              groups = groupQuery.allGroups();
            } else {
              final var group = groupQuery.groupSnapshot(groupId);
              groups = group == null ? List.of() : List.of(group);
            }
            for (final var group : groups) {
              final var members = new LinkedHashMap<String, Long>();
              group.members().forEach((id, m) -> members.put(id, m.assignedEpoch()));
              response.addGroup(
                  description ->
                      description
                          .setGroupId(group.groupId())
                          .setState(group.state().name())
                          .setGroupEpoch(group.groupEpoch())
                          .setAssignmentEpoch(group.assignmentEpoch())
                          .setSubscriptions(group.subscriptions())
                          .setMembers(members));
            }
            result.complete(CoordinationResponseEncoder.serialize(response));
          } catch (final RuntimeException e) {
            result.completeExceptionally(e);
          }
        });
    return result;
  }
}
