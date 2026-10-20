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
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record ClusterConfigurationChangeResponse(
    long changeId,
    LegacyConfigurationChangeResponse legacyResponse,
    // The new multi-partition-group configuration, absent for callers that don't populate it
    // (e.g. not yet carried over the wire by a peer). legacyResponse is always required, for
    // backwards compatibility.
    @Nullable CurrentConfigurationChangeResponse response) {

  public ClusterConfigurationChangeResponse {
    Objects.requireNonNull(legacyResponse, "legacyResponse must not be null");
  }

  public record LegacyConfigurationChangeResponse(
      SortedMap<MemberId, MemberState> currentConfiguration,
      SortedMap<MemberId, MemberState> expectedConfiguration,
      List<ClusterConfigurationChangeOperation> plannedChanges) {

    public LegacyConfigurationChangeResponse(
        final Map<MemberId, MemberState> currentConfiguration,
        final Map<MemberId, MemberState> expectedConfiguration,
        final List<ClusterConfigurationChangeOperation> plannedChanges) {
      this(
          ImmutableSortedMap.copyOf(currentConfiguration),
          ImmutableSortedMap.copyOf(expectedConfiguration),
          plannedChanges);
    }
  }

  public record CurrentConfigurationChangeResponse(
      CurrentClusterConfiguration currentConfiguration,
      CurrentClusterConfiguration expectedConfiguration,
      List<Phase> phases) {

    public CurrentConfigurationChangeResponse {
      Objects.requireNonNull(currentConfiguration, "currentConfiguration must not be null");
      Objects.requireNonNull(expectedConfiguration, "expectedConfiguration must not be null");
      Objects.requireNonNull(phases, "phases must not be null");
    }
  }
}
