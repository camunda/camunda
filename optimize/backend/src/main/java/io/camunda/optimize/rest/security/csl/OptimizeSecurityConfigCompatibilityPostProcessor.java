/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.optimize.OptimizeApplicationDetector;
import io.camunda.security.api.model.config.SessionConfiguration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
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
 * <p>Only active while CSL is active, i.e. {@code optimize.security.csl.enabled} is {@code true} or
 * absent — the default since 8.10 (camunda/camunda#58483). Keys with no meaning under CSL
 * (server-side sessions, no self-signed tokens) are logged as deprecated and otherwise ignored. An
 * operator who explicitly sets the flag to {@code false} gets a startup warning naming the 8.11
 * removal instead (camunda/camunda#58484, camunda/camunda#58485).
 *
 * <p>Also canonicalizes {@code optimize.security.csl.enabled} itself to a literal {@code "true"} or
 * {@code "false"}, overriding any other value (e.g. a typo). The CSL and legacy security beans are
 * gated by {@code @ConditionalOnProperty} on this same flag with exact-match {@code havingValue}s,
 * so without this, an unrecognised value would leave neither security stack active.
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
  static final String CANONICAL_FLAG_PROPERTY_SOURCE_NAME = "optimizeCslFlagCanonical";

  private static final String OIDC_PREFIX = "camunda.security.authentication.oidc.";
  private static final String IDENTITY_ISSUER = "camunda.identity.issuer";
  private static final String IDENTITY_ISSUER_BACKEND_URL = "camunda.identity.issuerBackendUrl";
  private static final String LEGACY_ISSUER_ENV_VAR = "CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL";
  private static final String LEGACY_ISSUER_BACKEND_URL_ENV_VAR =
      "CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_BACKEND_URL";
  private static final String IDENTITY_TYPE = "camunda.identity.type";
  private static final String IDENTITY_TYPE_ENV_VAR = "CAMUNDA_IDENTITY_TYPE";
  private static final String KEYCLOAK_IDENTITY_TYPE = "KEYCLOAK";
  private static final String ISSUER_LABEL = "the configured OIDC issuer";
  private static final String LEGACY_KEY_REMOVAL_VERSION = "8.11";
  // Keycloak publishes its endpoints under <base>/realms/<realm>/protocol/openid-connect/*.
  private static final Pattern KEYCLOAK_REALM_URL = Pattern.compile("/realms/[^/]+$");

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
    if (!OptimizeApplicationDetector.isOptimizeApplication(application)) {
      // See OptimizeApplicationDetector: without this guard, Optimize's OIDC-only bridge defaults
      // silently overrode the embedded broker's own explicit BASIC-auth configuration, which then
      // genuinely conflicted with the broker's separately-configured initial demo user
      // (camunda/camunda#60184).
      return;
    }

    final boolean cslEnabled =
        !"false".equalsIgnoreCase(env.getProperty(CSL_ENABLED_PROPERTY, "true"));
    // Canonicalize to a literal "true"/"false" so every @ConditionalOnProperty on this flag (CSL
    // config, legacy adapters) and every direct Environment read of it agree on the same outcome,
    // even given a value that is neither, e.g. a typo. Those conditions match the value verbatim,
    // so left unnormalized a typo would leave neither the CSL nor the legacy security stack active.
    // Added first (highest precedence) so it wins over whatever the operator actually set.
    env.getPropertySources()
        .addFirst(
            new MapPropertySource(
                CANONICAL_FLAG_PROPERTY_SOURCE_NAME,
                Map.of(CSL_ENABLED_PROPERTY, Boolean.toString(cslEnabled))));

    if (!cslEnabled) {
      warnCslFlagExplicitlyDisabled();
      return;
    }

    final Map<String, Object> derived = new HashMap<>();
    applyAlwaysOnDefaults(derived);
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

  // Reached only when an operator explicitly sets the flag to false: every other path now
  // defaults CSL to active (camunda/camunda#58483), so an unset property never reaches here.
  private void warnCslFlagExplicitlyDisabled() {
    LOG.warn(
        "Optimize config '{}' is deprecated and set to false, keeping the legacy authentication"
            + " stack active instead of CSL. Support for the flag and for the legacy config keys"
            + " it gates will be removed in {}, together with the legacy stack itself"
            + " (camunda/camunda#58484, camunda/camunda#58485). Unset it once CSL is confirmed"
            + " working to prepare for that removal.",
        CSL_ENABLED_PROPERTY,
        LEGACY_KEY_REMOVAL_VERSION);
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
    // Turns on CSL's server-side sessions, which OptimizeSessionStoreAdapter persists in the
    // web-session index. Applies to both editions: the store has an Elasticsearch and an OpenSearch
    // implementation, so CSL's session repository always finds the SessionStorePort bean it
    // requires. putIfAbsent so an operator can still opt out.
    derived.putIfAbsent(SessionConfiguration.PERSISTENT_ENABLED_PROPERTY, "true");
  }

  // OIDC / Identity (CCSM), from the CAMUNDA_OPTIMIZE_IDENTITY_* env vars, or — when those are
  // unset, as with the official camunda-platform Helm chart's Optimize ConfigMap — from the
  // camunda.identity.* structured config it renders instead (application-ccsm.yaml) plus the
  // CAMUNDA_IDENTITY_CLIENT_SECRET env var it uses for the secret. Skipped when Auth0 is
  // configured (the two modes are mutually exclusive, cloud wins) to avoid a mixed registration.
  //
  // Never bridged into issuer-uri: camunda.identity.issuerBackendUrl. CSL uses that single value
  // for OIDC discovery AND for validating each token's iss claim against the discovery document's
  // issuer field. Keycloak reports the same externally-configured issuer regardless of which URL
  // was dialed to reach it, so pointing issuer-uri at the backend-reachable URL would make
  // discovery fail (issuer mismatch) instead of fixing reachability. It feeds the back-channel
  // endpoints, which carry no issuer check — see deriveOidcEndpoints.
  //
  // Also deliberately NOT bridged: camunda.identity.clientSecret, the config-file secret key that
  // application-ccsm.yaml also declares — the official Helm chart never renders it, only the
  // CAMUNDA_IDENTITY_CLIENT_SECRET env var, which is the only secret-carrying key bridged here.
  private void bridgeIdentityOidc(
      final ConfigurableEnvironment env, final Map<String, Object> derived) {
    if (isAuth0Configured(env)) {
      return;
    }
    mapIssuerUri(env, derived, LEGACY_ISSUER_ENV_VAR);
    mapIfPresent(env, derived, "CAMUNDA_OPTIMIZE_IDENTITY_CLIENTID", OIDC_PREFIX + "client-id");
    mapIfPresent(
        env, derived, "CAMUNDA_OPTIMIZE_IDENTITY_CLIENTSECRET", OIDC_PREFIX + "client-secret");
    // Only when that env var is absent: application-ccsm.yaml mirrors it into
    // camunda.identity.issuer for every docker-compose CCSM install, so feeding both would
    // double-warn for a value the operator only set once.
    if (isBlank(env.getProperty(LEGACY_ISSUER_ENV_VAR))) {
      mapIssuerUri(env, derived, IDENTITY_ISSUER);
    }
    deriveOidcEndpoints(env, derived);

    // The Helm chart's Optimize ConfigMap can legitimately render clientId/CAMUNDA_IDENTITY_
    // CLIENT_SECRET while leaving camunda.identity.issuer blank. Bridging a client-id/secret with
    // no issuer-uri (and no authorization-uri/token-uri/jwk-set-uri) makes CSL's
    // ScopedClientRegistrationFactory throw IllegalStateException at startup, so this bridge is
    // all-or-nothing on the issuer-uri: withhold both credentials rather than trade a login problem
    // for a boot failure. Mirrors bridgeAuth0SaasOrgAndCluster's all-or-nothing precedent below.
    if (isBlank(effectiveOidcProperty(env, derived, "issuer-uri"))
        && !hasOidcEndpoints(env, derived)) {
      final String clientId = env.getProperty("camunda.identity.clientId");
      final String clientSecret = env.getProperty("CAMUNDA_IDENTITY_CLIENT_SECRET");
      if (!isBlank(clientId) || !isBlank(clientSecret)) {
        LOG.warn(
            "Optimize config 'camunda.identity.clientId'/'CAMUNDA_IDENTITY_CLIENT_SECRET' cannot"
                + " be bridged without an OIDC issuer: neither 'camunda.identity.issuer' nor"
                + " 'camunda.security.authentication.oidc.issuer-uri' resolves to a value."
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

  // Spells out the endpoints so CSL builds the registration without OIDC discovery: CSL
  // would run that from inside the pod at startup, against a browser-facing issuer. Each
  // endpoint gets the URL its caller can reach.
  //
  // The paths follow Keycloak's realm layout, so they are only derived for a Keycloak
  // provider or for endpoints nothing can dial. Every other provider keeps issuer-uri
  // and lets CSL discover its real endpoints.
  private void deriveOidcEndpoints(
      final ConfigurableEnvironment env, final Map<String, Object> derived) {
    if (hasExplicitOidcEndpoints(env)) {
      // The operator owns the endpoints, including any this would otherwise add.
      return;
    }
    if (!isBlank(effectiveOidcProperty(env, derived, "issuer-uri"))) {
      // CSL discovers the endpoints from the issuer.
      return;
    }
    final String browserIssuer = legacyIssuer(env);
    final String backendUrl = legacyIssuerBackendUrl(env);
    if (browserIssuer == null) {
      // No issuer, so no login dials the front channel and one URL serves both.
      if (backendUrl != null) {
        putDerivedEndpoints(env, derived, backendUrl, backendUrl);
        warnEndpointsDerived(backendUrlLabel(), backendUrlLabel());
        LOG.info(
            "Optimize has no browser-facing OIDC issuer, so its authorization endpoint points at an"
                + " internal URL that browser login cannot reach: set '{}' if this deployment"
                + " serves the Optimize UI.",
            IDENTITY_ISSUER);
      }
      return;
    }
    final String backChannel = keycloakBackChannel(env);
    if (backChannel != null) {
      putDerivedEndpoints(env, derived, browserIssuer, backChannel);
      warnEndpointsDerived(ISSUER_LABEL, backendUrlLabel());
      return;
    }
    if (backendUrl != null && !backendUrl.equals(browserIssuer)) {
      // A distinct back channel whose endpoint paths are unknown. Substituting the issuer would
      // point Optimize at a host it may not reach, so CSL keeps the issuer and discovers instead.
      LOG.info(
          "Optimize left '{}' unused: its OIDC endpoint paths are only known for Keycloak, so CSL"
              + " resolves the endpoints from the issuer by discovery. Set the"
              + " 'camunda.security.authentication.oidc.authorization-uri', 'token-uri',"
              + " 'jwk-set-uri' and 'end-session-endpoint-uri' explicitly to have"
              + " Optimize reach the provider on that URL.",
          IDENTITY_ISSUER_BACKEND_URL);
      return;
    }
    if (!isKeycloak(env, browserIssuer)) {
      return;
    }
    putDerivedEndpoints(env, derived, browserIssuer, browserIssuer);
    warnEndpointsDerived(ISSUER_LABEL, ISSUER_LABEL);
  }

  private void putDerivedEndpoints(
      final ConfigurableEnvironment env,
      final Map<String, Object> derived,
      final String front,
      final String back) {
    // Front channel: the browser is redirected here, so it must be externally reachable.
    derived.putIfAbsent(OIDC_PREFIX + "authorization-uri", front + "/protocol/openid-connect/auth");
    // RP-initiated logout has no endpoint unless it is set explicitly.
    derived.putIfAbsent(
        OIDC_PREFIX + "end-session-endpoint-uri", front + "/protocol/openid-connect/logout");
    // Back channel: Optimize calls these itself.
    derived.putIfAbsent(OIDC_PREFIX + "token-uri", back + "/protocol/openid-connect/token");
    // Deliberately no 'user-info-uri'. CSL builds the registration without a userNameAttributeName,
    // which Spring's DefaultOAuth2UserService requires as soon as the UserInfo endpoint is known,
    // so deriving it fails every login with 'missing_user_name_attribute'. Nothing consumes it:
    // CSL resolves its claims from the ID token, and UserInfo augmentation is off by default and
    // keys on an issuer-uri this bridge deliberately never derives.
    // The public API's own JWK set URI wins: it belongs to the IdP that signs the API tokens, which
    // need not be the Identity instance behind issuerBackendUrl.
    final String publicApiJwks =
        env.getProperty("SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI");
    derived.putIfAbsent(
        OIDC_PREFIX + "jwk-set-uri",
        isBlank(publicApiJwks) ? back + "/protocol/openid-connect/certs" : publicApiJwks);
  }

  private void warnEndpointsDerived(final String frontSource, final String backSource) {
    LOG.warn(
        "Optimize derived the CSL OIDC endpoints, assuming Keycloak's realm paths (configure them"
            + " explicitly for another provider): the browser-facing ones from {}, the back-channel"
            + " ones from {}. No 'camunda.security.authentication.oidc.issuer-uri' is set, so CSL"
            + " runs no OIDC discovery at startup and registers no issuer validator: tokens are"
            + " accepted on their signature, audience and expiry alone.",
        frontSource,
        backSource);
  }

  private static String backendUrlLabel() {
    return "'" + IDENTITY_ISSUER_BACKEND_URL + "'";
  }

  // Null when unset, so callers can fall back to the other URL. Templated
  // values carry padding and trailing slashes.
  private static String realmBase(final String url) {
    return isBlank(url) ? null : url.trim().replaceAll("/+$", "");
  }

  // Both spellings: application-ccsm.yaml mirrors the env var into camunda.identity.issuer, but
  // only under the ccsm profile.
  private static String legacyIssuer(final ConfigurableEnvironment env) {
    return realmBase(firstNonBlankProperty(env, LEGACY_ISSUER_ENV_VAR, IDENTITY_ISSUER));
  }

  private static String legacyIssuerBackendUrl(final ConfigurableEnvironment env) {
    return realmBase(
        firstNonBlankProperty(env, LEGACY_ISSUER_BACKEND_URL_ENV_VAR, IDENTITY_ISSUER_BACKEND_URL));
  }

  // The back-channel URL only when it is a distinct, usable host: a value equal to the issuer
  // splits nothing, so the registration keeps its issuer-uri and CSL resolves the endpoints by
  // discovery as it always has. Only a differing URL says the pod reaches the provider elsewhere.
  private static String keycloakBackChannel(final ConfigurableEnvironment env) {
    final String backChannel = legacyIssuerBackendUrl(env);
    if (backChannel == null || backChannel.equals(legacyIssuer(env))) {
      return null;
    }
    return isKeycloak(env, backChannel) ? backChannel : null;
  }

  // camunda.identity.type names the provider, and the official Helm chart puts it in every app's
  // environment (CAMUNDA_IDENTITY_TYPE, from the shared identity-env-vars ConfigMap). Where it is
  // absent, as in the docker-compose distributions, a realm URL identifies Keycloak instead.
  private static boolean isKeycloak(final ConfigurableEnvironment env, final String backChannel) {
    final String type = firstNonBlankProperty(env, IDENTITY_TYPE_ENV_VAR, IDENTITY_TYPE);
    return type != null
        ? KEYCLOAK_IDENTITY_TYPE.equalsIgnoreCase(type.trim())
        : KEYCLOAK_REALM_URL.matcher(backChannel).find();
  }

  private static String firstNonBlankProperty(
      final ConfigurableEnvironment env, final String preferredKey, final String fallbackKey) {
    final String preferred = env.getProperty(preferredKey);
    if (!isBlank(preferred)) {
      return preferred;
    }
    final String fallback = env.getProperty(fallbackKey);
    return isBlank(fallback) ? null : fallback;
  }

  // Bridges a legacy issuer source into issuer-uri, which makes CSL discover the endpoints by
  // dialing that issuer from inside the pod. Only when neither explicit endpoints nor a Keycloak
  // back channel are configured, because deriveOidcEndpoints covers those. The key is deprecated
  // either way, so each branch names the replacement that suits it.
  private void mapIssuerUri(
      final ConfigurableEnvironment env,
      final Map<String, Object> derived,
      final String legacyKey) {
    final String value = env.getProperty(legacyKey);
    if (isBlank(value)) {
      return;
    }
    if (hasExplicitOidcEndpoints(env) || keycloakBackChannel(env) != null) {
      LOG.warn(
          "Optimize config '{}' is deprecated; migrate to the explicit"
              + " 'camunda.security.authentication.oidc.authorization-uri', 'token-uri',"
              + " 'jwk-set-uri' and 'end-session-endpoint-uri' — the last one is"
              + " needed for RP-initiated logout. Prefer those over"
              + " 'camunda.security.authentication.oidc.issuer-uri', which makes CSL dial this URL"
              + " for OIDC discovery at startup. Support for the legacy key will be removed in {}.",
          legacyKey,
          LEGACY_KEY_REMOVAL_VERSION);
      return;
    }
    warnDeprecated(legacyKey, OIDC_PREFIX + "issuer-uri");
    derived.putIfAbsent(OIDC_PREFIX + "issuer-uri", value);
  }

  // All three are required: CSL accepts an issuer-uri or the complete trio, so withholding the
  // derived issuer-uri from a partial set would turn a working startup into a registration failure.
  private static boolean hasExplicitOidcEndpoints(final ConfigurableEnvironment env) {
    return !isBlank(env.getProperty(OIDC_PREFIX + "authorization-uri"))
        && !isBlank(env.getProperty(OIDC_PREFIX + "jwk-set-uri"))
        && !isBlank(env.getProperty(OIDC_PREFIX + "token-uri"));
  }

  // As hasExplicitOidcEndpoints, but also counting endpoints derived in this pass: a registration
  // cannot be built without a client id, so the guard below must not withhold the credentials.
  private static boolean hasOidcEndpoints(
      final ConfigurableEnvironment env, final Map<String, Object> derived) {
    return !isBlank(effectiveOidcProperty(env, derived, "authorization-uri"))
        && !isBlank(effectiveOidcProperty(env, derived, "jwk-set-uri"))
        && !isBlank(effectiveOidcProperty(env, derived, "token-uri"));
  }

  // What CSL will actually see: the derived source is added last, so any explicitly set value wins
  // even when blank.
  private static String effectiveOidcProperty(
      final ConfigurableEnvironment env, final Map<String, Object> derived, final String suffix) {
    final String explicit = env.getProperty(OIDC_PREFIX + suffix);
    if (explicit != null) {
      return explicit;
    }
    final Object derivedValue = derived.get(OIDC_PREFIX + suffix);
    return derivedValue == null ? null : derivedValue.toString();
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
