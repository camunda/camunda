/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.PurgeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RequestValidatorRegistryTest {

  private static final String OTHER_TENANT = "othertenant";

  private final RequestValidatorRegistry registry = new RequestValidatorRegistry();

  private static RestoreRequest restoreRequest() {
    return new RestoreRequest(List.of(1L), null, null, "elasticsearch", false, false);
  }

  private static ClusterConfigurationRequestValidator<RestoreRequest, RestoreRequest> validator(
      final RestoreRequest rewrittenTo) {
    return new ClusterConfigurationRequestValidator<>() {
      @Override
      public Class<RestoreRequest> requestType() {
        return RestoreRequest.class;
      }

      @Override
      public RestoreRequest validate(final RestoreRequest request) {
        return rewrittenTo;
      }
    };
  }

  @Test
  void shouldReturnRequestUnchangedWhenNoValidatorRegistered() {
    // given
    final var request = new PurgeRequest(false);

    // when
    final var result = registry.validateRequest(request);

    // then
    assertThat(result).isSameAs(request);
  }

  @Test
  void shouldUseValidatorRegisteredForMatchingRequestTypeAndTenant() {
    // given
    final var request = restoreRequest();
    final var rewritten = restoreRequest();
    registry.registerValidator(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, validator(rewritten));

    // when
    final var result = registry.validateRequest(request);

    // then
    assertThat(result).isSameAs(rewritten);
  }

  @Test
  void shouldNotUseValidatorRegisteredForADifferentTenant() {
    // given a validator registered for a different tenant than the request's
    final var request = restoreRequest();
    registry.registerValidator(OTHER_TENANT, validator(restoreRequest()));

    // when validating a request for the default tenant, no validator matches, and the request
    // requires validation
    // then
    assertThatThrownBy(() -> registry.validateRequest(request))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldUseWildcardValidatorWhenNoTenantSpecificValidatorIsRegistered() {
    // given a validator registered with a null tenant, applicable to every tenant
    final var request = restoreRequest();
    final var rewritten = restoreRequest();
    registry.registerValidator(null, validator(rewritten));

    // when
    final var result = registry.validateRequest(request);

    // then
    assertThat(result).isSameAs(rewritten);
  }

  @Test
  void shouldPreferTenantSpecificValidatorOverWildcard() {
    // given both a wildcard and a tenant-specific validator are registered
    final var request = restoreRequest();
    final var rewrittenByWildcard = restoreRequest();
    final var rewrittenByTenantSpecific = restoreRequest();
    registry.registerValidator(null, validator(rewrittenByWildcard));
    registry.registerValidator(
        PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, validator(rewrittenByTenantSpecific));

    // when
    final var result = registry.validateRequest(request);

    // then
    assertThat(result).isSameAs(rewrittenByTenantSpecific);
  }

  @Test
  void shouldThrowWhenValidationRequiredButNoValidatorRegistered() {
    // given a request type that requires validation, with nothing registered for it
    final var request = restoreRequest();

    // then
    assertThatThrownBy(() -> registry.validateRequest(request))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRemoveValidatorOnDeregister() {
    // given
    registry.registerValidator(
        PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, validator(restoreRequest()));
    registry.deregisterValidator(
        PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, RestoreRequest.class);

    // when validating after deregistration, no validator matches, and the request requires
    // validation
    // then
    assertThatThrownBy(() -> registry.validateRequest(restoreRequest()))
        .isInstanceOf(IllegalArgumentException.class);
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
    assertThat(registry.validateRequest(restoreRequest())).isSameAs(rewrittenByTenantSpecific);
  }
}
