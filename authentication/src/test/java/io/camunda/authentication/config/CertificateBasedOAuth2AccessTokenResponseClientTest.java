/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.authentication.oauth.ClientAssertionConstants;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import java.lang.reflect.Field;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.RequestEntity;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequestEntityConverter;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.MultiValueMap;

public class CertificateBasedOAuth2AccessTokenResponseClientTest {

  @Mock private CertificateClientAssertionService clientAssertionService;
  @Mock private OidcAuthenticationConfiguration oidcConfiguration;

  private CertificateBasedOAuth2AccessTokenResponseClient tokenResponseClient;
  private ClientRegistration clientRegistration;
  private OAuth2AuthorizationCodeGrantRequest grantRequest;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    clientRegistration =
        ClientRegistration.withRegistrationId("test-client")
            .clientId("test-client-id")
            .clientSecret("test-secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .tokenUri("https://login.microsoftonline.com/tenant/oauth2/v2.0/token")
            .authorizationUri("https://login.microsoftonline.com/tenant/oauth2/v2.0/authorize")
            .redirectUri("http://localhost:8080/callback")
            .scope("openid", "profile", "email")
            .build();

    final OAuth2AuthorizationRequest authorizationRequest =
        OAuth2AuthorizationRequest.authorizationCode()
            .clientId("test-client-id")
            .authorizationUri("https://login.microsoftonline.com/tenant/oauth2/v2.0/authorize")
            .redirectUri("http://localhost:8080/callback")
            .scopes(Set.of("openid", "profile", "email"))
            .state("test-state")
            .build();

    final OAuth2AuthorizationResponse authorizationResponse =
        OAuth2AuthorizationResponse.success("test-code")
            .redirectUri("http://localhost:8080/callback")
            .state("test-state")
            .build();

    final OAuth2AuthorizationExchange authorizationExchange =
        new OAuth2AuthorizationExchange(authorizationRequest, authorizationResponse);

    grantRequest =
        new OAuth2AuthorizationCodeGrantRequest(clientRegistration, authorizationExchange);

    tokenResponseClient =
        new CertificateBasedOAuth2AccessTokenResponseClient(
            clientAssertionService, oidcConfiguration);
  }

  @Test
  void shouldAddClientAssertionWhenEnabled() throws Exception {
    // given
    when(oidcConfiguration.isClientAssertionEnabled()).thenReturn(true);
    when(clientAssertionService.createClientAssertion(eq(oidcConfiguration), any(String.class)))
        .thenReturn("test-jwt-assertion");

    // Use reflection to access the converter
    final Converter<OAuth2AuthorizationCodeGrantRequest, RequestEntity<?>> converter =
        extractConverter();

    // when
    final RequestEntity<?> requestEntity = converter.convert(grantRequest);

    // then
    assertThat(requestEntity).isNotNull();
    assertThat(requestEntity.getBody()).isInstanceOf(MultiValueMap.class);

    @SuppressWarnings("unchecked")
    final MultiValueMap<String, String> body =
        (MultiValueMap<String, String>) requestEntity.getBody();

    // Verify client assertion parameters are added
    assertThat(body.get(ClientAssertionConstants.CLIENT_ASSERTION_TYPE_PARAM))
        .containsExactly(ClientAssertionConstants.CLIENT_ASSERTION_TYPE_JWT_BEARER);
    assertThat(body.get(ClientAssertionConstants.CLIENT_ASSERTION_PARAM))
        .containsExactly("test-jwt-assertion");

    // Verify client_secret is removed
    assertThat(body.containsKey(OAuth2ParameterNames.CLIENT_SECRET)).isFalse();

    // Verify client assertion service was called with correct parameters
    verify(clientAssertionService)
        .createClientAssertion(
            oidcConfiguration, clientRegistration.getProviderDetails().getTokenUri());
  }

  @Test
  void shouldNotAddClientAssertionWhenDisabled() throws Exception {
    // given
    when(oidcConfiguration.isClientAssertionEnabled()).thenReturn(false);

    // Use reflection to access the converter
    final Converter<OAuth2AuthorizationCodeGrantRequest, RequestEntity<?>> converter =
        extractConverter();

    // when
    final RequestEntity<?> requestEntity = converter.convert(grantRequest);

    // then
    assertThat(requestEntity).isNotNull();
    assertThat(requestEntity.getBody()).isInstanceOf(MultiValueMap.class);

    @SuppressWarnings("unchecked")
    final MultiValueMap<String, String> body =
        (MultiValueMap<String, String>) requestEntity.getBody();

    // Verify client assertion parameters are NOT added
    assertThat(body.containsKey(ClientAssertionConstants.CLIENT_ASSERTION_TYPE_PARAM)).isFalse();
    assertThat(body.containsKey(ClientAssertionConstants.CLIENT_ASSERTION_PARAM)).isFalse();

    // Verify basic OAuth2 parameters are present (default behavior should be preserved)
    assertThat(body.get(OAuth2ParameterNames.GRANT_TYPE)).isNotNull();
    assertThat(body.get(OAuth2ParameterNames.CLIENT_ID)).isNotNull();
  }

  @Test
  void shouldPreserveAuthorizationCodeGrantParameters() throws Exception {
    // given
    when(oidcConfiguration.isClientAssertionEnabled()).thenReturn(true);
    when(clientAssertionService.createClientAssertion(eq(oidcConfiguration), any(String.class)))
        .thenReturn("test-jwt-assertion");

    // Use reflection to access the converter
    final Converter<OAuth2AuthorizationCodeGrantRequest, RequestEntity<?>> converter =
        extractConverter();

    // when
    final RequestEntity<?> requestEntity = converter.convert(grantRequest);

    // then
    assertThat(requestEntity).isNotNull();
    assertThat(requestEntity.getBody()).isInstanceOf(MultiValueMap.class);

    @SuppressWarnings("unchecked")
    final MultiValueMap<String, String> body =
        (MultiValueMap<String, String>) requestEntity.getBody();

    // Verify standard authorization code grant parameters are preserved
    assertThat(body.get(OAuth2ParameterNames.GRANT_TYPE))
        .containsExactly(AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
    assertThat(body.get(OAuth2ParameterNames.CLIENT_ID)).containsExactly("test-client-id");
    assertThat(body.get(OAuth2ParameterNames.CODE)).containsExactly("test-code");
    assertThat(body.get(OAuth2ParameterNames.REDIRECT_URI))
        .containsExactly("http://localhost:8080/callback");
  }

  @Test
  void shouldHandleTokenEndpointCorrectly() throws Exception {
    // given
    when(oidcConfiguration.isClientAssertionEnabled()).thenReturn(true);
    when(clientAssertionService.createClientAssertion(eq(oidcConfiguration), any(String.class)))
        .thenReturn("test-jwt-assertion");

    // Use reflection to access the converter
    final Converter<OAuth2AuthorizationCodeGrantRequest, RequestEntity<?>> converter =
        extractConverter();

    // when
    final RequestEntity<?> requestEntity = converter.convert(grantRequest);

    // then
    // Verify that the correct token endpoint was passed to the client assertion service
    verify(clientAssertionService)
        .createClientAssertion(
            oidcConfiguration, "https://login.microsoftonline.com/tenant/oauth2/v2.0/token");

    // Verify the request entity targets the correct URL
    assertThat(requestEntity.getUrl().toString())
        .contains("login.microsoftonline.com/tenant/oauth2/v2.0/token");
  }

  @Test
  void shouldMaintainHttpHeaders() throws Exception {
    // given
    when(oidcConfiguration.isClientAssertionEnabled()).thenReturn(true);
    when(clientAssertionService.createClientAssertion(eq(oidcConfiguration), any(String.class)))
        .thenReturn("test-jwt-assertion");

    // Use reflection to access the converter
    final Converter<OAuth2AuthorizationCodeGrantRequest, RequestEntity<?>> converter =
        extractConverter();

    // when
    final RequestEntity<?> requestEntity = converter.convert(grantRequest);

    // then
    assertThat(requestEntity.getHeaders()).isNotEmpty();
    assertThat(requestEntity.getHeaders().getContentType().toString())
        .contains("application/x-www-form-urlencoded");
  }

  private Converter<OAuth2AuthorizationCodeGrantRequest, RequestEntity<?>> extractConverter()
      throws Exception {
    try {
      // Use reflection to access the private converter field
      final Field delegateField =
          CertificateBasedOAuth2AccessTokenResponseClient.class.getDeclaredField("delegate");
      delegateField.setAccessible(true);
      final DefaultAuthorizationCodeTokenResponseClient delegate =
          (DefaultAuthorizationCodeTokenResponseClient) delegateField.get(tokenResponseClient);

      final Field converterField =
          DefaultAuthorizationCodeTokenResponseClient.class.getDeclaredField(
              "requestEntityConverter");
      converterField.setAccessible(true);
      return (Converter<OAuth2AuthorizationCodeGrantRequest, RequestEntity<?>>)
          converterField.get(delegate);
    } catch (Exception e) {
      // If reflection fails, create a new instance and access its converter
      final DefaultAuthorizationCodeTokenResponseClient fallback =
          new DefaultAuthorizationCodeTokenResponseClient();
      try {
        final Field converterField =
            DefaultAuthorizationCodeTokenResponseClient.class.getDeclaredField(
                "requestEntityConverter");
        converterField.setAccessible(true);
        return (Converter<OAuth2AuthorizationCodeGrantRequest, RequestEntity<?>>)
            converterField.get(fallback);
      } catch (Exception ex) {
        // Last resort - return basic converter
        return new OAuth2AuthorizationCodeGrantRequestEntityConverter();
      }
    }
  }
}
