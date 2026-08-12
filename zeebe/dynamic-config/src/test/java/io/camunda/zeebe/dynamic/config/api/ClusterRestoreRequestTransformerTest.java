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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterRestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreParameters;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreResolvedRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.TenantRestoreArguments;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.util.RequestValidatorRegistry;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

final class ClusterRestoreRequestTransformerTest {

  private static final MemberId MEMBER = MemberId.from("0");
  private static final String TENANT_B = "tenant-b";

  @Test
  void shouldRestoreTheRequestedPhysicalTenantFromItsOwnParameters() {
    // given — a request naming exactly one physical tenant
    final var request =
        new ClusterRestoreRequest(
            Map.of(
                TENANT_B,
                new TenantRestoreArguments(
                    new RestoreParameters(List.of(55L), null, null), "elasticsearch", false)),
            false);
    final var transformer = new ClusterRestoreRequestTransformer(request, registry());

    // when
    final var result = transformer.operations(recoveringConfiguration());

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .contains(new PartitionRestoreOperation(MEMBER, 1, new TreeSet<>(List.of(55L))));
  }

  @Test
  void shouldRejectARestoreOfEveryPhysicalTenant() {
    // given a request naming no physical tenant
    final var request = new ClusterRestoreRequest(Map.of(), false);
    final var transformer = new ClusterRestoreRequestTransformer(request, registry());

    // when
    final var result = transformer.operations(recoveringConfiguration());

    // then — fanning the restore out over the partition groups is not implemented yet
    EitherAssert.assertThat(result).isLeft().left().isInstanceOf(InvalidRequest.class);
  }

  @Test
  void shouldRejectARestoreNamingMoreThanOnePhysicalTenant() {
    // given a request naming two physical tenants at once
    final var args =
        new TenantRestoreArguments(
            new RestoreParameters(List.of(55L), null, null), "elasticsearch", false);
    final var request = new ClusterRestoreRequest(Map.of(TENANT_B, args, "tenant-c", args), false);
    final var transformer = new ClusterRestoreRequestTransformer(request, registry());

    // when
    final var result = transformer.operations(recoveringConfiguration());

    // then — fanning the restore out over the partition groups is not implemented yet
    EitherAssert.assertThat(result).isLeft().left().isInstanceOf(InvalidRequest.class);
  }

  private static ClusterConfiguration recoveringConfiguration() {
    final var partitions = Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init()));
    return ClusterConfiguration.init()
        .addMember(MEMBER, MemberState.initializeAsActive(partitions).toRecovering());
  }

  /** Resolves every request to the backups it asked for, on the single partition of the member. */
  private static RequestValidatorRegistry registry() {
    final var registry = new RequestValidatorRegistry();
    registry.registerValidator(
        null,
        new ClusterConfigurationRequestValidator<RestoreRequest, RestoreResolvedRequest>() {
          @Override
          public Class<RestoreRequest> requestType() {
            return RestoreRequest.class;
          }

          @Override
          public Either<Exception, RestoreResolvedRequest> validate(final RestoreRequest request) {
            final var backupIds =
                request.arguments().parameters().backupIds().stream()
                    .mapToLong(Long::longValue)
                    .toArray();
            return Either.right(new RestoreResolvedRequest(Map.of(1, backupIds), false));
          }
        });
    return registry;
  }
}
