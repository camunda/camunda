/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.oauth;

/** Constants for OAuth2 and certificate-based authentication. */
public final class ClientAssertionConstants {

  // OAuth2 client assertion constants
  public static final String CLIENT_ASSERTION_TYPE_JWT_BEARER =
      "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
  public static final String CLIENT_ASSERTION_TYPE_PARAM = "client_assertion_type";
  public static final String CLIENT_ASSERTION_PARAM = "client_assertion";
  public static final String CLIENT_ASSERTION_GRANT_TYPE = "client_credentials";
  public static final String CLIENT_ASSERTION_GRANT_TYPE_PARAM = "grant_type";

  // OIDC registration constants
  public static final String OIDC_REGISTRATION_ID = "oidc";

  // Certificate user claims
  public static final String CERT_USER_ID = "certificate-user";
  public static final String CERT_USER_NAME = "Certificate User";
  public static final String CERT_USER_EMAIL = "certificate-user@camunda.local";
  public static final String CERT_USER_ROLE = "ROLE_USER";

  // Security constants
  public static final String SESSION_KEY = "camunda.certificate.auth";

  // Certificate constants
  public static final String KEYSTORE_TYPE_PKCS12 = "PKCS12";
  public static final String HASH_ALGORITHM_SHA1 = "SHA-1";

  private ClientAssertionConstants() {}
}
