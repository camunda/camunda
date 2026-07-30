/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.NodeIdProvider.S3;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

final class NodeIdProviderConfigurationUtilsTest {

  @Test
  void shouldUseConfiguredTaskIdWithoutQueryingEcs() {
    // given
    final var config = new S3();
    config.setTaskId("configured-task-id");
    config.setResolveTaskId(true);
    final var captured = new AtomicReference<Boolean>();
    final Function<Boolean, Optional<String>> resolver =
        flag -> {
          captured.set(flag);
          return Optional.of("ecs-task-id");
        };

    // when
    final var taskId = NodeIdProviderConfigurationUtils.resolveTaskId(config, resolver);

    // then the configured id wins and the resolver is never consulted
    assertThat(taskId).isEqualTo("configured-task-id");
    assertThat(captured).hasValue(null);
  }

  @Test
  void shouldPassResolveEnabledFlagToResolverAndUseItsResult() {
    // given
    final var config = new S3();
    config.setResolveTaskId(true);
    final var captured = new AtomicReference<Boolean>();
    final Function<Boolean, Optional<String>> resolver =
        flag -> {
          captured.set(flag);
          return Optional.of("ecs-task-id");
        };

    // when
    final var taskId = NodeIdProviderConfigurationUtils.resolveTaskId(config, resolver);

    // then the flag is forwarded and the resolved id is used
    assertThat(captured).hasValue(true);
    assertThat(taskId).isEqualTo("ecs-task-id");
  }

  @Test
  void shouldPassResolveDisabledFlagToResolverAndGenerateRandomTaskId() {
    // given
    final var config = new S3();
    config.setResolveTaskId(false);
    final var captured = new AtomicReference<Boolean>();
    final Function<Boolean, Optional<String>> resolver =
        flag -> {
          captured.set(flag);
          return Optional.empty();
        };

    // when
    final var taskId = NodeIdProviderConfigurationUtils.resolveTaskId(config, resolver);

    // then the disabled flag is forwarded and a random UUID is generated
    assertThat(captured).hasValue(false);
    assertThat(UUID.fromString(taskId)).isNotNull();
  }
}
