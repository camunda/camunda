/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.AuthorizationRejection;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.core.auth.RequiredAuthorization;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OptimizeComponentAuthorizationAdapterTest {

  private static final CamundaAuthentication AUTHENTICATION =
      CamundaAuthentication.of(builder -> builder.user("kermit"));

  @Mock private OptimizeComponentAccessPolicy policy;

  @Test
  void shouldGrantComponentAccessWhenThePolicyAllows() {
    // given
    when(policy.sessionDenialReason(AUTHENTICATION)).thenReturn(Optional.empty());

    // when
    final var result = adapter().check(AUTHENTICATION, componentAccess());

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldRejectComponentAccessWhenThePolicyDenies() {
    // given
    when(policy.sessionDenialReason(AUTHENTICATION)).thenReturn(Optional.of("no permission"));

    // when
    final var result = adapter().check(AUTHENTICATION, componentAccess());

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.leftValue())
        .isEqualTo(
            new AuthorizationRejection.Permission(
                AuthorizationResourceType.COMPONENT, PermissionType.ACCESS, "optimize"));
  }

  @Test
  void shouldGrantEveryRequirementThatIsNotComponentAccess() {
    // Optimize stores no authorizations, its own services decide on its data.
    // when
    final var result =
        adapter()
            .check(
                AUTHENTICATION,
                RequiredAuthorization.of(
                    builder ->
                        builder
                            .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                            .permissionType(PermissionType.READ_PROCESS_INSTANCE)));

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldRejectComponentAccessForClaimsWhenThePolicyDenies() {
    // given
    when(policy.sessionDenialReason(
            CamundaAuthentication.of(builder -> builder.claims(Map.of("sub", "kermit")))))
        .thenReturn(Optional.of("no permission"));

    // when
    final var result = adapter().check(Map.of("sub", "kermit"), componentAccess());

    // then
    assertThat(result.isLeft()).isTrue();
  }

  private OptimizeComponentAuthorizationAdapter adapter() {
    return new OptimizeComponentAuthorizationAdapter(policy);
  }

  private static RequiredAuthorization<Void> componentAccess() {
    return RequiredAuthorization.<Void>of(
            builder ->
                builder
                    .resourceType(AuthorizationResourceType.COMPONENT)
                    .permissionType(PermissionType.ACCESS))
        .withResourceId("optimize");
  }
}
