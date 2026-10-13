/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.discovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Payload of {@code GET /.well-known/camunda-auth}. */
@NullMarked
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CliAuthDiscoveryResponse(int version, String method, List<Provider> providers) {

  public static final int VERSION = 1;

  /**
   * @param audience the audiences the cluster accepts in access tokens from this issuer, so a
   *     client can tell a token it cannot use apart from a rejected one
   * @param resource the RFC 8707 resource indicator a client sends when the identity provider
   *     derives the audience from it rather than from a scope
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Provider(
      String id,
      String issuer,
      @JsonProperty("client_id") String clientId,
      List<String> scopes,
      @JsonProperty("redirect_uri") String redirectUri,
      List<String> audience,
      @Nullable String resource) {}
}
