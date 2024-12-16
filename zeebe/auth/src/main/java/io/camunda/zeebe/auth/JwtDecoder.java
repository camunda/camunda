/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class JwtDecoder {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final String token;
  private DecodedJWT decodedJwt;

  public JwtDecoder(final String token) {
    if (token == null || token.trim().isEmpty()) {
      throw new IllegalArgumentException("Token cannot be null or empty");
    }
    this.token = token;
  }

  /**
   * Explicitly decode the JWT. This is optional since claims access auto-decodes.
   *
   * @return the current JwtDecoder instance
   * @throws RuntimeException if decoding fails
   */
  public JwtDecoder decode() {
    if (decodedJwt == null) {
      try {
        decodedJwt = JWT.decode(token);
      } catch (final Exception e) {
        throw new RuntimeException("Failed to decode JWT: " + e.getMessage(), e);
      }
    }
    return this;
  }

  /**
   * Retrieves all claims as a Map<String, Object>. Automatically decodes the token if it hasn't
   * been decoded yet.
   *
   * @return Map of claims
   */
  public Map<String, Object> getClaims() {
    if (decodedJwt == null) {
      decode();
    }
    return convertClaimsToMap();
  }

  /**
   * Converts claims from DecodedJWT into a Map<String, Object>.
   *
   * @return Map of claims
   */
  private Map<String, Object> convertClaimsToMap() {
    final Map<String, Claim> claims = decodedJwt.getClaims();
    if (claims.isEmpty()) {
      return Collections.emptyMap();
    }

    final Map<String, Object> claimsMap = new HashMap<>();
    claims.forEach((key, claim) -> claimsMap.put(key, convertClaimValue(claim)));
    return claimsMap;
  }

  /**
   * Converts a Claim object into a generic Java Object. Supports nested JSON objects, lists, and
   * standard data types.
   *
   * @param claim the Claim to convert
   * @return the value as an Object
   */
  private Object convertClaimValue(final Claim claim) {
    if (claim.isNull()) {
      return null;
    }

    try {
      return OBJECT_MAPPER.readValue(claim.asString(), new TypeReference<Map<String, Object>>() {});
    } catch (final Exception e) {
      // Fallback to basic types
      if (claim.asBoolean() != null) {
        return claim.asBoolean();
      }
      if (claim.asInt() != null) {
        return claim.asInt();
      }
      if (claim.asLong() != null) {
        return claim.asLong();
      }
      if (claim.asDouble() != null) {
        return claim.asDouble();
      }
      if (claim.asList(String.class) != null) {
        return claim.asList(String.class);
      }
      return claim.asString(); // Default to string if no other type matches
    }
  }
}
