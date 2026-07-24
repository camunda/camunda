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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Backward-compatibility bridge for operators adopting CSL (ADR-0038).
 *
 * <p>Reads Optimize's existing auth/security configuration and emits the equivalent {@code
 * camunda.security.*} properties, so operators are not forced to migrate their configuration when
 * Optimize adopts CSL. Mirrors OC's {@code PersistentWebSessionPropertiesPostProcessor}.
 *
 * <p>The derived property source is added at the <b>end</b> of the source list (lowest precedence),
 * so any explicit {@code camunda.security.*} value an operator sets still wins.
 *
 * <p>Only active when {@code optimize.security.csl.enabled=true}. Keys with no meaning under CSL
 * (server-side sessions, no self-signed tokens) are logged as deprecated and otherwise ignored.
 *
 * <p>Bridges the CCSM (Identity) and CCSaaS (Auth0) OIDC registrations from their {@code
 * CAMUNDA_OPTIMIZE_IDENTITY_*} / {@code CAMUNDA_OPTIMIZE_AUTH0_*} / {@code
 * CAMUNDA_OPTIMIZE_CLIENT_*} env-var interface (the {@code ${...}} placeholders in {@code
 * service-config.yaml}).
 *
 * <p>Every recognised legacy key logs a deprecation warning naming its {@code camunda.security.*}
 * replacement (or, for obsolete keys, stating that it no longer has any effect). Legacy keys stay
 * supported until 8.11 and are removed afterwards.
 */
public final class OptimizeSecurityConfigCompatibilityPostProcessor
    implements EnvironmentPostProcessor, Ordered {

  static final String CSL_ENABLED_PROPERTY = "optimize.security.csl.enabled";
  static final String COMPATIBILITY_PROPERTY_SOURCE_NAME = "optimizeCslCompatibility";

  private static final String OIDC_PREFIX = "camunda.security.authentication.oidc.";
  private static final String LEGACY_KEY_REMOVAL_VERSION = "8.11";

  private static final Logger LOG =
      LoggerFactory.getLogger(OptimizeSecurityConfigCompatibilityPostProcessor.class);

  @Override
  public int getOrder() {
    // After Spring's config-data processing; lowest precedence for our derived source.
    return Ordered.LOWEST_PRECEDENCE;
  }

  @Override
  public void postProcessEnvironment(
      final ConfigurableEnvironment env, final SpringApplication application) {
    if (!Boolean.parseBoolean(env.getProperty(CSL_ENABLED_PROPERTY, "false"))) {
      return;
    }

    final Map<String, Object> derived = new HashMap<>();
    applyAlwaysOnDefaults(derived);
    bridgeIdentityOidc(env, derived);
    bridgeAuth0Cloud(env, derived);
    bridgePublicApiJwt(env, derived);
    bridgeResponseHeaders(env, derived);
    warnObsoleteKeys(env);

    if (!derived.isEmpty()) {
      // Add last so explicit camunda.security.* values keep precedence over the bridged defaults.
      env.getPropertySources()
          .addLast(new MapPropertySource(COMPATIBILITY_PROPERTY_SOURCE_NAME, derived));
      LOG.info(
          "Optimize CSL compatibility bridge applied {} camunda.security.* properties"
              + " (always-on defaults plus values derived from legacy Optimize config).",
          derived.size());
    }
  }

  // Always applied when CSL is enabled, independent of any legacy key.
  private void applyAlwaysOnDefaults(final Map<String, Object> derived) {
    derived.put("camunda.security.authentication.method", "oidc");
    // Optimize's webapp chain is /**, so the CSL deny chain must be suppressed.
    derived.put("camunda.security.authentication.catch-all-unhandled-paths-enabled", "false");
    // Persist server-side sessions to Optimize's Elasticsearch (OptimizeSessionStoreAdapter), so
    // scaling stays affinity-free. Override to false for a single node to use in-memory sessions.
    derived.putIfAbsent("camunda.security.session.persistent.enabled", "true");
    // CSL needs a redirect-uri for authorization-code login. CCSM default reuses Optimize's
    // existing /api/authentication/callback, so the pre-provisioned Identity client needs no change
    // (bridgeAuth0Cloud overrides it to /sso-callback for Auth0). CSL derives its
    // redirection-endpoint listener path from this value (ADR-0038); Spring expands {baseUrl} at
    // request time. putIfAbsent so an explicit oidc.redirect-uri wins.
    derived.putIfAbsent(OIDC_PREFIX + "redirect-uri", "{baseUrl}/api/authentication/callback");
  }

  // OIDC / Identity (CCSM). Primary interface is the CAMUNDA_OPTIMIZE_IDENTITY_* env vars.
  // Skipped when Auth0 (CCSaaS cloud) is configured: the two modes are mutually exclusive, cloud
  // wins, and running both would produce a mixed OIDC registration.
  private void bridgeIdentityOidc(
      final ConfigurableEnvironment env, final Map<String, Object> derived) {
    if (isAuth0Configured(env)) {
      return;
    }
    mapIfPresent(env, derived, "CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL", OIDC_PREFIX + "issuer-uri");
    mapIfPresent(env, derived, "CAMUNDA_OPTIMIZE_IDENTITY_CLIENTID", OIDC_PREFIX + "client-id");
    mapIfPresent(
        env, derived, "CAMUNDA_OPTIMIZE_IDENTITY_CLIENTSECRET", OIDC_PREFIX + "client-secret");
    mapIfPresent(env, derived, "CAMUNDA_OPTIMIZE_IDENTITY_AUDIENCE", OIDC_PREFIX + "audiences");
  }

  // OIDC / Auth0 (CCSaaS cloud). Primary interface is the CAMUNDA_OPTIMIZE_AUTH0_* /
  // CAMUNDA_OPTIMIZE_CLIENT_* env vars (service-config.yaml, security.auth.cloud). Detected by the
  // Auth0 client id being present; mutually exclusive with the CCSM block above.
  private void bridgeAuth0Cloud(
      final ConfigurableEnvironment env, final Map<String, Object> derived) {
    if (!isAuth0Configured(env)) {
      return;
    }
    final String auth0ClientId = env.getProperty("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID");
    final String clusterId = env.getProperty("CAMUNDA_OPTIMIZE_CLIENT_CLUSTERID");
    bridgeAuth0RedirectAndContextPath(env, derived, clusterId);
    bridgeAuth0Credentials(env, derived, auth0ClientId);
    bridgeAuth0SaasOrgAndCluster(env, derived, clusterId);
  }

  // CCSaaS cloud is detected by the Auth0 client id being present.
  private static boolean isAuth0Configured(final ConfigurableEnvironment env) {
    final String auth0ClientId = env.getProperty("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID");
    return auth0ClientId != null && !auth0ClientId.isBlank();
  }

  // Reproduces Optimize's legacy Auth0 callback: root host (no context path) + ?uuid=<clusterId>.
  // Auth0 cannot wildcard callback paths, so one root callback is registered and the cloud ingress
  // rewrites /sso-callback?uuid=<clusterId> to /<clusterId>/sso-callback. {baseScheme}://{baseHost}
  // {basePort} excludes the context path (unlike {baseUrl}), so no Auth0 re-registration is needed;
  // put() overrides the CCSM default, an explicit operator redirect-uri still wins (lowest
  // precedence).
  //
  // The app is served under /<clusterId>, derived from clusterId so CAMUNDA_OPTIMIZE_CONTEXT_PATH
  // is not needed. OptimizeTomcatConfig#getContextPath() reads the Spring "contextPath" key before
  // the legacy CAMUNDA_OPTIMIZE_CONTEXT_PATH, so we derive it only when the operator set neither
  // (hasExplicitContextPath), to stay non-breaking.
  private void bridgeAuth0RedirectAndContextPath(
      final ConfigurableEnvironment env,
      final Map<String, Object> derived,
      final String clusterId) {
    final boolean hasClusterId = clusterId != null && !clusterId.isBlank();
    final String uuidParam = hasClusterId ? "?uuid=" + clusterId : "";
    derived.put(
        OIDC_PREFIX + "redirect-uri",
        "{baseScheme}://{baseHost}{basePort}/sso-callback" + uuidParam);
    if (hasClusterId) {
      if (!hasExplicitContextPath(env)) {
        derived.put("contextPath", "/" + clusterId);
      }
      warnDeprecated(
          "CAMUNDA_OPTIMIZE_CLIENT_CLUSTERID",
          "camunda.security.saas.cluster-id (also derives the redirect-uri and contextPath)");
    }
  }

  // True when the operator set a context path via either the Spring "contextPath" property or the
  // legacy CAMUNDA_OPTIMIZE_CONTEXT_PATH env var.
  private static boolean hasExplicitContextPath(final ConfigurableEnvironment env) {
    return env.getProperty("contextPath") != null
        || env.getProperty("CAMUNDA_OPTIMIZE_CONTEXT_PATH") != null;
  }

  private void bridgeAuth0Credentials(
      final ConfigurableEnvironment env,
      final Map<String, Object> derived,
      final String auth0ClientId) {
    derived.putIfAbsent(OIDC_PREFIX + "client-id", auth0ClientId);
    warnDeprecated("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID", OIDC_PREFIX + "client-id");
    mapIfPresent(
        env, derived, "CAMUNDA_OPTIMIZE_AUTH0_CLIENTSECRET", OIDC_PREFIX + "client-secret");
    mapIfPresent(env, derived, "CAMUNDA_OPTIMIZE_CLIENT_AUDIENCE", OIDC_PREFIX + "audiences");
    mapIfPresent(
        env, derived, "CAMUNDA_OPTIMIZE_AUTH0_ORGANIZATION", OIDC_PREFIX + "organization-id");
    // Pass the cloud Accounts API audience as Auth0's `audience` authorize param, so the login
    // token is accepted by the Accounts API (CCSaaSUserCache). Legacy baked it into the authorize
    // URL; CSL reads it from authorize-request.additional-parameters.
    mapIfPresent(
        env,
        derived,
        "CAMUNDA_OPTIMIZE_M2M_ACCOUNTS_AUTH0_AUDIENCE",
        OIDC_PREFIX + "authorize-request.additional-parameters.audience");

    // Auth0 issuer is https://<customDomain>/; CSL discovers token/jwks/userinfo from it.
    final String auth0Domain = env.getProperty("CAMUNDA_OPTIMIZE_AUTH0_DOMAIN");
    if (auth0Domain != null && !auth0Domain.isBlank()) {
      derived.putIfAbsent(OIDC_PREFIX + "issuer-uri", toAuth0IssuerUri(auth0Domain));
      warnDeprecated("CAMUNDA_OPTIMIZE_AUTH0_DOMAIN", OIDC_PREFIX + "issuer-uri");
    }
  }

  // CSL SaaS config requires BOTH organization-id and cluster-id; set them together only when both
  // are present so a partial config never trips SaasConfiguration#isConfigured().
  private void bridgeAuth0SaasOrgAndCluster(
      final ConfigurableEnvironment env,
      final Map<String, Object> derived,
      final String clusterId) {
    final String organizationId = env.getProperty("CAMUNDA_OPTIMIZE_AUTH0_ORGANIZATION");
    final boolean hasOrganizationId = organizationId != null && !organizationId.isBlank();
    final boolean hasClusterId = clusterId != null && !clusterId.isBlank();
    if (hasOrganizationId && hasClusterId) {
      derived.putIfAbsent("camunda.security.saas.organization-id", organizationId);
      derived.putIfAbsent("camunda.security.saas.cluster-id", clusterId);
    }
  }

  // Public API JWT (api.jwtSetUri / api.audience).
  private void bridgePublicApiJwt(
      final ConfigurableEnvironment env, final Map<String, Object> derived) {
    mapIfPresent(
        env,
        derived,
        "SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI",
        OIDC_PREFIX + "jwk-set-uri");
    mapIfPresent(env, derived, "CAMUNDA_OPTIMIZE_API_AUDIENCE", OIDC_PREFIX + "audiences");
  }

  // HSTS max-age: negative disables the header in CSL.
  private void bridgeResponseHeaders(
      final ConfigurableEnvironment env, final Map<String, Object> derived) {
    final String hstsMaxAge =
        env.getProperty("CAMUNDA_OPTIMIZE_SECURITY_RESPONSE_HEADERS_HSTS_MAX_AGE");
    if (hstsMaxAge == null || hstsMaxAge.isBlank()) {
      return;
    }
    final long maxAgeSeconds;
    try {
      maxAgeSeconds = Long.parseLong(hstsMaxAge.trim());
    } catch (final NumberFormatException e) {
      // A compatibility bridge must never fail startup on a malformed legacy value; skip it and
      // let CSL apply its own HSTS default. Value is not logged (may be operator-sensitive).
      LOG.warn(
          "Ignoring 'CAMUNDA_OPTIMIZE_SECURITY_RESPONSE_HEADERS_HSTS_MAX_AGE': not a valid number;"
              + " leaving 'camunda.security.http-headers.hsts.*' at its CSL default.");
      return;
    }
    if (maxAgeSeconds < 0) {
      derived.put("camunda.security.http-headers.hsts.disabled", "true");
    } else {
      derived.put("camunda.security.http-headers.hsts.max-age-in-seconds", hstsMaxAge.trim());
    }
    warnDeprecated(
        "CAMUNDA_OPTIMIZE_SECURITY_RESPONSE_HEADERS_HSTS_MAX_AGE",
        "camunda.security.http-headers.hsts.max-age-in-seconds");
  }

  // Legacy keys that have no meaning under CSL (server-side sessions, no self-signed tokens, no
  // deprecated response headers). Their presence never fails startup; it only logs a warning.
  private void warnObsoleteKeys(final ConfigurableEnvironment env) {
    warnObsolete(
        env,
        "CAMUNDA_OPTIMIZE_SECURITY_AUTH_TOKEN_SECRET",
        "self-signed session tokens are not minted under CSL's server-side sessions");
    warnObsolete(
        env,
        "CAMUNDA_OPTIMIZE_SECURITY_AUTH_COOKIE_MAX_SIZE",
        "cookie splitting is gone; the session-id cookie is small");
    warnObsolete(
        env,
        "CAMUNDA_OPTIMIZE_SECURITY_AUTH_COOKIE_SAME_SITE_ENABLED",
        "CSL sets the SameSite attribute on its session cookie automatically");
    warnObsolete(
        env,
        "OPTIMIZE_API_ACCESS_TOKEN",
        "a static shared API token is not supported by the CSL bearer chain");
    warnObsolete(
        env,
        "security.responseHeaders.X-XSS-Protection",
        "CSL does not emit this deprecated header");
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

  // Deprecation warning for a legacy key that still maps to a CSL property. Never logs the value,
  // only the key names, so secrets (client secrets, tokens) never reach the log.
  private void warnDeprecated(final String legacyKey, final String replacement) {
    LOG.warn(
        "Optimize config '{}' is deprecated; migrate to '{}'. Support for the legacy key will be"
            + " removed in {}.",
        legacyKey,
        replacement,
        LEGACY_KEY_REMOVAL_VERSION);
  }

  // Deprecation warning for a legacy key that no longer has any effect under CSL.
  private void warnObsolete(
      final ConfigurableEnvironment env, final String legacyKey, final String why) {
    if (env.getProperty(legacyKey) != null) {
      LOG.warn(
          "Optimize config '{}' is deprecated and has no effect under CSL ({}). It will be removed"
              + " in {}.",
          legacyKey,
          why,
          LEGACY_KEY_REMOVAL_VERSION);
    }
  }
}
