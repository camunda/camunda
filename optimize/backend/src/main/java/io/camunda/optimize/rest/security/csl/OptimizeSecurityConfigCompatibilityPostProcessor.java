/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * SPIKE (ADR-0036): backward-compatibility bridge for operators.
 *
 * <p>Reads Optimize's existing auth/security configuration and emits the equivalent {@code
 * camunda.security.*} properties so operators are not forced to migrate their config when Optimize
 * adopts CSL. Mirrors OC's {@code PersistentWebSessionPropertiesPostProcessor}.
 *
 * <p>The derived property source is added at the <b>end</b> of the source list (lowest precedence),
 * so any explicit {@code camunda.security.*} value an operator sets still wins.
 *
 * <p>Only active when {@code optimize.security.csl.enabled=true}. Obsolete keys (no meaning under
 * server-side sessions) are logged as deprecations, not applied.
 *
 * <p>Bridges the CCSM (Identity) and CCSaaS (Auth0) OIDC registrations from their {@code
 * CAMUNDA_OPTIMIZE_IDENTITY_*} / {@code CAMUNDA_OPTIMIZE_AUTH0_*} / {@code
 * CAMUNDA_OPTIMIZE_CLIENT_*} env-var interface (the {@code ${...}} placeholders in {@code
 * service-config.yaml}). The full key-by-key mapping is in CONFIG-COMPAT.md; a few finer keys there
 * are still marked TODO.
 */
public final class OptimizeSecurityConfigCompatibilityPostProcessor
    implements EnvironmentPostProcessor, Ordered {

  private final Log log;

  // Boot instantiates EnvironmentPostProcessors with this constructor and a DeferredLogFactory,
  // because logging is not yet initialised at this phase.
  public OptimizeSecurityConfigCompatibilityPostProcessor(final DeferredLogFactory logFactory) {
    this.log = logFactory.getLog(getClass());
  }

  @Override
  public int getOrder() {
    // After Spring's config-data processing, low precedence for our derived source.
    return Ordered.LOWEST_PRECEDENCE;
  }

  @Override
  public void postProcessEnvironment(
      final ConfigurableEnvironment env, final SpringApplication application) {
    final boolean cslEnabled =
        Boolean.parseBoolean(env.getProperty("optimize.security.csl.enabled", "false"));
    if (!cslEnabled) {
      return;
    }

    final Map<String, Object> derived = new HashMap<>();

    // Always-on for the CSL adoption.
    derived.put("camunda.security.authentication.method", "oidc");
    // Optimize's webapp chain is /**, so the CSL deny chain must be suppressed.
    derived.put("camunda.security.unhandled-paths-chain.enabled", "false");
    // Persist server-side sessions to Optimize's Elasticsearch (OptimizeSessionStoreAdapter), so
    // scaling stays affinity-free. Override to false for a single node to use in-memory sessions.
    derived.putIfAbsent("camunda.security.session.persistent.enabled", "true");
    // CSL requires a redirect-uri for the authorization-code login. CCSM default: reuse Optimize's
    // existing callback path so the pre-provisioned Identity client (which already registers
    // /api/authentication/callback) needs no change (the cloud block below overrides this to
    // /sso-callback for Auth0). CSL derives its redirection-endpoint listener path from this value
    // (ADR-0036), so the sent redirect_uri and the listener stay aligned. Spring expands {baseUrl}
    // (scheme://host:port + context path) at request time. putIfAbsent so an explicit
    // camunda.security.authentication.oidc.redirect-uri still wins.
    derived.putIfAbsent(
        "camunda.security.authentication.oidc.redirect-uri",
        "{baseUrl}/api/authentication/callback");

    // --- OIDC / Identity (CCSM). Primary interface is the CAMUNDA_OPTIMIZE_IDENTITY_* env vars.
    // ---
    mapIfPresent(
        env,
        derived,
        "CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL",
        "camunda.security.authentication.oidc.issuer-uri");
    mapIfPresent(
        env,
        derived,
        "CAMUNDA_OPTIMIZE_IDENTITY_CLIENTID",
        "camunda.security.authentication.oidc.client-id");
    mapIfPresent(
        env,
        derived,
        "CAMUNDA_OPTIMIZE_IDENTITY_CLIENTSECRET",
        "camunda.security.authentication.oidc.client-secret");
    mapIfPresent(
        env,
        derived,
        "CAMUNDA_OPTIMIZE_IDENTITY_AUDIENCE",
        "camunda.security.authentication.oidc.audiences");

    // --- OIDC / Auth0 (CCSaaS cloud). Primary interface is the CAMUNDA_OPTIMIZE_AUTH0_* /
    // CAMUNDA_OPTIMIZE_CLIENT_* env vars (see service-config.yaml, security.auth.cloud). Detected
    // by
    // the Auth0 client id being present; mutually exclusive with the CCSM block above. ---
    final String auth0ClientId = env.getProperty("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID");
    if (auth0ClientId != null && !auth0ClientId.isBlank()) {
      // SaaS keeps Optimize's legacy Auth0 callback exactly: root host (NO servlet context path)
      // plus ?uuid=<clusterId>. Auth0 cannot wildcard callback paths, so a single root callback is
      // registered and the cloud ingress rewrites it to /<clusterId>/sso-callback (routing by
      // uuid),
      // which the context-path'd app then receives. {baseScheme}://{baseHost}{basePort} excludes
      // the
      // servlet context path (unlike {baseUrl}), reproducing the legacy redirect_uri so no Auth0
      // re-registration is needed. put (not putIfAbsent) overrides the CCSM default set above; an
      // explicit operator redirect-uri still wins (this source is added at lowest precedence).
      final String clusterId = env.getProperty("CAMUNDA_OPTIMIZE_CLIENT_CLUSTERID");
      final String uuidParam =
          (clusterId != null && !clusterId.isBlank()) ? "?uuid=" + clusterId : "";
      derived.put(
          "camunda.security.authentication.oidc.redirect-uri",
          "{baseScheme}://{baseHost}{basePort}/sso-callback" + uuidParam);

      // Serve the app under /<clusterId> so it receives the ingress-rewritten callback and webapp
      // under that prefix, derived from the clusterId so operators need not also set
      // CAMUNDA_OPTIMIZE_CONTEXT_PATH. OptimizeTomcatConfig reads "contextPath" from the Spring
      // Environment (this derived source) ahead of its YAML config. putIfAbsent keeps an explicit
      // Spring "contextPath" winning.
      if (clusterId != null && !clusterId.isBlank()) {
        derived.putIfAbsent("contextPath", "/" + clusterId);
      }

      derived.putIfAbsent("camunda.security.authentication.oidc.client-id", auth0ClientId);
      warnDeprecated(
          "CAMUNDA_OPTIMIZE_AUTH0_CLIENTID", "camunda.security.authentication.oidc.client-id");
      mapIfPresent(
          env,
          derived,
          "CAMUNDA_OPTIMIZE_AUTH0_CLIENTSECRET",
          "camunda.security.authentication.oidc.client-secret");
      mapIfPresent(
          env,
          derived,
          "CAMUNDA_OPTIMIZE_CLIENT_AUDIENCE",
          "camunda.security.authentication.oidc.audiences");
      mapIfPresent(
          env,
          derived,
          "CAMUNDA_OPTIMIZE_AUTH0_ORGANIZATION",
          "camunda.security.authentication.oidc.organization-id");

      // Auth0 issuer is https://<customDomain>/; CSL discovers token/jwks/userinfo from it.
      final String auth0Domain = env.getProperty("CAMUNDA_OPTIMIZE_AUTH0_DOMAIN");
      if (auth0Domain != null && !auth0Domain.isBlank()) {
        derived.putIfAbsent(
            "camunda.security.authentication.oidc.issuer-uri", toAuth0IssuerUri(auth0Domain));
        warnDeprecated(
            "CAMUNDA_OPTIMIZE_AUTH0_DOMAIN", "camunda.security.authentication.oidc.issuer-uri");
      }

      // CSL SaaS config requires BOTH organization-id and cluster-id; set them together only when
      // both are present so a partial config never trips SaasConfiguration#isConfigured().
      final String organizationId = env.getProperty("CAMUNDA_OPTIMIZE_AUTH0_ORGANIZATION");
      if (organizationId != null
          && !organizationId.isBlank()
          && clusterId != null
          && !clusterId.isBlank()) {
        derived.putIfAbsent("camunda.security.saas.organization-id", organizationId);
        derived.putIfAbsent("camunda.security.saas.cluster-id", clusterId);
      }
    }

    // --- Public API JWT (api.jwtSetUri / api.audience). ---
    mapIfPresent(
        env,
        derived,
        "SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI",
        "camunda.security.authentication.oidc.jwk-set-uri");
    mapIfPresent(
        env,
        derived,
        "CAMUNDA_OPTIMIZE_API_AUDIENCE",
        "camunda.security.authentication.oidc.audiences");

    // --- Response headers. HSTS max-age: negative disables the header in CSL. ---
    final String hstsMaxAge =
        env.getProperty("CAMUNDA_OPTIMIZE_SECURITY_RESPONSE_HEADERS_HSTS_MAX_AGE");
    if (hstsMaxAge != null && !hstsMaxAge.isBlank()) {
      if (Long.parseLong(hstsMaxAge.trim()) < 0) {
        derived.put("camunda.security.http-headers.hsts.disabled", "true");
      } else {
        derived.put("camunda.security.http-headers.hsts.max-age-in-seconds", hstsMaxAge.trim());
      }
      warnDeprecated(
          "CAMUNDA_OPTIMIZE_SECURITY_RESPONSE_HEADERS_HSTS_MAX_AGE",
          "camunda.security.http-headers.hsts.max-age-in-seconds");
    }
    // TODO(spike): Content-Security-Policy, X-Content-Type-Options (inverted ->
    // content-type-options.disabled),
    // token.lifeMin -> session timeout, redirectRootUrl -> oidc.redirect-uri, issuerBackendUrl ->
    // back-channel
    // endpoints, diagnosticsEnabled -> oidc.diagnostics. See CONFIG-COMPAT.md.

    warnObsolete(
        env,
        "CAMUNDA_OPTIMIZE_SECURITY_AUTH_TOKEN_SECRET",
        "self-signed token secret is unused with server-side sessions");
    warnObsolete(
        env,
        "CAMUNDA_OPTIMIZE_SECURITY_AUTH_COOKIE_MAX_SIZE",
        "cookie splitting is gone; the session-id cookie is small");
    warnObsolete(
        env,
        "OPTIMIZE_API_ACCESS_TOKEN",
        "static shared API token is not supported by the CSL bearer chain");

    if (!derived.isEmpty()) {
      // Add last so explicit camunda.security.* values keep precedence over the bridged defaults.
      env.getPropertySources().addLast(new MapPropertySource("optimizeCslCompatibility", derived));
      log.info("Bridged " + derived.size() + " legacy Optimize auth keys to camunda.security.*");
    }
  }

  private void mapIfPresent(
      final ConfigurableEnvironment env,
      final Map<String, Object> derived,
      final String legacyKey,
      final String cslKey) {
    final String value = env.getProperty(legacyKey);
    if (value != null && !value.isBlank()) {
      derived.putIfAbsent(cslKey, value);
      warnDeprecated(legacyKey, cslKey);
    }
  }

  // Auth0 issuer URI is https://<domain>/ (trailing slash required for OIDC discovery). Accepts a
  // bare domain or a full URL.
  private static String toAuth0IssuerUri(final String domain) {
    final String base =
        domain.startsWith("http://") || domain.startsWith("https://")
            ? domain
            : "https://" + domain;
    return base.endsWith("/") ? base : base + "/";
  }

  // Deprecation warning for a legacy key that still maps to a CSL property.
  private void warnDeprecated(final String legacyKey, final String replacement) {
    log.warn(
        "Optimize config '"
            + legacyKey
            + "' is deprecated; migrate to '"
            + replacement
            + "'. Support for the legacy key will be removed in 8.11.");
  }

  // Deprecation warning for a legacy key that no longer has any effect under CSL.
  private void warnObsolete(
      final ConfigurableEnvironment env, final String legacyKey, final String why) {
    if (env.getProperty(legacyKey) != null) {
      log.warn(
          "Optimize config '"
              + legacyKey
              + "' is deprecated and has no effect under CSL ("
              + why
              + "). It will be removed in 8.11.");
    }
  }
}
