/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestWebappClient;
import io.camunda.qa.util.multidb.MultiDbPhysicalTenants;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * The web app COMPONENT ACCESS check follows the physical tenant's own {@code
 * authorizations.enabled}, not the root value. Root has authorizations off; one tenant turns them
 * on, the other inherits off. The user is copied into every tenant and holds no grant.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
@MultiDbPhysicalTenants({
  PhysicalTenantWebappComponentAccessIT.TENANT_AUTHORIZATIONS_ON,
  PhysicalTenantWebappComponentAccessIT.TENANT_AUTHORIZATIONS_OFF
})
class PhysicalTenantWebappComponentAccessIT {

  static final String TENANT_AUTHORIZATIONS_ON = "webappon";
  static final String TENANT_AUTHORIZATIONS_OFF = "webappoff";

  private static final String USERNAME = "webapp-no-grants";
  private static final String PASSWORD = "password";

  @MultiDbTestApplication
  private static final TestCamundaApplication CAMUNDA =
      new TestCamundaApplication()
          .withBasicAuth()
          .withAuthenticatedAccess()
          .withSecurityConfig(
              conf -> {
                final var users = new ArrayList<>(conf.getInitialization().getUsers());
                users.add(new ConfiguredUser(USERNAME, PASSWORD, USERNAME, ""));
                conf.getInitialization().setUsers(users);
              })
          .withPtConfig(
              TENANT_AUTHORIZATIONS_ON, c -> c.getSecurity().getAuthorizations().setEnabled(true));

  @Test
  void shouldRedirectToForbiddenWhenPhysicalTenantEnablesAuthorizations() {
    // when
    final HttpResponse<String> response = getOperateIn(TENANT_AUTHORIZATIONS_ON);

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_MOVED_TEMP);
    assertThat(response.headers().firstValue("Location"))
        .hasValueSatisfying(location -> assertThat(location).endsWith("operate/forbidden"));
  }

  @Test
  void shouldAllowAccessWhenPhysicalTenantInheritsDisabledAuthorizations() {
    // when
    final HttpResponse<String> response = getOperateIn(TENANT_AUTHORIZATIONS_OFF);

    // then
    assertThat(response.statusCode())
        .isNotIn(
            HttpURLConnection.HTTP_UNAUTHORIZED,
            HttpURLConnection.HTTP_FORBIDDEN,
            HttpURLConnection.HTTP_MOVED_TEMP);
  }

  private static HttpResponse<String> getOperateIn(final String physicalTenantId) {
    final String base = CAMUNDA.restAddress().toString().replaceAll("/+$", "");
    final URI tenantRoot = URI.create(base + "/physical-tenants/" + physicalTenantId + "/");
    try (final var loggedIn = new TestWebappClient(tenantRoot).logIn(USERNAME, PASSWORD)) {
      final var result = loggedIn.send("operate/user");
      assertThat(result.isRight()).isTrue();
      return result.get();
    }
  }
}
