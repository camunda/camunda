/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.model;

/** OAuth2 grant types relevant to token exchange and delegation. */
public enum GrantType {
  TOKEN_EXCHANGE("urn:ietf:params:oauth:grant-type:token-exchange"),
  CLIENT_CREDENTIALS("client_credentials"),
  JWT_BEARER("urn:ietf:params:oauth:grant-type:jwt-bearer"),
  AUTHORIZATION_CODE("authorization_code");

  private final String uri;

  GrantType(final String uri) {
    this.uri = uri;
  }

  public String uri() {
    return uri;
  }

  public static GrantType fromUri(final String uri) {
    for (final GrantType type : values()) {
      if (type.uri.equals(uri)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown grant type URI: " + uri);
  }
}
