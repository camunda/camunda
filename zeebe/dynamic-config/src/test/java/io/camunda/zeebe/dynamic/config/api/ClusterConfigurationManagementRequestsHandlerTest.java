/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeResult;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.util.RequestValidatorRegistry.RequestValidator;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ClusterConfigurationManagementRequestsHandlerTest {

  private static final MemberId LOCAL_MEMBER = MemberId.from("0");

  private final TestConcurrencyControl executor = new TestConcurrencyControl();
  private final ConfigurationChangeCoordinator coordinator =
      mock(ConfigurationChangeCoordinator.class);

  private ClusterConfigurationManagementRequestsHandler handler(final RequestValidator validator) {
    return new ClusterConfigurationManagementRequestsHandler(
        coordinator, LOCAL_MEMBER, executor, validator);
  }

  private static RestoreRequest restoreRequest() {
    return new RestoreRequest(List.of(1L), null, null, "elasticsearch", false, false);
  }

  private static Throwable failureOf(final ActorFuture<?> future) {
    final var error = new AtomicReference<Throwable>();
    future.onComplete((ignored, throwable) -> error.set(throwable));
    return error.get();
  }

  @Test
  void shouldPropagateValidationMessageAsInvalidRequest() {
    // given
    final var handler =
        handler(
            request -> {
              throw new IllegalArgumentException("backupId and time range are mutually exclusive");
            });

    // when
    final var failure = failureOf(handler.restore(restoreRequest()));

    // then the validator's error message is propagated to the caller as an invalid request
    assertThat(failure)
        .isInstanceOf(InvalidRequest.class)
        .hasMessage("backupId and time range are mutually exclusive");
    // and the request never reaches the coordinator
    verifyNoInteractions(coordinator);
  }

  @Test
  void shouldPropagateNonArgumentValidationExceptionUnwrapped() {
    // given
    final var exception = new IllegalStateException("backup store unavailable");
    final var handler =
        handler(
            request -> {
              throw exception;
            });

    // when
    final var failure = failureOf(handler.restore(restoreRequest()));

    // then the original exception is propagated as-is
    assertThat(failure).isSameAs(exception);
    verifyNoInteractions(coordinator);
  }

  @Test
  void shouldProceedWithNoValidation() {
    // given a no-op validator (validation is completely optional)
    final var config = ClusterConfiguration.init();
    when(coordinator.applyOperations(any()))
        .thenReturn(
            CompletableActorFuture.completed(
                new ConfigurationChangeResult(config, config, 1L, List.of())));
    final var handler = handler(request -> request);

    // when
    final ActorFuture<ClusterConfigurationChangeResponse> result =
        handler.restore(restoreRequest());

    // then the request is handled normally
    assertThat(failureOf(result)).isNull();
    assertThat(Objects.requireNonNull(result.join()).changeId()).isOne();
    verify(coordinator).applyOperations(any());
  }

  @Test
  void shouldReceiveTheOriginalRequestInstanceForValidation() {
    // given
    final var request = restoreRequest();
    final var received = new AtomicReference<ClusterConfigurationManagementRequest>();
    final var handler =
        handler(
            r -> {
              received.set(r);
              return r;
            });
    when(coordinator.applyOperations(any()))
        .thenReturn(
            CompletableActorFuture.completed(
                new ConfigurationChangeResult(
                    ClusterConfiguration.init(), ClusterConfiguration.init(), 1L, List.of())));

    // when
    handler.restore(request);

    // then the exact request instance is handed to the validator
    assertThat(received.get()).isSameAs(request);
  }
}
