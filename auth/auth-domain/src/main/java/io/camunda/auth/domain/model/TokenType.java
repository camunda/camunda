/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.model;

/** Token types as defined by RFC 8693 Section 3. */
public enum TokenType {
  ACCESS_TOKEN("urn:ietf:params:oauth:token-type:access_token"),
  REFRESH_TOKEN("urn:ietf:params:oauth:token-type:refresh_token"),
  ID_TOKEN("urn:ietf:params:oauth:token-type:id_token"),
  SAML1("urn:ietf:params:oauth:token-type:saml1"),
  SAML2("urn:ietf:params:oauth:token-type:saml2"),
  JWT("urn:ietf:params:oauth:token-type:jwt");

  private final String uri;

  TokenType(final String uri) {
    this.uri = uri;
  }

  public String uri() {
    return uri;
  }

  public static TokenType fromUri(final String uri) {
    for (final TokenType type : values()) {
      if (type.uri.equals(uri)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown token type URI: " + uri);
  }
}
