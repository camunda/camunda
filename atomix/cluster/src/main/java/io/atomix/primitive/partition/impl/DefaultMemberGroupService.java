/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.primitive.partition.impl;

import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.primitive.partition.ManagedMemberGroupService;
import io.atomix.primitive.partition.MemberGroup;
import io.atomix.primitive.partition.MemberGroupEvent;
import io.atomix.primitive.partition.MemberGroupEventListener;
import io.atomix.primitive.partition.MemberGroupProvider;
import io.atomix.primitive.partition.MemberGroupService;
import io.atomix.utils.event.AbstractListenerManager;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Default member group service. */
public class DefaultMemberGroupService
    extends AbstractListenerManager<MemberGroupEvent, MemberGroupEventListener>
    implements ManagedMemberGroupService {

  private final AtomicBoolean started = new AtomicBoolean();
  private final ClusterMembershipService membershipService;
  private final MemberGroupProvider memberGroupProvider;
  private volatile Collection<MemberGroup> memberGroups;
  private final ClusterMembershipEventListener membershipEventListener = event -> recomputeGroups();

  public DefaultMemberGroupService(
      final ClusterMembershipService membershipService,
      final MemberGroupProvider memberGroupProvider) {
    this.membershipService = membershipService;
    this.memberGroupProvider = memberGroupProvider;
  }

  @Override
  public Collection<MemberGroup> getMemberGroups() {
    return memberGroups;
  }

  private void recomputeGroups() {
    memberGroups = memberGroupProvider.getMemberGroups(membershipService.getMembers());
  }

  @Override
  public CompletableFuture<MemberGroupService> start() {
    if (started.compareAndSet(false, true)) {
      memberGroups = memberGroupProvider.getMemberGroups(membershipService.getMembers());
      membershipService.addListener(membershipEventListener);
    }
    return CompletableFuture.completedFuture(this);
  }

  @Override
  public boolean isRunning() {
    return started.get();
  }

  @Override
  public CompletableFuture<Void> stop() {
    if (started.compareAndSet(true, false)) {
      membershipService.removeListener(membershipEventListener);
    }
    return CompletableFuture.completedFuture(null);
  }
}
