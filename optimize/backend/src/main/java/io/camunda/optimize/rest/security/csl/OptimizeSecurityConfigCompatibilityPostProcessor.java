/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.security.api.model.config.SessionConfiguration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
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
 * service-config.yaml}), or from the {@code camunda.identity.issuer} / {@code
 * camunda.identity.clientId} / {@code camunda.identity.audience} / {@code
 * CAMUNDA_IDENTITY_CLIENT_SECRET} config surface the official {@code camunda-platform} Helm chart's
 * Optimize ConfigMap renders instead.
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
    applySessionPersistence(env, derived);
    bridgeIdentityOidc(env, derived);
    bridgeAuth0Cloud(env, derived);
    bridgePublicApiJwt(env, derived);
    bridgeAudiences(env, derived);
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
    // CSL needs a redirect-uri for authorization-code login and derives its listener path from it
    // (ADR-0038). CCSM reuses Optimize's existing /api/authentication/callback (no Identity-client
    // change); bridgeAuth0Cloud overrides it for Auth0. putIfAbsent so an explicit value wins.
    derived.putIfAbsent(OIDC_PREFIX + "redirect-uri", "{baseUrl}/api/authentication/callback");
  }

  /**
   * Turns on CSL's server-side sessions, which {@code OptimizeSessionStoreAdapter} persists in the
   * {@code web-session} index. Only for the Elasticsearch edition: the store has no OpenSearch
   * implementation yet, and CSL's session repository requires a {@code SessionStorePort} bean once
   * the property is set, so enabling it on OpenSearch would fail startup. OpenSearch therefore
   * keeps CSL's in-memory sessions (single-node only) until the OpenSearch store lands.
   */
  private void applySessionPersistence(
      final ConfigurableEnvironment env, final Map<String, Object> derived) {
    if (ConfigurationService.getDatabaseType(env) == DatabaseType.ELASTICSEARCH) {
      derived.putIfAbsent(SessionConfiguration.PERSISTENT_ENABLED_PROPERTY, "true");
    } else {
      LOG.warn(
          "Persistent web sessions are not enabled: the CSL session store is currently"
              + " Elasticsearch-only. Sessions are kept in memory, so they are lost on restart and"
              + " are not shared across Optimize instances.");
    }
  }

  // OIDC / Identity (CCSM), from the CAMUNDA_OPTIMIZE_IDENTITY_* env vars, or — when those are
  // unset, as with the official camunda-platform Helm chart's Optimize ConfigMap — from the
  // camunda.identity.* structured config it renders instead (application-ccsm.yaml) plus the
  // CAMUNDA_IDENTITY_CLIENT_SECRET env var it uses for the secret. Skipped when Auth0 is
  // configured (the two modes are mutually exclusive, cloud wins) to avoid a mixed registration.
  //
  // Deliberately NOT bridged: camunda.identity.issuerBackendUrl. CSL has a single issuer-uri, used
  // for OIDC discovery AND for validating each token's iss claim against the discovery document's
  // issuer field. Keycloak reports the same externally-configured issuer regardless of which URL
  // was dialed to reach it, so pointing issuer-uri at the backend-reachable URL would make
  // discovery fail (issuer mismatch) instead of fixing reachability. camunda.identity.issuer (the
  // browser-facing one) is the correct, and only, source for issuer-uri — mirroring the existing
  // CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL precedent, which never bridged _ISSUER_BACKEND_URL either.
  //
  // Also deliberately NOT bridged: camunda.identity.clientSecret, the config-file secret key that
  // application-ccsm.yaml also declares — the official Helm chart never renders it, only the
  // CAMUNDA_IDENTITY_CLIENT_SECRET env var, which is the only secret-carrying key bridged here.
  private void bridgeIdentityOidc(
      final ConfigurableEnvironment env, final Map<String, Object> derived) {
    if (isAuth0Configured(env)) {
      return;
    }
    mapIfPresent(env, derived, "CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL", OIDC_PREFIX + "issuer-uri");
    mapIfPresent(env, derived, "CAMUNDA_OPTIMIZE_IDENTITY_CLIENTID", OIDC_PREFIX + "client-id");
    mapIfPresent(
        env, derived, "CAMUNDA_OPTIMIZE_IDENTITY_CLIENTSECRET", OIDC_PREFIX + "client-secret");
    mapIfCslKeyUnset(env, derived, "camunda.identity.issuer", OIDC_PREFIX + "issuer-uri");

    // The Helm chart's Optimize ConfigMap can legitimately render clientId/CAMUNDA_IDENTITY_
    // CLIENT_SECRET while leaving camunda.identity.issuer blank. Bridging a client-id/secret with
    // no issuer-uri (and no authorization-uri/token-uri/jwk-set-uri) makes CSL's
    // ScopedClientRegistrationFactory throw IllegalStateException at startup, so this bridge is
    // all-or-nothing on the issuer-uri: withhold both credentials rather than trade a login problem
    // for a boot failure. Mirrors bridgeAuth0SaasOrgAndCluster's all-or-nothing precedent below.
    if (!derived.containsKey(OIDC_PREFIX + "issuer-uri")
        && env.getProperty(OIDC_PREFIX + "issuer-uri") == null) {
      final String clientId = env.getProperty("camunda.identity.clientId");
      final String clientSecret = env.getProperty("CAMUNDA_IDENTITY_CLIENT_SECRET");
      if (!isBlank(clientId) || !isBlank(clientSecret)) {
        LOG.warn(
            "Optimize config 'camunda.identity.clientId'/'CAMUNDA_IDENTITY_CLIENT_SECRET' cannot"
                + " be bridged without an OIDC issuer: 'camunda.identity.issuer' is blank and no"
                + " 'camunda.security.authentication.oidc.issuer-uri' is set explicitly."
                + " 'camunda.identity.issuerBackendUrl' cannot substitute for it (OIDC discovery"
                + " would fail against it, see the Javadoc above). Leaving the OIDC client"
                + " unconfigured rather than failing Optimize startup with a missing issuer.");
      }
      return;
    }

    mapIfCslKeyUnset(env, derived, "camunda.identity.clientId", OIDC_PREFIX + "client-id");
    mapIfCslKeyUnset(env, derived, "CAMUNDA_IDENTITY_CLIENT_SECRET", OIDC_PREFIX + "client-secret");
  }

  // OIDC / Auth0 (CCSaaS cloud), from the CAMUNDA_OPTIMIZE_AUTH0_* / CAMUNDA_OPTIMIZE_CLIENT_* env
  // vars (service-config.yaml, security.auth.cloud).
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

  private static boolean isAuth0Configured(final ConfigurableEnvironment env) {
    final String auth0ClientId = env.getProperty("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID");
    return auth0ClientId != null && !auth0ClientId.isBlank();
  }

  // Reproduces Optimize's legacy Auth0 callback so no re-registration is needed: root host (no
  // context path) + ?uuid=<clusterId>, which the cloud ingress rewrites to
  // /<clusterId>/sso-callback (Auth0 cannot wildcard callback paths). {baseScheme}://{baseHost}
  // {basePort} excludes the context path (unlike {baseUrl}); put() overrides the CCSM default.
  // The app runs under /<clusterId>, but contextPath is derived only when the operator set neither
  // the Spring "contextPath" key nor CAMUNDA_OPTIMIZE_CONTEXT_PATH (getContextPath reads the former
  // first), to stay non-breaking.
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
  }

  // CSL has one Set-valued audiences property, so we collect every legacy audience source that
  // applies to the active mode into one comma-joined value that Spring binds to the Set; the old
  // per-key putIfAbsent kept only the first and silently dropped the rest.
  //
  // CCSM validated two distinct audiences on two paths: the identity audience on the login/session
  // token and the public-API audience on the bearer path. Both must survive here.
  //
  // CSL applies one audiences set to both the login id_token decoder and the api bearer decoder,
  // so it must hold both audiences: the Auth0 login id_token's only aud is the webapp client id,
  // while public-API and M2M bearer tokens carry the resource audience. Mirrors the operator
  // (camunda/camunda-operator#4240). The public-API audience is a CCSM-only key, not fed here.
  private void bridgeAudiences(
      final ConfigurableEnvironment env, final Map<String, Object> derived) {
    final Set<String> audiences = new LinkedHashSet<>();
    if (isAuth0Configured(env)) {
      addAudience(env, audiences, "CAMUNDA_OPTIMIZE_CLIENT_AUDIENCE");
      // The client id is the login id_token's only aud; it is bridged to client-id elsewhere, so
      // add it as an accepted audience without emitting a deprecation warning for it here.
      addClientIdAsAudience(env, audiences);
    } else {
      addAudience(env, audiences, "CAMUNDA_OPTIMIZE_IDENTITY_AUDIENCE");
      addAudience(env, audiences, "CAMUNDA_OPTIMIZE_API_AUDIENCE");
      // camunda.identity.audience: the login/session-token audience the official camunda-platform
      // Helm chart's Optimize ConfigMap renders in place of CAMUNDA_OPTIMIZE_IDENTITY_AUDIENCE.
      // Only bridged when that legacy env var is absent: application-ccsm.yaml mirrors it into
      // camunda.identity.audience automatically for every docker-compose CCSM install, so bridging
      // both would double-warn for a value the operator only set once.
      if (isBlank(env.getProperty("CAMUNDA_OPTIMIZE_IDENTITY_AUDIENCE"))) {
        addAudience(env, audiences, "camunda.identity.audience");
      }
    }
    if (!audiences.isEmpty()) {
      derived.putIfAbsent(OIDC_PREFIX + "audiences", String.join(",", audiences));
    }
  }

  private void addClientIdAsAudience(
      final ConfigurableEnvironment env, final Set<String> audiences) {
    final String clientId = env.getProperty("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID");
    if (!isBlank(clientId)) {
      audiences.add(clientId.trim());
    }
  }

  private void addAudience(
      final ConfigurableEnvironment env, final Set<String> audiences, final String legacyKey) {
    final String value = env.getProperty(legacyKey);
    if (!isBlank(value)) {
      audiences.add(value.trim());
      warnDeprecated(legacyKey, OIDC_PREFIX + "audiences");
    }
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
    final String replacement;
    if (maxAgeSeconds < 0) {
      // A negative legacy max-age means "disable HSTS", which maps to hsts.disabled, not max-age.
      derived.put("camunda.security.http-headers.hsts.disabled", "true");
      replacement = "camunda.security.http-headers.hsts.disabled";
    } else {
      derived.put("camunda.security.http-headers.hsts.max-age-in-seconds", hstsMaxAge.trim());
      replacement = "camunda.security.http-headers.hsts.max-age-in-seconds";
    }
    warnDeprecated("CAMUNDA_OPTIMIZE_SECURITY_RESPONSE_HEADERS_HSTS_MAX_AGE", replacement);
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
    if (!isBlank(value)) {
      derived.putIfAbsent(cslKey, value);
      warnDeprecated(legacyKey, cslKey);
    }
  }

  // Bridges a legacy key into a CSL property, but only if no higher-priority legacy key has
  // already supplied a value for the same CSL property. Without this, docker-compose CCSM
  // installs that already set CAMUNDA_OPTIMIZE_IDENTITY_* would get a second, confusing
  // deprecation warning for the identical value Optimize's own application-ccsm.yaml mirrors into
  // camunda.identity.* automatically (an internal resource-file detail, not an operator choice).
  private void mapIfCslKeyUnset(
      final ConfigurableEnvironment env,
      final Map<String, Object> derived,
      final String legacyKey,
      final String cslKey) {
    if (derived.containsKey(cslKey)) {
      return;
    }
    mapIfPresent(env, derived, legacyKey, cslKey);
  }

  private static boolean isBlank(final String value) {
    return value == null || value.isBlank();
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
