/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.security.cloud;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthorizationRequestCookieValueMapperTest {

  private AuthorizationRequestCookieValueMapper underTest = new AuthorizationRequestCookieValueMapper();

  @Test
  public void equalityAfterDeserialization() {
    // given
    final OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
      .authorizationUri("https://weblogin.cloud.dev.ultrawombat.com/authorize")
      .redirectUri("http://localhost:8090/sso-callback?uuid=optimize")
      .clientId("whatever")
      .scopes(Set.of("openid", "profile"))
      .state("myState")
      .additionalParameters(Map.of("nonce", "whatever"))
      .authorizationUri("https://weblogin.cloud.dev.ultrawombat.com/authorize?response_type=code")
      .attributes(Map.of("registration_id", "auth0", "nonce", "anotherNonce"))
      .build();

    // when
    final String serializedString = underTest.serialize(authorizationRequest);
    final OAuth2AuthorizationRequest deserializedAuthorizationRequest = underTest.deserialize(serializedString);

    // then
    assertThat(deserializedAuthorizationRequest).usingRecursiveComparison().isEqualTo(authorizationRequest);
  }
}
