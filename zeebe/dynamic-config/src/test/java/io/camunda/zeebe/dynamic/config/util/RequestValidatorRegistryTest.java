/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestValidator;
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RequestValidatorRegistryTest {

  private static final String OTHER_TENANT = "othertenant";

  private final RequestValidatorRegistry registry = new RequestValidatorRegistry();

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

  private static ClusterConfigurationRequestValidator<RestoreRequest, RestoreRequest> validator(
      final RestoreRequest rewrittenTo) {
    return new ClusterConfigurationRequestValidator<>() {
      @Override
      public Class<RestoreRequest> requestType() {
        return RestoreRequest.class;
      }

      @Override
      public Either<Exception, RestoreRequest> validate(final RestoreRequest request) {
        return Either.right(rewrittenTo);
      }
    };
  }

  @Test
  void shouldReturnEmptyWhenNoValidatorRegistered() {
    // when
    final var result =
        registry.getValidator(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, RestoreRequest.class);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnValidatorRegisteredForMatchingRequestTypeAndTenant() {
    // given
    final var rewritten = restoreRequest();
    registry.registerValidator(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, validator(rewritten));

    // when
    final var result =
        registry.getValidator(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, RestoreRequest.class);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().validate(restoreRequest()).get()).isSameAs(rewritten);
  }

  @Test
  void shouldNotReturnValidatorRegisteredForADifferentTenant() {
    // given a validator registered for a different tenant than the one being looked up
    registry.registerValidator(OTHER_TENANT, validator(restoreRequest()));

    // when
    final var result =
        registry.getValidator(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, RestoreRequest.class);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldFallBackToWildcardValidatorWhenNoTenantSpecificValidatorIsRegistered() {
    // given a validator registered with a null tenant, applicable to every tenant
    final var rewritten = restoreRequest();
    registry.registerValidator(null, validator(rewritten));

    // when
    final var result =
        registry.getValidator(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, RestoreRequest.class);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().validate(restoreRequest()).get()).isSameAs(rewritten);
  }

  @Test
  void shouldPreferTenantSpecificValidatorOverWildcard() {
    // given both a wildcard and a tenant-specific validator are registered
    final var rewrittenByWildcard = restoreRequest();
    final var rewrittenByTenantSpecific = restoreRequest();
    registry.registerValidator(null, validator(rewrittenByWildcard));
    registry.registerValidator(
        PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, validator(rewrittenByTenantSpecific));

    // when
    final var result =
        registry.getValidator(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, RestoreRequest.class);

    // then
    assertThat(result.get().validate(restoreRequest()).get()).isSameAs(rewrittenByTenantSpecific);
  }

  @Test
  void shouldRemoveValidatorOnDeregister() {
    // given
    registry.registerValidator(
        PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, validator(restoreRequest()));

    // when
    registry.deregisterValidator(
        PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, RestoreRequest.class);

    // then
    assertThat(
            registry.getValidator(
                PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, RestoreRequest.class))
        .isEmpty();
  }

  @Test
  void shouldOnlyDeregisterTheGivenTenant() {
    // given both a wildcard and a tenant-specific validator are registered
    final var rewrittenByWildcard = restoreRequest();
    final var rewrittenByTenantSpecific = restoreRequest();
    registry.registerValidator(null, validator(rewrittenByWildcard));
    registry.registerValidator(
        PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, validator(rewrittenByTenantSpecific));

    // when deregistering only the wildcard entry
    registry.deregisterValidator(null, RestoreRequest.class);

    // then the tenant-specific validator is unaffected
    assertThat(
            registry
                .getValidator(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, RestoreRequest.class)
                .get()
                .validate(restoreRequest())
                .get())
        .isSameAs(rewrittenByTenantSpecific);
  }

  @Test
  void shouldReturnAllValidatorsRegisteredForARequestTypeAcrossTenants() {
    // given
    final var wildcard = validator(restoreRequest());
    final var tenantSpecific = validator(restoreRequest());
    registry.registerValidator(null, wildcard);
    registry.registerValidator(OTHER_TENANT, tenantSpecific);

    // when
    final var result = registry.validatorsForRequest(RestoreRequest.class);

    // then
    assertThat(result).containsExactlyInAnyOrder(wildcard, tenantSpecific);
  }
}
