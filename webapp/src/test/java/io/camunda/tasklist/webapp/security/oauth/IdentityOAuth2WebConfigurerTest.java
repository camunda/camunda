/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.oauth;

import static io.camunda.tasklist.webapp.security.oauth.IdentityOAuth2WebConfigurer.SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.identity.sdk.IdentityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.test.util.ReflectionTestUtils;

class IdentityOAuth2WebConfigurerTest {

  @Mock private Environment environment;

  @Mock private IdentityConfiguration identityConfiguration;

  @InjectMocks private IdentityOAuth2WebConfigurer webConfigurer;

  @BeforeEach
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  public void configureShouldEnableJWTWithSuccess() throws Exception {
    // given
    when(environment.containsProperty(
            IdentityOAuth2WebConfigurer.SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_ISSUER_URI))
        .thenReturn(true);
    when(environment.getProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI))
        .thenReturn("https://example.com/jwks");
    when(identityConfiguration.getIssuerBackendUrl()).thenReturn("https://example.com/identity");

    final HttpSecurity httpSecurity = mock(HttpSecurity.class);

    // when
    webConfigurer.configure(httpSecurity);

    // then
    verify(httpSecurity, times(1)).oauth2ResourceServer(any());
  }

  @Test
  public void configureJWTDisabledShouldNotApplyNoConfigurations() throws Exception {
    // given
    when(environment.containsProperty(
            IdentityOAuth2WebConfigurer.SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_ISSUER_URI))
        .thenReturn(false);
    when(environment.containsProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI))
        .thenReturn(false);

    final HttpSecurity httpSecurity = mock(HttpSecurity.class);

    // when
    webConfigurer.configure(httpSecurity);

    // then
    verify(httpSecurity, never()).oauth2ResourceServer(any());
  }

  @Test
  public void shouldReturnConcatUrlForJdkAuth() {
    when(identityConfiguration.getIssuerBackendUrl()).thenReturn("http://localhost:1111");

    final String result =
        (String) (ReflectionTestUtils.invokeGetterMethod(webConfigurer, "getJwkSetUriProperty"));

    assertThat(result).isEqualTo("http://localhost:1111/protocol/openid-connect/certs");
  }

  @Test
  public void shouldReturnConcatEnvVarJdkAuth() {
    when(environment.getProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI))
        .thenReturn("http://localhost:1111");
    when(environment.containsProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI))
        .thenReturn(true);

    final String result =
        (String) (ReflectionTestUtils.invokeGetterMethod(webConfigurer, "getJwkSetUriProperty"));

    assertThat(result).isEqualTo("http://localhost:1111");
  }
}
