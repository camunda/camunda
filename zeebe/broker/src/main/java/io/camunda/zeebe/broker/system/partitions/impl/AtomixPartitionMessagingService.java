/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.utils.serializer.serializers.DefaultSerializers;
import io.camunda.zeebe.broker.system.partitions.PartitionMessagingService;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class AtomixPartitionMessagingService implements PartitionMessagingService {
  private final ClusterCommunicationService communicationService;
  private final ClusterMembershipService clusterMembershipService;
  private final Supplier<Collection<MemberId>> partitionMembers;
  private final MemberId localMember;

  public AtomixPartitionMessagingService(
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService clusterMembershipService,
      final Supplier<Collection<MemberId>> partitionMembers) {
    localMember = clusterMembershipService.getLocalMember().id();
    this.communicationService = communicationService;
    this.clusterMembershipService = clusterMembershipService;
    this.partitionMembers = partitionMembers;
  }

  @Override
  public void subscribe(
      final String subject, final Consumer<ByteBuffer> consumer, final Executor executor) {
    communicationService.consume(subject, DefaultSerializers.BASIC::decode, consumer, executor);
  }

  @Override
  public void broadcast(final String subject, final ByteBuffer payload) {
    final var reachableMembers =
        partitionMembers.get().stream()
            .filter(memberId -> !memberId.equals(localMember))
            .filter(this::isReachable)
            .collect(Collectors.toUnmodifiableSet());
    communicationService.multicast(
        subject, payload, DefaultSerializers.BASIC::encode, reachableMembers, true);
  }

  @Override
  public void unsubscribe(final String subject) {
    communicationService.unsubscribe(subject);
  }

  private boolean isReachable(final MemberId memberId) {
    return Optional.ofNullable(clusterMembershipService.getMember(memberId))
        .map(Member::isReachable)
        .orElse(false);
  }
}
