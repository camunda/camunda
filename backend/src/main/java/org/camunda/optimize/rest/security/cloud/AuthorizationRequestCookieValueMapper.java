/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.security.cloud;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponseType;

import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

public class AuthorizationRequestCookieValueMapper {
  private final ObjectMapper objectMapper;

  public AuthorizationRequestCookieValueMapper() {
    this.objectMapper = new ObjectMapper();
    this.objectMapper.addMixIn(OAuth2AuthorizationRequest.class, OAuth2AuthorizationRequestMixin.class);
    this.objectMapper.addMixIn(OAuth2AuthorizationResponseType.class, OAuth2AuthorizationResponseTypeMixin.class);
    this.objectMapper.addMixIn(AuthorizationGrantType.class, AuthorizationGrantTypeMixin.class);
  }

  @SneakyThrows
  public String serialize(final OAuth2AuthorizationRequest authorizationRequest) {
    return Base64.getUrlEncoder().encodeToString(objectMapper.writeValueAsString(authorizationRequest).getBytes(UTF_8));
  }

  @SneakyThrows
  public OAuth2AuthorizationRequest deserialize(final String value) {
    return objectMapper.readValue(Base64.getUrlDecoder().decode(value), OAuth2AuthorizationRequest.class);
  }

  private abstract static class AuthorizationGrantTypeMixin {
    @JsonCreator
    public AuthorizationGrantTypeMixin(@JsonProperty("value") String value) {
    }
  }

  private abstract static class OAuth2AuthorizationRequestMixin {
    @JsonProperty("grantType")
    AuthorizationGrantType authorizationGrantType;
  }

  private abstract static class OAuth2AuthorizationResponseTypeMixin {
    @JsonCreator
    public OAuth2AuthorizationResponseTypeMixin(@JsonProperty("value") String value) {
    }
  }


}
