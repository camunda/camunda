/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.cloud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

public class AuthorizationRequestCookieValueMapperTest {

  private AuthorizationRequestCookieValueMapper underTest =
      new AuthorizationRequestCookieValueMapper();

  @Test
  public void equalityAfterDeserialization() {
    // given
    final OAuth2AuthorizationRequest authorizationRequest =
        OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("https://weblogin.cloud.dev.ultrawombat.com/authorize")
            .redirectUri("http://localhost:8090/sso-callback?uuid=optimize")
            .clientId("whatever")
            .scopes(Set.of("openid", "profile"))
            .state("myState")
            .additionalParameters(Map.of("nonce", "whatever"))
            .authorizationUri(
                "https://weblogin.cloud.dev.ultrawombat.com/authorize?response_type=code")
            .attributes(Map.of("registration_id", "auth0", "nonce", "anotherNonce"))
            .build();

    // when
    final String serializedString = underTest.serialize(authorizationRequest);
    final OAuth2AuthorizationRequest deserializedAuthorizationRequest =
        underTest.deserialize(serializedString);

    // then
    assertThat(deserializedAuthorizationRequest)
        .usingRecursiveComparison()
        .isEqualTo(authorizationRequest);
  }

  @Test
  public void stateIsPreservedAfterSerializationRoundTrip() {
    // given
    final String originalState = "crypto-random-state-value-abc123";
    final OAuth2AuthorizationRequest request =
        OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("https://example.com/authorize")
            .clientId("test-client")
            .redirectUri("http://localhost/callback")
            .state(originalState)
            .build();

    // when
    final String serialized = underTest.serialize(request);
    final OAuth2AuthorizationRequest deserialized = underTest.deserialize(serialized);

    // then
    assertThat(deserialized.getState()).isEqualTo(originalState);
  }

  @Test
  public void deserializationFailsWhenStateMissing() {
    // given - JSON without 'state' field
    final String jsonWithoutState =
        "{\"authorizationUri\":\"https://example.com/auth\","
            + "\"clientId\":\"client\",\"redirectUri\":\"http://localhost/cb\","
            + "\"authorizationRequestUri\":\"https://example.com/auth?foo=bar\"}";
    final String encoded =
        Base64.getUrlEncoder().encodeToString(jsonWithoutState.getBytes(StandardCharsets.UTF_8));

    // when/then
    assertThatThrownBy(() -> underTest.deserialize(encoded))
        .isInstanceOf(OptimizeRuntimeException.class);
  }

  @Test
  public void deserializationFailsWhenStateIsNull() {
    // given - JSON with explicit null state
    final String jsonWithNullState =
        "{\"authorizationUri\":\"https://example.com/auth\","
            + "\"clientId\":\"client\",\"redirectUri\":\"http://localhost/cb\","
            + "\"state\":null,"
            + "\"authorizationRequestUri\":\"https://example.com/auth?foo=bar\"}";
    final String encoded =
        Base64.getUrlEncoder().encodeToString(jsonWithNullState.getBytes(StandardCharsets.UTF_8));

    // when/then
    assertThatThrownBy(() -> underTest.deserialize(encoded))
        .isInstanceOf(OptimizeRuntimeException.class);
  }
}
