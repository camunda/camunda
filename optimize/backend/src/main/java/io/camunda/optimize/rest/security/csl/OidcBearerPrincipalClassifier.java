/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.core.oidc.OidcPrincipalLoader;
import io.camunda.security.core.oidc.OidcPrincipalLoader.OidcPrincipals;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classifies a CCSM bearer token's subject as a human user or a machine-to-machine (M2M) client,
 * using the same {@link OidcPrincipalLoader} mechanism {@code zeebe/gateway-grpc}'s {@code
 * AuthenticationHandler} already uses for gRPC callers.
 *
 * <p>Unlike that gRPC handler, which rejects a token outright when neither claim resolves, this
 * classifier fails closed by assuming the token belongs to a user: a legacy M2M client whose IdP
 * does not populate the configured claim paths must not be newly rejected by this class, but a user
 * token must not silently skip the Optimize permission check either. See camunda/camunda#63372.
 *
 * <p>When {@link OidcConfiguration#getClientIdClaim()} is not configured, this class falls back to
 * {@value #DEFAULT_CLIENT_ID_CLAIM} — the claim Keycloak populates on a client-credentials token's
 * access token, and the default the orchestration cluster's own Helm chart already uses for the
 * equivalent setting. Optimize's own configuration surface has no such default today, and without
 * one an unconfigured deployment would classify every bearer token as a user, checking the Optimize
 * permission against M2M clients that never needed it and were never granted it.
 */
public final class OidcBearerPrincipalClassifier {

  private static final String DEFAULT_CLIENT_ID_CLAIM = "client_id";

  private static final Logger LOG = LoggerFactory.getLogger(OidcBearerPrincipalClassifier.class);

  private final OidcPrincipalLoader principalLoader;
  private final boolean preferUsernameClaim;

  public OidcBearerPrincipalClassifier(final OidcConfiguration oidcConfiguration) {
    final String clientIdClaim =
        StringUtils.isBlank(oidcConfiguration.getClientIdClaim())
            ? DEFAULT_CLIENT_ID_CLAIM
            : oidcConfiguration.getClientIdClaim();
    principalLoader = new OidcPrincipalLoader(oidcConfiguration.getUsernameClaim(), clientIdClaim);
    preferUsernameClaim = oidcConfiguration.isPreferUsernameClaim();
  }

  /**
   * @return {@code true} when the token's subject is a user, or cannot be classified at all (fail
   *     closed); {@code false} only when the subject is clearly an M2M client.
   */
  public boolean requiresOptimizePermissionCheck(final Map<String, Object> claims) {
    final OidcPrincipals principals;
    try {
      principals = principalLoader.load(claims);
    } catch (final RuntimeException e) {
      LOG.debug("Could not classify the bearer token's subject, assuming a user", e);
      return true;
    }

    if (principals.username() == null && principals.clientId() == null) {
      LOG.debug("Bearer token carries neither a username nor a client ID claim, assuming a user");
      return true;
    }

    return (preferUsernameClaim && principals.username() != null) || principals.clientId() == null;
  }
}
