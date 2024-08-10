/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

public final class PartitionTransitionTestArgumentProviders {

  static final class TransitionsThatShouldCloseService implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(final ExtensionContext extensionContext)
        throws Exception {
      return Stream.of(
          Arguments.of(Role.FOLLOWER, Role.LEADER),
          Arguments.of(Role.CANDIDATE, Role.LEADER),
          Arguments.of(Role.LEADER, Role.FOLLOWER),
          Arguments.of(Role.LEADER, Role.CANDIDATE),
          Arguments.of(Role.LEADER, Role.INACTIVE),
          Arguments.of(Role.FOLLOWER, Role.INACTIVE),
          Arguments.of(Role.CANDIDATE, Role.INACTIVE));
    }
  }

  static final class TransitionsThatShouldInstallService implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(final ExtensionContext extensionContext)
        throws Exception {
      return Stream.of(
          Arguments.of(null, Role.FOLLOWER),
          Arguments.of(null, Role.LEADER),
          Arguments.of(null, Role.CANDIDATE),
          Arguments.of(Role.FOLLOWER, Role.LEADER),
          Arguments.of(Role.CANDIDATE, Role.LEADER),
          Arguments.of(Role.LEADER, Role.FOLLOWER),
          Arguments.of(Role.LEADER, Role.CANDIDATE),
          Arguments.of(Role.INACTIVE, Role.FOLLOWER),
          Arguments.of(Role.INACTIVE, Role.LEADER),
          Arguments.of(Role.INACTIVE, Role.CANDIDATE));
    }
  }

  static final class TransitionsThatShouldDoNothing implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(final ExtensionContext extensionContext)
        throws Exception {
      return Stream.of(
          Arguments.of(Role.CANDIDATE, Role.FOLLOWER),
          Arguments.of(Role.FOLLOWER, Role.CANDIDATE),
          Arguments.of(null, Role.INACTIVE));
    }
  }
}
