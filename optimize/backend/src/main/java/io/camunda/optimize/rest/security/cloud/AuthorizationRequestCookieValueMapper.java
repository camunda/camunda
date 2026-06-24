/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.cloud;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponseType;

public class AuthorizationRequestCookieValueMapper {

  private final ObjectMapper objectMapper;

  public AuthorizationRequestCookieValueMapper() {
    objectMapper = new ObjectMapper();
    objectMapper.addMixIn(
        OAuth2AuthorizationResponseType.class, OAuth2AuthorizationResponseTypeMixin.class);
    objectMapper.addMixIn(AuthorizationGrantType.class, AuthorizationGrantTypeMixin.class);

    // OAuth2AuthorizationRequest uses a builder pattern and has no default constructor.
    // In Spring Security 7, the class structure changed, requiring a custom deserializer.
    final SimpleModule module = new SimpleModule();
    module.addDeserializer(
        OAuth2AuthorizationRequest.class, new OAuth2AuthorizationRequestDeserializer());
    objectMapper.registerModule(module);
  }

  public String serialize(final OAuth2AuthorizationRequest authorizationRequest) {
    try {
      return Base64.getUrlEncoder()
          .encodeToString(objectMapper.writeValueAsString(authorizationRequest).getBytes(UTF_8));
    } catch (final JsonProcessingException e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  public OAuth2AuthorizationRequest deserialize(final String value) {
    try {
      return objectMapper.readValue(
          Base64.getUrlDecoder().decode(value), OAuth2AuthorizationRequest.class);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  private abstract static class AuthorizationGrantTypeMixin {

    @JsonCreator
    AuthorizationGrantTypeMixin(@JsonProperty("value") final String value) {}
  }

  private abstract static class OAuth2AuthorizationResponseTypeMixin {

    @JsonCreator
    OAuth2AuthorizationResponseTypeMixin(@JsonProperty("value") final String value) {}
  }

  private static final class OAuth2AuthorizationRequestDeserializer
      extends JsonDeserializer<OAuth2AuthorizationRequest> {

    private static final TypeReference<Set<String>> SET_TYPE_REF = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {};

    @Override
    public OAuth2AuthorizationRequest deserialize(
        final JsonParser parser, final DeserializationContext context) throws IOException {
      final JsonNode node = parser.getCodec().readTree(parser);
      final ObjectMapper mapper = (ObjectMapper) parser.getCodec();

      final String authorizationUri = getText(node, "authorizationUri");
      final String clientId = getText(node, "clientId");
      final String redirectUri = getText(node, "redirectUri");
      final String state = getText(node, "state");
      final String authorizationRequestUri = getText(node, "authorizationRequestUri");

      final OAuth2AuthorizationRequest.Builder builder =
          OAuth2AuthorizationRequest.authorizationCode()
              .authorizationUri(authorizationUri)
              .clientId(clientId)
              .redirectUri(redirectUri)
              .state(state)
              .authorizationRequestUri(authorizationRequestUri);

      if (node.has("scopes") && !node.get("scopes").isNull()) {
        builder.scopes(mapper.convertValue(node.get("scopes"), SET_TYPE_REF));
      }
      if (node.has("additionalParameters") && !node.get("additionalParameters").isNull()) {
        builder.additionalParameters(
            mapper.convertValue(node.get("additionalParameters"), MAP_TYPE_REF));
      }
      if (node.has("attributes") && !node.get("attributes").isNull()) {
        final Map<String, Object> attributes =
            mapper.convertValue(node.get("attributes"), MAP_TYPE_REF);
        builder.attributes(attrs -> attrs.putAll(attributes));
      }

      return builder.build();
    }

    private String getText(final JsonNode node, final String fieldName) {
      return node.has(fieldName) && !node.get(fieldName).isNull()
          ? node.get(fieldName).asText()
          : null;
    }
  }
}
