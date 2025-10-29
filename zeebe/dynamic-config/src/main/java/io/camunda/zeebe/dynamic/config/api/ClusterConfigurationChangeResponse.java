/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import com.google.common.collect.ImmutableSortedMap;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

public record ClusterConfigurationChangeResponse(
    long changeId,
    SortedMap<MemberId, MemberState> currentConfiguration,
    SortedMap<MemberId, MemberState> expectedConfiguration,
    List<ClusterConfigurationChangeOperation> plannedChanges) {

  public ClusterConfigurationChangeResponse(
      final long changeId,
      final Map<MemberId, MemberState> currentConfiguration,
      final Map<MemberId, MemberState> expectedConfiguration,
      final List<ClusterConfigurationChangeOperation> plannedChanges) {
    this(
        changeId,
        ImmutableSortedMap.copyOf(currentConfiguration),
        ImmutableSortedMap.copyOf(expectedConfiguration),
        plannedChanges);
  }
}
