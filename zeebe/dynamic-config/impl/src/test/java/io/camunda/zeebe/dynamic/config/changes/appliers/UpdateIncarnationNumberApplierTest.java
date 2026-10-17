/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;

import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class UpdateIncarnationNumberApplierTest {

  private final GlobalConfiguration globalConfiguration = GlobalConfiguration.init();

  @Test
  void shouldIncrementIncarnationNumber() {
    // given
    final var group =
        new PartitionGroupConfiguration(
            1, 5, Map.of(), Optional.empty(), Optional.empty(), Optional.empty());
    final var applier = new UpdateIncarnationNumberApplier();

    // when
    final var initResult = applier.init(globalConfiguration, group);
    final var resultingGroup = applier.apply().join().apply(group);

    // then
    assertThat(initResult).isRight();
    Assertions.assertThat(resultingGroup.incarnationNumber()).isEqualTo(6);
    Assertions.assertThat(resultingGroup.version()).isEqualTo(1);
  }
}
