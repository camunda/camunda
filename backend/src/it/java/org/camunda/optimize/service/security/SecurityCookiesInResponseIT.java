/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_AUTHORIZATION;
import static org.camunda.optimize.rest.constants.RestConstants.SAME_SITE_COOKIE_FLAG;
import static org.camunda.optimize.rest.constants.RestConstants.SAME_SITE_COOKIE_STRICT_VALUE;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

public class SecurityCookiesInResponseIT extends AbstractIT {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void cookieIsSecureForHttpsOnly(final boolean useHttps) {
    // when
    final Response authResponse = authWithDefaultCredentials(useHttps);

    // then
    assertThat(authResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(authResponse.getCookies().get(OPTIMIZE_AUTHORIZATION).isSecure()).isEqualTo(useHttps);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void cookieIsHttpOnly(final boolean useHttps) {
    // when
    final Response authResponse = authWithDefaultCredentials(useHttps);

    // then
    assertThat(authResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(authResponse.getCookies().get(OPTIMIZE_AUTHORIZATION).isHttpOnly()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void canDisableSameSiteCookieFlag(final boolean useHttps) {
    // given
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getCookieConfiguration()
      .setSameSiteFlagEnabled(false);

    // when
    final Response authResponse = authWithDefaultCredentials(useHttps);

    // then
    assertThat(authResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE).toString())
      .doesNotContain(SAME_SITE_COOKIE_FLAG);

    // cleanup
    assertThat(authResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getCookieConfiguration()
      .setSameSiteFlagEnabled(true);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void cookieHasSameSiteCookieFlagEnabledByDefault(final boolean useHttps) {
    // when
    final Response authResponse = authWithDefaultCredentials(useHttps);

    // then
    assertThat(authResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(authResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE).toString())
      .contains(SAME_SITE_COOKIE_FLAG + "=" + SAME_SITE_COOKIE_STRICT_VALUE);
  }

  private Response authWithDefaultCredentials(final boolean useHttps) {
    if (useHttps) {
      return embeddedOptimizeExtension.securedRootTarget()
        .path("api/authentication")
        .request()
        .post(Entity.json(new CredentialsRequestDto(DEFAULT_USERNAME, DEFAULT_PASSWORD)));
    }
    return embeddedOptimizeExtension
      .authenticateUserRequest(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

}
