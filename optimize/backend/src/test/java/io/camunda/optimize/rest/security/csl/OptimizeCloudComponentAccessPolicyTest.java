/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static io.camunda.optimize.rest.security.csl.OptimizeCloudOrganizationValidator.ORGANIZATIONS_CLAIM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.security.AuthConfiguration;
import io.camunda.optimize.service.util.configuration.security.CloudAuthConfiguration;
import io.camunda.security.api.model.CamundaAuthentication;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OptimizeCloudComponentAccessPolicyTest {

  @Mock private ConfigurationService configurationService;
  @Mock private AuthConfiguration authConfiguration;
  @Mock private CloudAuthConfiguration cloudAuthConfiguration;

  @Test
  void shouldAllowLoginForConfiguredOrganizationWithAllowedRole() {
    // given
    final OptimizeCloudComponentAccessPolicy policy = policyFor("org-1");

    // when
    final Optional<String> reason =
        policy.loginDenialReason("token", orgClaims("org-1", "analyst"));

    // then
    assertThat(reason).isEmpty();
  }

  @Test
  void shouldDenyLoginForAnotherOrganization() {
    // given
    final OptimizeCloudComponentAccessPolicy policy = policyFor("org-1");

    // when
    final Optional<String> reason = policy.loginDenialReason("token", orgClaims("org-2", "admin"));

    // then
    assertThat(reason).isPresent();
  }

  @Test
  void shouldDenySessionWithoutAnAllowedRole() {
    // given
    final OptimizeCloudComponentAccessPolicy policy = policyFor("org-1");
    final CamundaAuthentication authentication =
        CamundaAuthentication.of(builder -> builder.user("kermit").claims(orgClaims("org-1", "")));

    // when
    final Optional<String> reason = policy.sessionDenialReason(authentication);

    // then
    assertThat(reason).isPresent();
  }

  @Test
  void shouldAllowSessionWithoutTheOrganizationsClaim() {
    // Lenient on absence, like the token validator: token validation owns the tokens that carry no
    // organizations claim.
    // given
    final OptimizeCloudComponentAccessPolicy policy = policyFor("org-1");
    final CamundaAuthentication authentication =
        CamundaAuthentication.of(builder -> builder.user("kermit"));

    // when
    final Optional<String> reason = policy.sessionDenialReason(authentication);

    // then
    assertThat(reason).isEmpty();
  }

  @Test
  void shouldFailStartupWhenOrganizationIdIsBlank() {
    // given
    when(configurationService.getAuthConfiguration()).thenReturn(authConfiguration);
    when(authConfiguration.getCloudAuthConfiguration()).thenReturn(cloudAuthConfiguration);
    when(cloudAuthConfiguration.getOrganizationId()).thenReturn(" ");

    // when, then
    assertThatThrownBy(() -> new OptimizeCloudComponentAccessPolicy(configurationService))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("organizationId");
  }

  private OptimizeCloudComponentAccessPolicy policyFor(final String organizationId) {
    when(configurationService.getAuthConfiguration()).thenReturn(authConfiguration);
    when(authConfiguration.getCloudAuthConfiguration()).thenReturn(cloudAuthConfiguration);
    when(cloudAuthConfiguration.getOrganizationId()).thenReturn(organizationId);
    return new OptimizeCloudComponentAccessPolicy(configurationService);
  }

  private static Map<String, Object> orgClaims(final String organizationId, final String role) {
    return Map.of(
        ORGANIZATIONS_CLAIM,
        List.of(Map.of("id", organizationId, "roles", role.isEmpty() ? List.of() : List.of(role))));
  }
}
