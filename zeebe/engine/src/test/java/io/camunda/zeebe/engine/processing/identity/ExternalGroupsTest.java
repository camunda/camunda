/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;

/**
 * With one OIDC provider that has a groups claim and one that does not, the groups of the claim
 * exist only in the IdP, so a group assigned to a role or tenant is not required to exist in
 * Camunda.
 */
public class ExternalGroupsTest {

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withSecurityConfig(
              config -> {
                final var authentication = config.getAuthentication();
                authentication.setMethod(AuthenticationMethod.OIDC);
                authentication.getProviders().getOidc().put("keycloak", provider("groups"));
                authentication.getProviders().getOidc().put("partner", provider(null));
              });

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldAddAGroupThatDoesNotExistInCamundaToARole() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    engine.role().newRole(roleId).create();

    // when
    final var updatedRole =
        engine
            .role()
            .addEntity(roleId)
            .withEntityId("engineering")
            .withEntityType(EntityType.GROUP)
            .add()
            .getValue();

    // then
    assertThat(updatedRole).hasEntityId("engineering").hasEntityType(EntityType.GROUP);
  }

  @Test
  public void shouldAddAGroupThatDoesNotExistInCamundaToATenant() {
    // given
    final var tenantId = UUID.randomUUID().toString();
    engine.tenant().newTenant().withTenantId(tenantId).withName("Tenant 1").create();

    // when
    final var updatedTenant =
        engine
            .tenant()
            .addEntity(tenantId)
            .withEntityId("engineering")
            .withEntityType(EntityType.GROUP)
            .add()
            .getValue();

    // then
    assertThat(updatedTenant).hasEntityId("engineering").hasEntityType(EntityType.GROUP);
  }

  private static OidcConfiguration provider(final String groupsClaim) {
    final var provider = new OidcConfiguration();
    provider.setClientId("client");
    provider.setIssuerUri("https://" + UUID.randomUUID() + ".example.com");
    if (groupsClaim != null) {
      provider.setGroupsClaim(groupsClaim);
    }
    return provider;
  }
}
