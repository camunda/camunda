/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class UpdateIncarnationNumberApplierTest {

  @Test
  void shouldSucceedOnInit() {
    // given
    final MemberId memberId = MemberId.from("1");
    final var applier = new UpdateIncarnationNumberApplier();

    final ClusterConfiguration clusterConfiguration =
        ClusterConfiguration.init().addMember(memberId, MemberState.initializeAsActive(Map.of()));

    // when
    final var result = applier.init(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isRight();
  }

  @Test
  void shouldIncrementIncarnationNumberByOne() {
    // given
    final MemberId memberId = MemberId.from("1");
    final var applier = new UpdateIncarnationNumberApplier();

    final ClusterConfiguration initialConfiguration =
        ClusterConfiguration.builder()
            .from(
                ClusterConfiguration.init()
                    .addMember(memberId, MemberState.initializeAsActive(Map.of())))
            .incarnationNumber(5)
            .build();

    // when
    final var result = applier.apply();

    // then
    assertThat(result).succeedsWithin(Duration.ofMillis(100));
    final var updatedConfiguration = result.join().apply(initialConfiguration);
    assertThat(updatedConfiguration.incarnationNumber()).isEqualTo(6);
  }
}
