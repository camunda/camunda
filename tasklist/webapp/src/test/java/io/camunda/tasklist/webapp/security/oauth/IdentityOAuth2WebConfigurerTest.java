/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.oauth;

import static io.camunda.tasklist.webapp.security.oauth.IdentityOAuth2WebConfigurer.SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import io.camunda.identity.sdk.IdentityConfiguration;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

class IdentityOAuth2WebConfigurerTest {

  @Mock private Environment environment;

  @Mock private IdentityConfiguration identityConfiguration;

  @Mock private IdentityJwt2AuthenticationTokenConverter jwtConverter;

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
    // then
    final var oauth2ResourceServerCustomizer =
        captureFrom(
            httpSecurity,
            Customizer.class,
            (o, t) -> {
              try {
                o.oauth2ResourceServer(t);
              } catch (final Exception e) {
                fail(e);
              }
            });
    final var serverConfigurer = mock(OAuth2ResourceServerConfigurer.class);
    when(serverConfigurer.authenticationEntryPoint(any())).thenReturn(serverConfigurer);
    oauth2ResourceServerCustomizer.customize(serverConfigurer);
    final var jwtCustomizer =
        captureFrom(serverConfigurer, Customizer.class, OAuth2ResourceServerConfigurer::jwt);
    final OAuth2ResourceServerConfigurer<HttpSecurity>.JwtConfigurer jwtConfigurer =
        mock(OAuth2ResourceServerConfigurer.JwtConfigurer.class);
    when(jwtConfigurer.jwtAuthenticationConverter(jwtConverter)).thenReturn(jwtConfigurer);
    jwtCustomizer.customize(jwtConfigurer);

    final JwtDecoder jwtDecoder =
        captureFrom(
            jwtConfigurer, JwtDecoder.class, OAuth2ResourceServerConfigurer.JwtConfigurer::decoder);
    assertThat(jwtDecoder).isInstanceOf(NimbusJwtDecoder.class);

    final NimbusJwtDecoder nimbusJwtDecoder = (NimbusJwtDecoder) jwtDecoder;

    // Use reflection to verify JWSTypeVerifier configuration
    final var processor = ReflectionTestUtils.getField(nimbusJwtDecoder, "jwtProcessor");
    final var jwsTypeVerifier = ReflectionTestUtils.invokeMethod(processor, "getJWSTypeVerifier");

    assertThat(jwsTypeVerifier).isNotNull();
    assertThat(jwsTypeVerifier).isInstanceOf(DefaultJOSEObjectTypeVerifier.class);

    final var joseVerifier = (DefaultJOSEObjectTypeVerifier<?>) jwsTypeVerifier;

    // Ensure that both jwt and at+jwt types are being verified
    assertThat(joseVerifier.getAllowedTypes())
        .containsExactlyInAnyOrder(new JOSEObjectType("jwt"), new JOSEObjectType("at+jwt"), null);
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

  private <O, T> T captureFrom(
      final O from, final Class<T> tClass, final BiConsumer<O, T> consumer) {
    final var argumentCaptor = ArgumentCaptor.forClass(tClass);
    consumer.accept(verify(from, times(1)), argumentCaptor.capture());
    return argumentCaptor.getValue();
  }
}
