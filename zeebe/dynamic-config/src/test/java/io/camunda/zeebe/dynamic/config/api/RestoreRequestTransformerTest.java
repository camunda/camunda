/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.ConcurrentModificationException;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InternalError;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidState;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.util.RequestValidatorRegistry;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

final class RestoreRequestTransformerTest {

  private static final MemberId MEMBER = MemberId.from("0");

  private static RestoreRequest restoreRequest() {
    return new RestoreRequest(
        PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
        List.of(1L),
        null,
        null,
        "elasticsearch",
        false,
        false);
  }

  private static ClusterConfiguration recoveringTopology() {
    return ClusterConfiguration.init()
        .addMember(MEMBER, MemberState.initializeAsActive(Map.of()).toRecovering());
  }

  private static ClusterConfigurationRequestValidator<RestoreRequest, RestoreRequest>
      validatorReturning(final Either<Exception, RestoreRequest> result) {
    return new ClusterConfigurationRequestValidator<>() {
      @Override
      public Class<RestoreRequest> requestType() {
        return RestoreRequest.class;
      }

      @Override
      public Either<Exception, RestoreRequest> validate(final RestoreRequest request) {
        return result;
      }
    };
  }

  private static RequestValidatorRegistry registryWithValidator(
      final ClusterConfigurationRequestValidator<RestoreRequest, RestoreRequest> validator) {
    final var registry = new RequestValidatorRegistry();
    registry.registerValidator(null, validator);
    return registry;
  }

  @Test
  void shouldRejectWhenClusterIsNotRecovering() {
    // given a cluster that is not in recovery, even though a validator would accept the request
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(validatorReturning(Either.right(restoreRequest()))));

    // when
    final var result = transformer.operations(ClusterConfiguration.init());

    // then
    EitherAssert.assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ConcurrentModificationException.class);
  }

  @Test
  void shouldRejectWhenNoValidatorIsRegistered() {
    // given
    final var transformer =
        new RestoreRequestTransformer(restoreRequest(), new RequestValidatorRegistry());

    // when
    final var result = transformer.operations(recoveringTopology());

    // then
    EitherAssert.assertThat(result).isLeft().left().isInstanceOf(InternalError.class);
  }

  @Test
  void shouldPropagateValidatorRejection() {
    // given
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(
                validatorReturning(
                    Either.left(
                        new InvalidRequest("backupId and time range are mutually exclusive")))));

    // when
    final var result = transformer.operations(recoveringTopology());

    // then
    EitherAssert.assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(InvalidRequest.class)
        .extracting(Exception::getMessage)
        .isEqualTo("backupId and time range are mutually exclusive");
  }

  @Test
  void shouldMapIllegalArgumentExceptionToInvalidRequest() {
    // given - the registered validator reports malformed requests as plain exceptions
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(
                validatorReturning(
                    Either.left(new IllegalArgumentException("bad request parameters")))));

    // when
    final var result = transformer.operations(recoveringTopology());

    // then
    EitherAssert.assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(InvalidRequest.class)
        .extracting(Exception::getMessage)
        .isEqualTo("bad request parameters");
  }

  @Test
  void shouldMapIllegalStateExceptionToInvalidState() {
    // given
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(
                validatorReturning(
                    Either.left(new IllegalStateException("no common checkpoint")))));

    // when
    final var result = transformer.operations(recoveringTopology());

    // then
    EitherAssert.assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(InvalidState.class)
        .extracting(Exception::getMessage)
        .isEqualTo("no common checkpoint");
  }

  @Test
  void shouldMapNoSuchElementExceptionToNotFound() {
    // given
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(
                validatorReturning(Either.left(new NoSuchElementException("backup not found")))));

    // when
    final var result = transformer.operations(recoveringTopology());

    // then
    EitherAssert.assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(NotFound.class)
        .extracting(Exception::getMessage)
        .isEqualTo("backup not found");
  }

  @Test
  void shouldMapUnrecognizedExceptionToInternalError() {
    // given - a failure mode the validator was not expected to produce
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(
                validatorReturning(Either.left(new RuntimeException("unexpected")))));

    // when
    final var result = transformer.operations(recoveringTopology());

    // then
    EitherAssert.assertThat(result).isLeft().left().isInstanceOf(InternalError.class);
  }

  @Test
  void shouldProduceEmptyPlanWhenValidatorAcceptsAndClusterIsRecovering() {
    // given
    final var request = restoreRequest();
    final var transformer =
        new RestoreRequestTransformer(
            request, registryWithValidator(validatorReturning(Either.right(request))));

    // when
    final var result = transformer.operations(recoveringTopology());

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).isEmpty();
  }
}
