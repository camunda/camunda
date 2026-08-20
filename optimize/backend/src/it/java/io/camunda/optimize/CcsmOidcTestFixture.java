/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import java.util.Map;

/**
 * The static OIDC client registration CSL needs to boot under the {@code ccsm-test} group,
 * mirroring the fixture already shipped in {@code
 * optimize/backend/src/it/resources/application-ccsm.yaml} and the unit test {@code
 * CslSecurityChainSelectionTest}. Kept as one constant so the values used across the Java-side
 * fixtures ({@link CcsmOidcTestFixture#STATIC_OIDC_PROPERTIES}'s callers) can't drift apart from
 * each other.
 */
public final class CcsmOidcTestFixture {

  public static final Map<String, String> STATIC_OIDC_PROPERTIES =
      Map.of(
          "camunda.security.authentication.oidc.client-id",
          "it-client",
          "camunda.security.authentication.oidc.client-secret",
          "it-secret",
          "camunda.security.authentication.oidc.authorization-uri",
          "https://idp.example.com/authorize",
          "camunda.security.authentication.oidc.token-uri",
          "https://idp.example.com/token",
          "camunda.security.authentication.oidc.jwk-set-uri",
          "https://idp.example.com/jwks");

  private CcsmOidcTestFixture() {}
}
