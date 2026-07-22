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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@ExtendWith(MockitoExtension.class)
class OptimizeCloudOidcUserServiceTest {

  private static final String ORG_ID = "org-1";

  @Mock private OidcUser oidcUser;

  private final OptimizeCloudOidcUserService service =
      new OptimizeCloudOidcUserService(ORG_ID, OptimizeCloudOidcUserService.ALLOWED_ORG_ROLES);

  private void withOrgs(final Object organizationsClaim) {
    when(oidcUser.getClaims())
        .thenReturn(Map.of(OptimizeCloudOidcUserService.ORGANIZATIONS_CLAIM, organizationsClaim));
  }

  @Test
  void grantsAccessForConfiguredOrgWithAllowedRole() {
    withOrgs(List.of(Map.of("id", ORG_ID, "roles", List.of("analyst"))));

    assertThat(service.hasRequiredOrgRole(oidcUser)).isTrue();
  }

  @Test
  void deniesWhenConfiguredOrgHasNoAllowedRole() {
    withOrgs(List.of(Map.of("id", ORG_ID, "roles", List.of("visitor"))));

    assertThat(service.hasRequiredOrgRole(oidcUser)).isFalse();
  }

  @Test
  void deniesWhenUserIsNotMemberOfConfiguredOrg() {
    withOrgs(List.of(Map.of("id", "other-org", "roles", List.of("owner"))));

    assertThat(service.hasRequiredOrgRole(oidcUser)).isFalse();
  }

  @Test
  void deniesWhenOrganizationsClaimMissing() {
    when(oidcUser.getClaims()).thenReturn(Map.of());

    assertThat(service.hasRequiredOrgRole(oidcUser)).isFalse();
  }

  @Test
  void deniesWhenOrganizationsClaimIsNotACollection() {
    withOrgs("not-a-list");

    assertThat(service.hasRequiredOrgRole(oidcUser)).isFalse();
  }
}
