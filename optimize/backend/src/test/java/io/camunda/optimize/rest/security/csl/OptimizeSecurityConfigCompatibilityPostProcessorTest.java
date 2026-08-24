/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CAMUNDA_OPTIMIZE_DATABASE;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ELASTICSEARCH_DATABASE_PROPERTY;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.OPENSEARCH_DATABASE_PROPERTY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.Main;
import io.camunda.security.api.model.config.SessionConfiguration;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.event.Level;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class OptimizeSecurityConfigCompatibilityPostProcessorTest {

  private static final String OIDC = "camunda.security.authentication.oidc.";
  private static final SpringApplication OPTIMIZE_APPLICATION = new SpringApplication(Main.class);

  @RegisterExtension
  final LogCapturer logs =
      LogCapturer.create()
          .captureForType(OptimizeSecurityConfigCompatibilityPostProcessor.class, Level.INFO);

  private final OptimizeSecurityConfigCompatibilityPostProcessor processor =
      new OptimizeSecurityConfigCompatibilityPostProcessor();

  private static StandardEnvironment environmentWith(final Map<String, Object> legacy) {
    final StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources().addFirst(new MapPropertySource("test-legacy", legacy));
    return env;
  }

  private static Map<String, Object> cslEnabledConfig() {
    final Map<String, Object> legacy = new HashMap<>();
    legacy.put("optimize.security.csl.enabled", "true");
    return legacy;
  }

  @Test
  void shouldDoNothingAndWarnWhenCslExplicitlyDisabled() {
    // given an operator using the escape hatch (CSL now defaults to true, camunda/camunda#58483)
    final Map<String, Object> legacy = new HashMap<>();
    legacy.put("optimize.security.csl.enabled", "false");
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID", "cloud-client");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty("camunda.security.authentication.method")).isNull();
    assertThat(env.getProperty(OIDC + "client-id")).isNull();
    logs.assertContains(
        entry ->
            entry.getLevel() == Level.WARN
                && entry.getMessage().contains("optimize.security.csl.enabled")
                && entry.getMessage().contains("8.11"),
        "expected a deprecation warning naming the flag and the 8.11 removal");
    assertThat(env.getProperty("optimize.security.csl.enabled")).isEqualTo("false");
  }

  @Test
  void shouldBridgeAndNotWarnFlagExplicitlyDisabledWhenValueIsNeitherTrueNorFalse() {
    // given a typo (e.g. "flase") rather than a literal "false": Boolean.parseBoolean would treat
    // it as false and wrongly log "set to false" for a value the operator never set to false
    final Map<String, Object> legacy = new HashMap<>();
    legacy.put("optimize.security.csl.enabled", "flase");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty("camunda.security.authentication.method")).isEqualTo("oidc");
    logs.assertDoesNotContain(
        entry -> entry.getLevel() == Level.WARN && entry.getMessage().contains("set to false"),
        "expected no misleading 'set to false' warning for a value that isn't literally false");
    // canonicalized so the CSL config bean's @ConditionalOnProperty(havingValue = "true") and the
    // legacy adapters' @ConditionalOnProperty(havingValue = "false") agree with this outcome too
    assertThat(env.getProperty("optimize.security.csl.enabled")).isEqualTo("true");
  }

  @Test
  void shouldBridgeByDefaultWhenFlagAbsent() {
    // given the flag is not set at all — CSL is now the default (camunda/camunda#58483)
    final Map<String, Object> legacy = new HashMap<>();

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty("camunda.security.authentication.method")).isEqualTo("oidc");
    logs.assertDoesNotContain(
        entry -> entry.getLevel() == Level.WARN,
        "expected no deprecation warning when the flag is simply left at its default");
  }

  @Test
  void shouldSkipEntirelyWhenApplicationIsNotOptimizesOwn() {
    // given a different SpringApplication booted in the same JVM/classpath (e.g. the embedded
    // TestStandaloneBroker Optimize's own CCSM ITs start for import tests, see
    // camunda/camunda#60184) that never asked for Optimize's OIDC-only bridge defaults
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL", "http://localhost:18080/realm");

    final StandardEnvironment env = environmentWith(legacy);
    final SpringApplication notOptimize = new SpringApplication(StandardEnvironment.class);

    processor.postProcessEnvironment(env, notOptimize);

    assertThat(env.getProperty("camunda.security.authentication.method")).isNull();
    assertThat(env.getProperty(OIDC + "issuer-uri")).isNull();
    // left exactly as the caller set it: the guard returns before canonicalization runs, so a
    // foreign application never even sees this processor's usual normalization of the flag
    assertThat(env.getProperty("optimize.security.csl.enabled")).isEqualTo("true");
    logs.assertDoesNotContain(
        entry -> true, "expected no deprecation warnings for an application that isn't Optimize");
  }

  @Test
  void shouldSkipEntirelyWhenApplicationIsNull() {
    // given no SpringApplication at all (defensive: postProcessEnvironment's contract allows null)
    final StandardEnvironment env = environmentWith(cslEnabledConfig());

    processor.postProcessEnvironment(env, null);

    assertThat(env.getProperty("camunda.security.authentication.method")).isNull();
  }

  @Test
  void shouldBridgeCcsmIdentityConfigAndLogDeprecation() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL", "http://localhost:18080/realm");
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_CLIENTID", "optimize");
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_CLIENTSECRET", "identity-secret");
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_AUDIENCE", "optimize-api");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty("camunda.security.authentication.method")).isEqualTo("oidc");
    assertThat(env.getProperty("camunda.security.authentication.catch-all-unhandled-paths-enabled"))
        .isEqualTo("false");
    assertThat(env.getProperty(OIDC + "issuer-uri")).isEqualTo("http://localhost:18080/realm");
    assertThat(env.getProperty(OIDC + "client-id")).isEqualTo("optimize");
    assertThat(env.getProperty(OIDC + "client-secret")).isEqualTo("identity-secret");
    assertThat(env.getProperty(OIDC + "audiences")).isEqualTo("optimize-api");
    assertThat(env.getProperty(OIDC + "redirect-uri"))
        .isEqualTo("{baseUrl}/api/authentication/callback");
    assertThat(env.getProperty("camunda.security.saas.organization-id")).isNull();
    assertThat(env.getProperty("camunda.security.saas.cluster-id")).isNull();
    assertThat(env.getProperty("contextPath")).isNull();

    logs.assertContains(
        entry ->
            entry.getMessage().contains("CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL")
                && entry.getMessage().contains(OIDC + "issuer-uri"),
        "expected deprecation warning naming the legacy key and its replacement");
    // the secret value itself must never be logged
    logs.assertDoesNotContain(
        entry -> entry.getMessage().contains("identity-secret"),
        "client secret value must never be logged");
  }

  @Test
  void shouldBridgeHelmChartIdentityConfigAndLogDeprecation() {
    // The official camunda-platform Helm chart never sets CAMUNDA_OPTIMIZE_IDENTITY_*; it renders
    // camunda.identity.* as structured YAML (application-ccsm.yaml) plus
    // CAMUNDA_IDENTITY_CLIENT_SECRET
    // for the secret instead.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.issuer", "http://localhost:18080/auth/realms/camunda-platform");
    legacy.put("camunda.identity.clientId", "optimize");
    legacy.put("CAMUNDA_IDENTITY_CLIENT_SECRET", "helm-secret");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "issuer-uri"))
        .isEqualTo("http://localhost:18080/auth/realms/camunda-platform");
    assertThat(env.getProperty(OIDC + "client-id")).isEqualTo("optimize");
    assertThat(env.getProperty(OIDC + "client-secret")).isEqualTo("helm-secret");

    logs.assertContains(
        entry ->
            entry.getMessage().contains("camunda.identity.issuer")
                && entry.getMessage().contains(OIDC + "issuer-uri"),
        "expected deprecation warning naming the Helm-rendered issuer key");
    logs.assertContains(
        entry ->
            entry.getMessage().contains("camunda.identity.clientId")
                && entry.getMessage().contains(OIDC + "client-id"),
        "expected deprecation warning naming the Helm-rendered clientId key");
    logs.assertContains(
        entry ->
            entry.getMessage().contains("CAMUNDA_IDENTITY_CLIENT_SECRET")
                && entry.getMessage().contains(OIDC + "client-secret"),
        "expected deprecation warning naming the Helm client-secret env var");
    // the secret value itself must never be logged
    logs.assertDoesNotContain(
        entry -> entry.getMessage().contains("helm-secret"),
        "client secret value must never be logged");
  }

  @Test
  void shouldDeriveEndpointsFromIssuerBackendUrlWhenNoIssuerIsConfigured() {
    // A deployment that only protects the public API: a JWK set URI, no browser-facing issuer. CSL
    // needs an issuer-uri or all three endpoints, so without this it can build no registration at
    // all and Optimize fails to start (camunda/camunda#60617).
    final String backend = "http://identity.svc:18080/auth/realms/camunda-platform";
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.issuer", "");
    legacy.put("camunda.identity.issuerBackendUrl", backend);
    legacy.put("camunda.identity.clientId", "optimize");
    legacy.put("CAMUNDA_IDENTITY_CLIENT_SECRET", "benchmark-secret");
    legacy.put(
        "SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI",
        "https://public-api-idp.example.com/keys");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "issuer-uri")).isNull();
    assertThat(env.getProperty(OIDC + "authorization-uri"))
        .isEqualTo(backend + "/protocol/openid-connect/auth");
    assertThat(env.getProperty(OIDC + "token-uri"))
        .isEqualTo(backend + "/protocol/openid-connect/token");
    // The public API's own JWK set URI wins over the derived one: it may belong to a different IdP.
    assertThat(env.getProperty(OIDC + "jwk-set-uri"))
        .isEqualTo("https://public-api-idp.example.com/keys");
    // The credentials must come with them: a registration cannot be built without a client id, so
    // the guard that withholds them when there is no issuer has to count the derived endpoints.
    assertThat(env.getProperty(OIDC + "client-id")).isEqualTo("optimize");
    assertThat(env.getProperty(OIDC + "client-secret")).isEqualTo("benchmark-secret");
  }

  @Test
  void shouldDeriveTheJwkSetUriFromTheBackendUrlWhenThePublicApiConfiguresNone() {
    final String backend = "http://identity.svc:18080/auth/realms/camunda-platform";
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.issuer", "");
    legacy.put("camunda.identity.issuerBackendUrl", backend);

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "jwk-set-uri"))
        .isEqualTo(backend + "/protocol/openid-connect/certs");
  }

  @Test
  void shouldKeepExplicitlyConfiguredEndpointsAndDeriveOnlyTheMissingOnes() {
    // A host that pointed one endpoint at its own IdP keeps it: the derived values are added as the
    // lowest-precedence source, so they only fill the gaps.
    final String backend = "http://identity.svc:18080/auth/realms/camunda-platform";
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.issuer", "");
    legacy.put("camunda.identity.issuerBackendUrl", backend);
    legacy.put("camunda.identity.clientId", "optimize");
    legacy.put(OIDC + "authorization-uri", "https://idp.example.com/authorize");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "authorization-uri"))
        .isEqualTo("https://idp.example.com/authorize");
    assertThat(env.getProperty(OIDC + "token-uri"))
        .isEqualTo(backend + "/protocol/openid-connect/token");
    assertThat(env.getProperty(OIDC + "jwk-set-uri"))
        .isEqualTo(backend + "/protocol/openid-connect/certs");
    assertThat(env.getProperty(OIDC + "client-id")).isEqualTo("optimize");
  }

  @Test
  void shouldTellOperatorsWhichKeysToSetWhenEndpointsAreDerived() {
    // The only signal that Optimize started with endpoints browser login cannot use.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.issuer", "");
    legacy.put(
        "camunda.identity.issuerBackendUrl",
        "http://identity.svc:18080/auth/realms/camunda-platform");

    processor.postProcessEnvironment(environmentWith(legacy), OPTIMIZE_APPLICATION);

    logs.assertContains("camunda.identity.issuerBackendUrl");
    logs.assertContains("browser login cannot reach");
    logs.assertContains("camunda.identity.issuer'");
  }

  @Test
  void shouldNormaliseAWhitespacePaddedIssuerBackendUrl() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.issuer", "");
    legacy.put(
        "camunda.identity.issuerBackendUrl",
        "  http://identity.svc:18080/auth/realms/camunda-platform//  ");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "token-uri"))
        .isEqualTo(
            "http://identity.svc:18080/auth/realms/camunda-platform/protocol/openid-connect/token");
  }

  @Test
  void shouldDeriveEndpointsWhenTheCslIssuerUriIsExplicitlyBlank() {
    // The explicitly blank value outranks the bridged issuer-uri, so CSL needs the endpoints.
    final String backend = "http://identity.svc:18080/auth/realms/camunda-platform";
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.issuer", "https://weblogin.example.com/realm");
    legacy.put("camunda.identity.issuerBackendUrl", backend);
    legacy.put("camunda.identity.clientId", "optimize");
    legacy.put("CAMUNDA_IDENTITY_CLIENT_SECRET", "secret");
    legacy.put(OIDC + "issuer-uri", "");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "authorization-uri"))
        .isEqualTo("https://weblogin.example.com/realm/protocol/openid-connect/auth");
    assertThat(env.getProperty(OIDC + "token-uri"))
        .isEqualTo(backend + "/protocol/openid-connect/token");
    assertThat(env.getProperty(OIDC + "jwk-set-uri"))
        .isEqualTo(backend + "/protocol/openid-connect/certs");
    assertThat(env.getProperty(OIDC + "client-id")).isEqualTo("optimize");
    assertThat(env.getProperty(OIDC + "client-secret")).isEqualTo("secret");
  }

  @Test
  void shouldDeriveEndpointsFromTheIssuerAloneWhenNoBackendUrlIsConfigured() {
    // The only host configured, so it serves both channels.
    final String issuer = "https://keycloak.example.com/auth/realms/camunda-platform";
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.issuer", issuer);
    legacy.put("camunda.identity.clientId", "optimize");
    legacy.put("CAMUNDA_IDENTITY_CLIENT_SECRET", "secret");
    legacy.put(OIDC + "issuer-uri", "");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "authorization-uri"))
        .isEqualTo(issuer + "/protocol/openid-connect/auth");
    assertThat(env.getProperty(OIDC + "token-uri"))
        .isEqualTo(issuer + "/protocol/openid-connect/token");
    assertThat(env.getProperty(OIDC + "client-id")).isEqualTo("optimize");
    assertThat(env.getProperty(OIDC + "client-secret")).isEqualTo("secret");
    // The log must not credit a key the operator never set.
    logs.assertDoesNotContain("camunda.identity.issuerBackendUrl");
  }

  @Test
  void shouldWithholdCredentialsWhenAnEndpointIsExplicitlyBlank() {
    // An explicitly set blank value outranks the derived one, so the trio CSL sees is incomplete.
    // Bridging the credentials into it would swap the missing-endpoint failure for a missing-client
    // one rather than fixing anything.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.issuer", "");
    legacy.put(
        "camunda.identity.issuerBackendUrl",
        "http://identity.svc:18080/auth/realms/camunda-platform");
    legacy.put("camunda.identity.clientId", "optimize");
    legacy.put("CAMUNDA_IDENTITY_CLIENT_SECRET", "secret");
    legacy.put(OIDC + "token-uri", "");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "client-id")).isNull();
    assertThat(env.getProperty(OIDC + "client-secret")).isNull();
  }

  @Test
  void shouldPointTheDeprecationGuidanceAtTheEndpointsWhenTheChannelsAreSplit() {
    // Migrating to issuer-uri would restore the startup dial against the browser-facing URL, so the
    // warning has to name the endpoints instead.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.issuer", "https://camunda.example.com/auth/realms/camunda");
    legacy.put(
        "camunda.identity.issuerBackendUrl", "http://keycloak:8080/auth/realms/camunda-platform");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    logs.assertContains(
        entry ->
            entry.getMessage().contains("camunda.identity.issuer")
                && entry.getMessage().contains(OIDC + "authorization-uri"),
        "expected migration guidance naming the explicit endpoints");
  }

  @Test
  void shouldDeriveNothingForANonKeycloakProviderWhenTheCslIssuerUriIsBlanked() {
    // A blank issuer-uri leaves CSL no issuer, but the issuer is still no basis for Keycloak paths
    // on another provider. Withholding the credentials keeps a broken guess out of the
    // registration.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.type", "MICROSOFT");
    legacy.put("camunda.identity.issuer", "https://login.microsoftonline.com/tenant-id/v2.0");
    legacy.put("camunda.identity.clientId", "optimize");
    legacy.put("CAMUNDA_IDENTITY_CLIENT_SECRET", "secret");
    legacy.put(OIDC + "issuer-uri", "");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "authorization-uri")).isNull();
    assertThat(env.getProperty(OIDC + "token-uri")).isNull();
    assertThat(env.getProperty(OIDC + "jwk-set-uri")).isNull();
    assertThat(env.getProperty(OIDC + "user-info-uri")).isNull();
    assertThat(env.getProperty(OIDC + "client-id")).isNull();
    assertThat(env.getProperty(OIDC + "client-secret")).isNull();
  }

  @Test
  void shouldLeaveANonKeycloakBackendUrlUnusedRatherThanSubstituteTheIssuer() {
    // A Keycloak-shaped issuer with a distinct back channel whose paths are unknown. Deriving the
    // back channel from the issuer would send Optimize at a host it may not reach and ignore the
    // configured URL, so nothing is derived and the log names the setting it left unused.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.issuer", "https://camunda.example.com/auth/realms/camunda");
    legacy.put("camunda.identity.issuerBackendUrl", "https://idp.internal.svc/oauth2/default");
    legacy.put("camunda.identity.clientId", "optimize");
    legacy.put("CAMUNDA_IDENTITY_CLIENT_SECRET", "secret");
    legacy.put(OIDC + "issuer-uri", "");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "authorization-uri")).isNull();
    assertThat(env.getProperty(OIDC + "token-uri")).isNull();
    assertThat(env.getProperty(OIDC + "jwk-set-uri")).isNull();
    assertThat(env.getProperty(OIDC + "user-info-uri")).isNull();
    logs.assertContains("left 'camunda.identity.issuerBackendUrl' unused");
  }

  @ParameterizedTest
  @ValueSource(strings = {"keycloak", "KeyCloak", " KEYCLOAK "})
  void shouldTreatTheIdentityTypeCaseAndPaddingInsensitively(final String type) {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.type", type);
    legacy.put("camunda.identity.issuer", "https://idp.example.com/tenant/v2.0");
    legacy.put("camunda.identity.issuerBackendUrl", "http://idp.internal.svc/tenant/v2.0");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    // Neither URL has a realm path, so only the configured type can select the split.
    assertThat(env.getProperty(OIDC + "issuer-uri")).isNull();
    assertThat(env.getProperty(OIDC + "token-uri"))
        .isEqualTo("http://idp.internal.svc/tenant/v2.0/protocol/openid-connect/token");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void shouldFallBackToTheUrlShapeWhenTheIdentityTypeIsBlank(final String type) {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.type", type);
    legacy.put("camunda.identity.issuer", "https://camunda.example.com/auth/realms/camunda");
    legacy.put(
        "camunda.identity.issuerBackendUrl", "http://keycloak:8080/auth/realms/camunda-platform");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "issuer-uri")).isNull();
    assertThat(env.getProperty(OIDC + "token-uri"))
        .isEqualTo(
            "http://keycloak:8080/auth/realms/camunda-platform/protocol/openid-connect/token");
  }

  @Test
  void shouldKeepDiscoveryWhenTheBackendUrlEqualsTheIssuer() {
    // The shape of Optimize's own local and smoke-test setups: both settings name the same host, so
    // there is no reachability problem to solve and nothing to split. Keeping issuer-uri leaves the
    // registration with a discovered issuer, which CSL needs for issuer validation and for the
    // UserInfo claims it merges.
    final String url = "http://localhost:18080/auth/realms/camunda-platform";
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL", url);
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_BACKEND_URL", url);
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_CLIENTID", "optimize");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "issuer-uri")).isEqualTo(url);
    assertThat(env.getProperty(OIDC + "authorization-uri")).isNull();
    assertThat(env.getProperty(OIDC + "token-uri")).isNull();
    assertThat(env.getProperty(OIDC + "user-info-uri")).isNull();
    assertThat(env.getProperty(OIDC + "end-session-endpoint-uri")).isNull();
  }

  @Test
  void shouldKeepDiscoveryWhenTheConfiguredIdentityTypeIsNotKeycloak() {
    // camunda.identity.type names the provider outright, so it decides even when the URL happens to
    // look like a Keycloak realm.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.type", "MICROSOFT");
    legacy.put("camunda.identity.issuer", "https://idp.example.com/auth/realms/lookalike");
    // A distinct host, so only the configured type can rule Keycloak out: the realm path would
    // otherwise satisfy the URL check and split the channels.
    legacy.put(
        "camunda.identity.issuerBackendUrl", "https://idp.internal.svc:8443/auth/realms/lookalike");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "issuer-uri"))
        .isEqualTo("https://idp.example.com/auth/realms/lookalike");
    assertThat(env.getProperty(OIDC + "authorization-uri")).isNull();
    assertThat(env.getProperty(OIDC + "end-session-endpoint-uri")).isNull();
  }

  @Test
  void shouldSplitFrontAndBackChannelWhenTheIdentityTypeIsKeycloak() {
    // The Helm chart's CAMUNDA_IDENTITY_TYPE, which the Optimize deployment imports from the shared
    // identity-env-vars ConfigMap. It decides even for a realm path the URL check would not match.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_IDENTITY_TYPE", "KEYCLOAK");
    legacy.put("camunda.identity.issuer", "https://camunda.example.com/auth/realms/camunda/extra");
    legacy.put("camunda.identity.issuerBackendUrl", "http://keycloak:8080/auth/realms/kc/extra");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "issuer-uri")).isNull();
    assertThat(env.getProperty(OIDC + "authorization-uri"))
        .isEqualTo(
            "https://camunda.example.com/auth/realms/camunda/extra/protocol/openid-connect/auth");
    assertThat(env.getProperty(OIDC + "token-uri"))
        .isEqualTo("http://keycloak:8080/auth/realms/kc/extra/protocol/openid-connect/token");
  }

  @Test
  void shouldKeepDiscoveryForANonKeycloakIssuerBackendUrl() {
    // Only Keycloak publishes the paths this bridge would guess, so a provider with a different
    // layout keeps issuer-uri and lets CSL discover its real endpoints.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.issuer", "https://login.microsoftonline.com/tenant-id/v2.0");
    // A distinct host, so the URL check is what has to reject it: an equal URL would short-circuit
    // before the provider is ever inferred.
    legacy.put(
        "camunda.identity.issuerBackendUrl", "https://login.internal.example/tenant-id/v2.0");
    legacy.put("camunda.identity.clientId", "optimize");
    legacy.put("CAMUNDA_IDENTITY_CLIENT_SECRET", "secret");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "issuer-uri"))
        .isEqualTo("https://login.microsoftonline.com/tenant-id/v2.0");
    assertThat(env.getProperty(OIDC + "authorization-uri")).isNull();
    assertThat(env.getProperty(OIDC + "token-uri")).isNull();
    assertThat(env.getProperty(OIDC + "jwk-set-uri")).isNull();
    assertThat(env.getProperty(OIDC + "end-session-endpoint-uri")).isNull();
    assertThat(env.getProperty(OIDC + "client-id")).isEqualTo("optimize");
  }

  @Test
  void shouldDeriveNothingWhenTheHostConfiguredTheCompleteEndpointTrio() {
    // CSL enables IdP logout by default, so a guessed end-session endpoint would send a provider
    // that never published one to a URL it does not serve.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.issuer", "https://idp.example.com/oauth2/default");
    legacy.put("camunda.identity.issuerBackendUrl", "https://idp.example.com/oauth2/default");
    legacy.put(OIDC + "authorization-uri", "https://idp.example.com/oauth2/v1/authorize");
    legacy.put(OIDC + "token-uri", "https://idp.example.com/oauth2/v1/token");
    legacy.put(OIDC + "jwk-set-uri", "https://idp.example.com/oauth2/v1/keys");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "end-session-endpoint-uri")).isNull();
    assertThat(env.getProperty(OIDC + "authorization-uri"))
        .isEqualTo("https://idp.example.com/oauth2/v1/authorize");
    assertThat(env.getProperty(OIDC + "issuer-uri")).isNull();
  }

  @Test
  void shouldSplitFrontAndBackChannelForTheLegacyEnvVarSpelling() {
    // The env vars alone, without the camunda.identity.* mirroring the ccsm profile adds. localhost
    // is the host-published Keycloak port: it resolves to the Optimize container itself.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put(
        "CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL",
        "http://localhost:18080/auth/realms/camunda-platform");
    legacy.put(
        "CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_BACKEND_URL",
        "http://keycloak:8080/auth/realms/camunda-platform");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "issuer-uri")).isNull();
    assertThat(env.getProperty(OIDC + "authorization-uri"))
        .isEqualTo(
            "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/auth");
    assertThat(env.getProperty(OIDC + "token-uri"))
        .isEqualTo(
            "http://keycloak:8080/auth/realms/camunda-platform/protocol/openid-connect/token");
    assertThat(env.getProperty(OIDC + "jwk-set-uri"))
        .isEqualTo(
            "http://keycloak:8080/auth/realms/camunda-platform/protocol/openid-connect/certs");
  }

  @Test
  void shouldSplitFrontAndBackChannelWhenBothIssuerUrlsAreConfigured() {
    // The docker-compose CCSM shape: the issuer is reachable from a browser only, so each endpoint
    // takes the URL its caller can reach.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put(
        "camunda.identity.issuer", "https://public.example.com/auth/realms/camunda-platform");
    legacy.put(
        "camunda.identity.issuerBackendUrl",
        "http://identity.svc:18080/auth/realms/camunda-platform");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    final String browser = "https://public.example.com/auth/realms/camunda-platform";
    final String backend = "http://identity.svc:18080/auth/realms/camunda-platform";
    // No issuer-uri, so CSL builds the registration without OIDC discovery.
    assertThat(env.getProperty(OIDC + "issuer-uri")).isNull();
    assertThat(env.getProperty(OIDC + "authorization-uri"))
        .isEqualTo(browser + "/protocol/openid-connect/auth");
    assertThat(env.getProperty(OIDC + "end-session-endpoint-uri"))
        .isEqualTo(browser + "/protocol/openid-connect/logout");
    assertThat(env.getProperty(OIDC + "token-uri"))
        .isEqualTo(backend + "/protocol/openid-connect/token");
    assertThat(env.getProperty(OIDC + "jwk-set-uri"))
        .isEqualTo(backend + "/protocol/openid-connect/certs");
    // Never derive a UserInfo endpoint: CSL builds the registration without a
    // userNameAttributeName, so as soon as Spring knows the endpoint every login fails with
    // 'missing_user_name_attribute'. Deriving it here regressed SM 8.10 Optimize login (#60834).
    assertThat(env.getProperty(OIDC + "user-info-uri")).isNull();
  }

  @Test
  void shouldNotDoubleTheSeparatorWhenIssuerBackendUrlEndsWithSlash() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.issuer", "");
    legacy.put(
        "camunda.identity.issuerBackendUrl",
        "http://identity.svc:18080/auth/realms/camunda-platform/");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "token-uri"))
        .isEqualTo(
            "http://identity.svc:18080/auth/realms/camunda-platform/protocol/openid-connect/token");
  }

  @Test
  void shouldNotDeriveIssuerUriWhenTheHostConfiguredTheOidcEndpoints() {
    // The camunda-platform Helm chart renders the endpoints directly for a Keycloak setup, keeping
    // the back-channel on the in-cluster URL. Deriving an issuer-uri from camunda.identity.issuer
    // would make CSL discard them and resolve the endpoints over the browser-facing issuer instead,
    // which fails wherever the pod cannot validate that certificate.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put(
        "camunda.identity.issuer", "https://public.example.com/auth/realms/camunda-platform");
    legacy.put("camunda.identity.clientId", "optimize");
    legacy.put("CAMUNDA_IDENTITY_CLIENT_SECRET", "helm-secret");
    legacy.put(
        OIDC + "authorization-uri",
        "https://public.example.com/auth/realms/camunda-platform/protocol/openid-connect/auth");
    legacy.put(
        OIDC + "jwk-set-uri",
        "http://keycloak:80/auth/realms/camunda-platform/protocol/openid-connect/certs");
    legacy.put(
        OIDC + "token-uri",
        "http://keycloak:80/auth/realms/camunda-platform/protocol/openid-connect/token");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "issuer-uri")).isNull();
    assertThat(env.getProperty(OIDC + "jwk-set-uri"))
        .isEqualTo("http://keycloak:80/auth/realms/camunda-platform/protocol/openid-connect/certs");
  }

  @Test
  void shouldStillBridgeCredentialsWhenOnlyTheOidcEndpointsAreConfigured() {
    // Without an issuer-uri the bridge withholds client-id and client-secret, because CSL cannot
    // build a registration from credentials alone. The complete endpoint trio is the other way to
    // give it one, so it must satisfy that guard rather than leave the client unconfigured.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.clientId", "optimize");
    legacy.put("CAMUNDA_IDENTITY_CLIENT_SECRET", "helm-secret");
    legacy.put(
        OIDC + "authorization-uri",
        "https://public.example.com/auth/realms/camunda-platform/protocol/openid-connect/auth");
    legacy.put(
        OIDC + "jwk-set-uri",
        "http://keycloak:80/auth/realms/camunda-platform/protocol/openid-connect/certs");
    legacy.put(
        OIDC + "token-uri",
        "http://keycloak:80/auth/realms/camunda-platform/protocol/openid-connect/token");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "client-id")).isEqualTo("optimize");
    assertThat(env.getProperty(OIDC + "client-secret")).isEqualTo("helm-secret");
    assertThat(env.getProperty(OIDC + "issuer-uri")).isNull();
  }

  @Test
  void shouldNotDeriveIssuerUriFromTheLegacyEnvVarWhenTheOidcEndpointsAreConfigured() {
    // The docker-compose CCSM shape sets CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL rather than
    // camunda.identity.issuer. It feeds the same issuer-uri, so it has to be gated the same way,
    // while still telling the operator the key is deprecated.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put(
        "CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL",
        "https://public.example.com/auth/realms/camunda-platform");
    legacy.put(
        OIDC + "authorization-uri",
        "https://public.example.com/auth/realms/camunda-platform/protocol/openid-connect/auth");
    legacy.put(
        OIDC + "jwk-set-uri",
        "http://keycloak:80/auth/realms/camunda-platform/protocol/openid-connect/certs");
    legacy.put(
        OIDC + "token-uri",
        "http://keycloak:80/auth/realms/camunda-platform/protocol/openid-connect/token");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "issuer-uri")).isNull();
    // The key stays deprecated, but pointing at issuer-uri would send CSL back to discovery, so the
    // guidance names the endpoints it builds the registration from.
    logs.assertContains(
        entry ->
            entry.getMessage().contains("CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL")
                && entry.getMessage().contains(OIDC + "authorization-uri")
                && entry.getMessage().contains("'token-uri'")
                && entry.getMessage().contains("'jwk-set-uri'"),
        "expected migration guidance naming the explicit endpoints");
  }

  @Test
  void shouldStillDeriveIssuerUriWhenTheOidcEndpointsAreIncomplete() {
    // CSL needs an issuer-uri or all three endpoints. A host that configured only some of them
    // relies on the derived issuer-uri to boot at all, so the derivation must stay.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put(
        "camunda.identity.issuer", "https://public.example.com/auth/realms/camunda-platform");
    legacy.put(
        OIDC + "jwk-set-uri",
        "http://keycloak:80/auth/realms/camunda-platform/protocol/openid-connect/certs");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "issuer-uri"))
        .isEqualTo("https://public.example.com/auth/realms/camunda-platform");
  }

  @Test
  void shouldNotBridgeHelmChartIdentityIssuerUriKeyWhenBlank() {
    // application-ccsm.yaml resolves camunda.identity.issuer to "" (via a Spring placeholder
    // default) whenever CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL is unset, which is the normal state
    // for any deployment that isn't Helm-based CCSM with CSL enabled. A blank value must never be
    // bridged or warned about.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.issuer", "");
    legacy.put("camunda.identity.clientId", "");
    legacy.put("CAMUNDA_IDENTITY_CLIENT_SECRET", "");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "issuer-uri")).isNull();
    assertThat(env.getProperty(OIDC + "client-id")).isNull();
    assertThat(env.getProperty(OIDC + "client-secret")).isNull();
    logs.assertDoesNotContain(
        entry -> entry.getMessage().contains("camunda.identity"),
        "expected no warning for blank Helm-rendered keys");
  }

  @Test
  void shouldPreferLegacyOptimizeIdentityEnvVarsOverHelmChartKeysAndNotDoubleWarn() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL", "http://legacy.example.com/realm");
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_CLIENTID", "legacy-client");
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_CLIENTSECRET", "legacy-secret");
    // Mirrors what application-ccsm.yaml actually does in a real docker-compose CCSM deployment:
    // the same values end up in camunda.identity.* too, purely as an internal resource-file detail,
    // not a second operator choice.
    legacy.put("camunda.identity.issuer", "http://legacy.example.com/realm");
    legacy.put("camunda.identity.clientId", "legacy-client");
    legacy.put("CAMUNDA_IDENTITY_CLIENT_SECRET", "helm-secret");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "issuer-uri")).isEqualTo("http://legacy.example.com/realm");
    assertThat(env.getProperty(OIDC + "client-id")).isEqualTo("legacy-client");
    assertThat(env.getProperty(OIDC + "client-secret")).isEqualTo("legacy-secret");
    // exactly one warning per property, naming the legacy env var — never the mirrored
    // camunda.identity.* key, since its value was never actually used
    logs.assertContains(
        entry ->
            entry.getMessage().contains("CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL")
                && entry.getMessage().contains(OIDC + "issuer-uri"),
        "expected the deprecation warning to name the legacy env var");
    logs.assertDoesNotContain(
        entry ->
            entry.getMessage().contains("camunda.identity.issuer")
                && !entry.getMessage().contains("CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL"),
        "the mirrored camunda.identity.issuer key must not get its own warning when the legacy env var already won");
    // the secret's own warning must not fire either: the legacy secret already won
    logs.assertDoesNotContain(
        entry -> entry.getMessage().contains("CAMUNDA_IDENTITY_CLIENT_SECRET"),
        "the mirrored CAMUNDA_IDENTITY_CLIENT_SECRET key must not get its own warning when the"
            + " legacy secret already won");
  }

  @Test
  void shouldNotBridgeHelmChartClientCredentialsWithoutAnIssuerUri() {
    // The official camunda-platform Helm chart's Optimize ConfigMap can legitimately render
    // clientId/CAMUNDA_IDENTITY_CLIENT_SECRET while leaving camunda.identity.issuer blank (e.g. an
    // OIDC-disabled or misconfigured install). Bridging the client-id/secret without an issuer-uri
    // would let CSL's ScopedClientRegistrationFactory register a provider with no issuer,
    // authorization-uri, token-uri, or jwk-set-uri, throwing IllegalStateException at startup.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.issuer", "");
    legacy.put("camunda.identity.clientId", "optimize");
    legacy.put("CAMUNDA_IDENTITY_CLIENT_SECRET", "helm-secret");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "issuer-uri")).isNull();
    assertThat(env.getProperty(OIDC + "client-id")).isNull();
    assertThat(env.getProperty(OIDC + "client-secret")).isNull();
    logs.assertContains(
        entry -> entry.getMessage().contains("camunda.identity.issuer"),
        "expected a warning naming the missing issuer key");
    // the secret value itself must never be logged
    logs.assertDoesNotContain(
        entry -> entry.getMessage().contains("helm-secret"),
        "client secret value must never be logged");
  }

  @Test
  void shouldTreatAnExplicitlyBlankIssuerUriAsMissingForThePartialConfigGuard() {
    // An operator (or another property source) can set
    // camunda.security.authentication.oidc.issuer-uri to an empty string rather than leaving it
    // absent. The partial-config guard must treat that the same as "no issuer-uri set": otherwise
    // it would let clientId/CAMUNDA_IDENTITY_CLIENT_SECRET bridge through with a blank issuer-uri,
    // reintroducing the CSL startup failure the guard exists to prevent.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.issuer", "");
    legacy.put("camunda.identity.clientId", "optimize");
    legacy.put("CAMUNDA_IDENTITY_CLIENT_SECRET", "helm-secret");
    legacy.put(OIDC + "issuer-uri", "");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "client-id")).isNull();
    assertThat(env.getProperty(OIDC + "client-secret")).isNull();
    logs.assertContains(
        entry -> entry.getMessage().contains("camunda.identity.issuer"),
        "expected a warning naming the missing issuer key");
    logs.assertDoesNotContain(
        entry -> entry.getMessage().contains("helm-secret"),
        "client secret value must never be logged");
  }

  @Test
  void shouldNotBridgeHelmChartIdentityConfigWhenAuth0IsConfigured() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID", "cloud-client");
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_DOMAIN", "weblogin.example.com");
    legacy.put("camunda.identity.issuer", "http://helm.example.com/realm");
    legacy.put("camunda.identity.clientId", "helm-client");
    legacy.put("CAMUNDA_IDENTITY_CLIENT_SECRET", "helm-secret");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "client-id")).isEqualTo("cloud-client");
    assertThat(env.getProperty(OIDC + "issuer-uri")).isEqualTo("https://weblogin.example.com/");
  }

  @Test
  void shouldLetExplicitCamundaSecurityValueTakePrecedenceOverBridgedDefault() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL", "http://localhost:18080/realm");
    legacy.put(OIDC + "issuer-uri", "https://operator-configured.example.com/realm");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "issuer-uri"))
        .isEqualTo("https://operator-configured.example.com/realm");
  }

  @Test
  void shouldBridgeAuth0CloudConfig() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID", "cloud-client");
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_CLIENTSECRET", "cloud-secret");
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_DOMAIN", "weblogin.example.com");
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_ORGANIZATION", "org-42");
    legacy.put("CAMUNDA_OPTIMIZE_CLIENT_CLUSTERID", "cluster-7");
    legacy.put("CAMUNDA_OPTIMIZE_CLIENT_AUDIENCE", "optimize");
    legacy.put("CAMUNDA_OPTIMIZE_M2M_ACCOUNTS_AUTH0_AUDIENCE", "cloud.accounts");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty("camunda.security.authentication.method")).isEqualTo("oidc");
    assertThat(env.getProperty(OIDC + "client-id")).isEqualTo("cloud-client");
    assertThat(env.getProperty(OIDC + "client-secret")).isEqualTo("cloud-secret");
    assertThat(env.getProperty(OIDC + "issuer-uri")).isEqualTo("https://weblogin.example.com/");
    // OR-set: the resource audience for public-API/M2M bearer tokens plus the webapp client id so
    // the login id_token (whose only aud is the client id) is also accepted. Mirrors the operator.
    assertThat(env.getProperty(OIDC + "audiences")).isEqualTo("optimize,cloud-client");
    assertThat(env.getProperty(OIDC + "organization-id")).isEqualTo("org-42");
    assertThat(env.getProperty(OIDC + "redirect-uri"))
        .isEqualTo("{baseScheme}://{baseHost}{basePort}/sso-callback?uuid=cluster-7");
    assertThat(env.getProperty("camunda.security.saas.organization-id")).isEqualTo("org-42");
    assertThat(env.getProperty("camunda.security.saas.cluster-id")).isEqualTo("cluster-7");
    // context path derived from the clusterId so CAMUNDA_OPTIMIZE_CONTEXT_PATH is not needed
    assertThat(env.getProperty("contextPath")).isEqualTo("/cluster-7");
    // Auth0 `audience` authorization param so the login token is valid for the cloud Accounts API
    assertThat(env.getProperty(OIDC + "authorize-request.additional-parameters.audience"))
        .isEqualTo("cloud.accounts");

    logs.assertDoesNotContain(
        entry -> entry.getMessage().contains("cloud-secret"),
        "client secret value must never be logged");
  }

  @Test
  void shouldNotSetSaasPropertiesWhenOnlyOrganizationIdIsPresent() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID", "cloud-client");
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_ORGANIZATION", "org-42");
    // no clusterId set

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty("camunda.security.saas.organization-id")).isNull();
    assertThat(env.getProperty("camunda.security.saas.cluster-id")).isNull();
    assertThat(env.getProperty("contextPath")).isNull();
    // redirect-uri still bridges to the Auth0 callback shape, but with no uuid query param
    assertThat(env.getProperty(OIDC + "redirect-uri"))
        .isEqualTo("{baseScheme}://{baseHost}{basePort}/sso-callback");
  }

  @Test
  void shouldBridgePublicApiJwtConfig() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put(
        "SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI",
        "https://idp.example.com/.well-known/jwks.json");
    legacy.put("CAMUNDA_OPTIMIZE_API_AUDIENCE", "optimize-public-api");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "jwk-set-uri"))
        .isEqualTo("https://idp.example.com/.well-known/jwks.json");
    assertThat(env.getProperty(OIDC + "audiences")).isEqualTo("optimize-public-api");
    logs.assertContains(
        entry ->
            entry.getMessage().contains("SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI")
                && entry.getMessage().contains(OIDC + "jwk-set-uri"),
        "expected deprecation warning for the public-API JWK set uri");
  }

  @Test
  void shouldMergeCcsmLoginAndPublicApiAudiences() {
    // A distinct login audience and public-API audience must both survive: dropping either breaks
    // the corresponding token validation. They map to CSL's single Set-valued audiences property.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL", "http://localhost:18080/realm");
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_AUDIENCE", "optimize-login");
    legacy.put("CAMUNDA_OPTIMIZE_API_AUDIENCE", "optimize-public-api");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "audiences")).isEqualTo("optimize-login,optimize-public-api");
  }

  @Test
  void shouldBridgeHelmChartIdentityAudienceIntoAudiencesSet() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.audience", "optimize-api");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "audiences")).isEqualTo("optimize-api");
  }

  @Test
  void shouldMergeHelmChartAudienceWithLegacyPublicApiAudience() {
    // The Helm chart's camunda.identity.audience (login/session token audience) and the legacy
    // CAMUNDA_OPTIMIZE_API_AUDIENCE (public-API bearer audience) must both survive: CSL applies one
    // audiences set to both the login id_token decoder and the api bearer decoder.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_API_AUDIENCE", "optimize-public-api");
    legacy.put("camunda.identity.audience", "optimize-login");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    // insertion order follows the code's call order: the pre-existing API-audience bridge call
    // runs before the new Helm-chart identity-audience bridge call
    assertThat(env.getProperty(OIDC + "audiences")).isEqualTo("optimize-public-api,optimize-login");
  }

  @Test
  void
      shouldNotBridgeHelmChartIdentityAudienceWhenLegacyIdentityAudienceIsAlreadySetAndNotDoubleWarn() {
    // Mirrors what application-ccsm.yaml actually does in a real docker-compose CCSM deployment:
    // CAMUNDA_OPTIMIZE_IDENTITY_AUDIENCE is mirrored into camunda.identity.audience automatically,
    // with the identical value — not a second operator choice.
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_AUDIENCE", "optimize-login");
    legacy.put("camunda.identity.audience", "optimize-login");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "audiences")).isEqualTo("optimize-login");
    logs.assertDoesNotContain(
        entry -> entry.getMessage().contains("camunda.identity.audience"),
        "the mirrored camunda.identity.audience key must not get its own warning when the legacy env var already won");
  }

  @Test
  void shouldNotBridgeHelmChartIdentityAudienceWhenAuth0IsConfigured() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID", "cloud-client");
    legacy.put("CAMUNDA_OPTIMIZE_CLIENT_AUDIENCE", "optimize");
    legacy.put("camunda.identity.audience", "optimize-api");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    // the CCSM-only camunda.identity.audience must not leak into the Auth0 audience set
    assertThat(env.getProperty(OIDC + "audiences")).isEqualTo("optimize,cloud-client");
  }

  @Test
  void shouldBridgeClientAudienceAndClientIdInAuth0ModeIgnoringPublicApiAudience() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID", "cloud-client");
    legacy.put("CAMUNDA_OPTIMIZE_CLIENT_AUDIENCE", "optimize");
    // The public-API audience is a CCSM-only key; legacy cloud never validated it, so it must not
    // leak into the audience set in Auth0 mode.
    legacy.put("CAMUNDA_OPTIMIZE_API_AUDIENCE", "optimize-public-api");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    // The resource audience (bearer path) and the client id (login id_token aud) both survive; the
    // CCSM-only public-API audience does not.
    assertThat(env.getProperty(OIDC + "audiences")).isEqualTo("optimize,cloud-client");
  }

  @Test
  void shouldAddClientIdToAudiencesForLoginWhenNoResourceAudienceInAuth0Mode() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID", "cloud-client");
    // No CAMUNDA_OPTIMIZE_CLIENT_AUDIENCE configured: the login id_token must still be accepted, so
    // the client id alone is bridged into the audiences set.
    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(OIDC + "audiences")).isEqualTo("cloud-client");
  }

  @Test
  void shouldNotWarnDeprecationForClientIdWhenAddedAsAudience() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID", "cloud-client");
    legacy.put("CAMUNDA_OPTIMIZE_CLIENT_AUDIENCE", "optimize");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    // The client id is bridged to client-id (its deprecation warning is emitted there); reusing it
    // as an audience must not emit a second "deprecated -> audiences" warning.
    logs.assertDoesNotContain(
        entry ->
            entry.getMessage().contains("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID")
                && entry.getMessage().contains(OIDC + "audiences"),
        "client id must not be reported as deprecated in favour of the audiences property");
  }

  @Test
  void shouldBridgeHstsMaxAge() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_SECURITY_RESPONSE_HEADERS_HSTS_MAX_AGE", "31536000");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty("camunda.security.http-headers.hsts.max-age-in-seconds"))
        .isEqualTo("31536000");
    assertThat(env.getProperty("camunda.security.http-headers.hsts.disabled")).isNull();
  }

  @Test
  void shouldDisableHeaderWhenHstsMaxAgeIsNegative() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_SECURITY_RESPONSE_HEADERS_HSTS_MAX_AGE", "-1");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty("camunda.security.http-headers.hsts.disabled")).isEqualTo("true");
    assertThat(env.getProperty("camunda.security.http-headers.hsts.max-age-in-seconds")).isNull();
    // the migration hint must point at hsts.disabled for the disable case, not max-age-in-seconds
    logs.assertContains(
        entry ->
            entry.getMessage().contains("camunda.security.http-headers.hsts.disabled")
                && !entry.getMessage().contains("max-age-in-seconds"),
        "expected the deprecation warning to name hsts.disabled for a negative max-age");
  }

  @Test
  void shouldIgnoreObsoleteKeysWithDeprecationWarningAndNotFailStartup() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_SECURITY_AUTH_TOKEN_SECRET", "some-secret-value");
    legacy.put("CAMUNDA_OPTIMIZE_SECURITY_AUTH_COOKIE_MAX_SIZE", "3968");
    legacy.put("CAMUNDA_OPTIMIZE_SECURITY_AUTH_COOKIE_SAME_SITE_ENABLED", "false");
    legacy.put("OPTIMIZE_API_ACCESS_TOKEN", "static-token-value");
    legacy.put("security.responseHeaders.X-XSS-Protection", "0");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    logs.assertContains(
        entry -> entry.getMessage().contains("CAMUNDA_OPTIMIZE_SECURITY_AUTH_TOKEN_SECRET"),
        "expected obsolete-key warning for the token secret");
    logs.assertContains(
        entry -> entry.getMessage().contains("CAMUNDA_OPTIMIZE_SECURITY_AUTH_COOKIE_MAX_SIZE"),
        "expected obsolete-key warning for the cookie max size");
    logs.assertContains(
        entry ->
            entry.getMessage().contains("CAMUNDA_OPTIMIZE_SECURITY_AUTH_COOKIE_SAME_SITE_ENABLED"),
        "expected obsolete-key warning for the same-site cookie flag");
    logs.assertContains(
        entry -> entry.getMessage().contains("OPTIMIZE_API_ACCESS_TOKEN"),
        "expected obsolete-key warning for the static API access token");
    logs.assertContains(
        entry -> entry.getMessage().contains("security.responseHeaders.X-XSS-Protection"),
        "expected obsolete-key warning for X-XSS-Protection");

    // secret/token values must never be logged, only the key names
    logs.assertDoesNotContain(
        entry -> entry.getMessage().contains("some-secret-value"),
        "token secret value must never be logged");
    logs.assertDoesNotContain(
        entry -> entry.getMessage().contains("static-token-value"),
        "API access token value must never be logged");
  }

  @Test
  void shouldIgnoreMalformedHstsMaxAgeAndNotFailStartup() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_SECURITY_RESPONSE_HEADERS_HSTS_MAX_AGE", "not-a-number");

    final StandardEnvironment env = environmentWith(legacy);
    // must not throw during environment post-processing
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty("camunda.security.http-headers.hsts.max-age-in-seconds")).isNull();
    assertThat(env.getProperty("camunda.security.http-headers.hsts.disabled")).isNull();
    logs.assertContains(
        entry ->
            entry.getMessage().contains("CAMUNDA_OPTIMIZE_SECURITY_RESPONSE_HEADERS_HSTS_MAX_AGE"),
        "expected a warning naming the malformed HSTS key");
    // the (potentially sensitive) malformed value must not be logged
    logs.assertDoesNotContain(
        entry -> entry.getMessage().contains("not-a-number"),
        "the malformed value must not be logged");
  }

  @Test
  void shouldNotBridgeCcsmIdentityWhenAuth0IsConfigured() {
    final Map<String, Object> legacy = cslEnabledConfig();
    // both sets present: cloud wins, CCSM identity keys must be ignored
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID", "cloud-client");
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_DOMAIN", "weblogin.example.com");
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL", "http://localhost:18080/realm");
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_CLIENTID", "ccsm-client");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    // Auth0 values win, no mix with the CCSM identity values
    assertThat(env.getProperty(OIDC + "client-id")).isEqualTo("cloud-client");
    assertThat(env.getProperty(OIDC + "issuer-uri")).isEqualTo("https://weblogin.example.com/");
  }

  @Test
  void shouldNotDeriveContextPathWhenLegacyContextPathIsAlreadySet() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID", "cloud-client");
    legacy.put("CAMUNDA_OPTIMIZE_CLIENT_CLUSTERID", "cluster-7");
    // operator still configures the context path the legacy way
    legacy.put("CAMUNDA_OPTIMIZE_CONTEXT_PATH", "/legacy-path");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    // the clusterId-derived default must not shadow the operator's existing setting
    assertThat(env.getProperty("contextPath")).isNull();
  }

  @Test
  void shouldNotDeriveContextPathWhenExplicitSpringContextPathIsSet() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID", "cloud-client");
    legacy.put("CAMUNDA_OPTIMIZE_CLIENT_CLUSTERID", "cluster-7");
    legacy.put("contextPath", "/explicit");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty("contextPath")).isEqualTo("/explicit");
  }

  @ParameterizedTest
  @ValueSource(strings = {ELASTICSEARCH_DATABASE_PROPERTY, OPENSEARCH_DATABASE_PROPERTY})
  void shouldEnablePersistentSessionsOnEveryDatabase(final String database) {
    // given
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put(CAMUNDA_OPTIMIZE_DATABASE, database);
    final StandardEnvironment env = environmentWith(legacy);

    // when
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    // then the CSL session store is active on both editions, so sessions survive a restart and are
    // shared across instances rather than being kept in memory on one of them
    assertThat(env.getProperty(SessionConfiguration.PERSISTENT_ENABLED_PROPERTY)).isEqualTo("true");
  }

  @Test
  void shouldLetOperatorTurnOffPersistentSessions() {
    // given an operator who explicitly opted out
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put(SessionConfiguration.PERSISTENT_ENABLED_PROPERTY, "false");
    final StandardEnvironment env = environmentWith(legacy);

    // when
    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    // then the bridged default does not override them
    assertThat(env.getProperty(SessionConfiguration.PERSISTENT_ENABLED_PROPERTY))
        .isEqualTo("false");
  }
}
