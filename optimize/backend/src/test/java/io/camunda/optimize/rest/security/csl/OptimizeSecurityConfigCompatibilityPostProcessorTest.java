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

import io.camunda.security.api.model.config.SessionConfiguration;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.event.Level;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class OptimizeSecurityConfigCompatibilityPostProcessorTest {

  private static final String OIDC = "camunda.security.authentication.oidc.";

  @RegisterExtension
  final LogCapturer logs =
      LogCapturer.create()
          .captureForType(OptimizeSecurityConfigCompatibilityPostProcessor.class, Level.WARN);

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
    processor.postProcessEnvironment(env, null);

    assertThat(env.getProperty("camunda.security.authentication.method")).isNull();
    assertThat(env.getProperty(OIDC + "client-id")).isNull();
    logs.assertContains(
        entry ->
            entry.getLevel() == Level.WARN
                && entry.getMessage().contains("optimize.security.csl.enabled")
                && entry.getMessage().contains("8.11"),
        "expected a deprecation warning naming the flag and the 8.11 removal");
  }

  @Test
  void shouldBridgeAndNotWarnFlagExplicitlyDisabledWhenValueIsNeitherTrueNorFalse() {
    // given a typo (e.g. "flase") rather than a literal "false": Boolean.parseBoolean would treat
    // it as false and wrongly log "set to false" for a value the operator never set to false
    final Map<String, Object> legacy = new HashMap<>();
    legacy.put("optimize.security.csl.enabled", "flase");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, null);

    assertThat(env.getProperty("camunda.security.authentication.method")).isEqualTo("oidc");
    logs.assertDoesNotContain(
        entry -> entry.getLevel() == Level.WARN && entry.getMessage().contains("set to false"),
        "expected no misleading 'set to false' warning for a value that isn't literally false");
  }

  @Test
  void shouldBridgeByDefaultWhenFlagAbsent() {
    // given the flag is not set at all — CSL is now the default (camunda/camunda#58483)
    final Map<String, Object> legacy = new HashMap<>();

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, null);

    assertThat(env.getProperty("camunda.security.authentication.method")).isEqualTo("oidc");
    logs.assertDoesNotContain(
        entry -> entry.getLevel() == Level.WARN,
        "expected no deprecation warning when the flag is simply left at its default");
  }

  @Test
  void shouldBridgeCcsmIdentityConfigAndLogDeprecation() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL", "http://localhost:18080/realm");
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_CLIENTID", "optimize");
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_CLIENTSECRET", "identity-secret");
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_AUDIENCE", "optimize-api");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

    assertThat(env.getProperty(OIDC + "client-id")).isEqualTo("cloud-client");
    assertThat(env.getProperty(OIDC + "issuer-uri")).isEqualTo("https://weblogin.example.com/");
  }

  @Test
  void shouldLetExplicitCamundaSecurityValueTakePrecedenceOverBridgedDefault() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL", "http://localhost:18080/realm");
    legacy.put(OIDC + "issuer-uri", "https://operator-configured.example.com/realm");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

    assertThat(env.getProperty(OIDC + "audiences")).isEqualTo("optimize-login,optimize-public-api");
  }

  @Test
  void shouldBridgeHelmChartIdentityAudienceIntoAudiencesSet() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("camunda.identity.audience", "optimize-api");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

    assertThat(env.getProperty(OIDC + "audiences")).isEqualTo("cloud-client");
  }

  @Test
  void shouldNotWarnDeprecationForClientIdWhenAddedAsAudience() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID", "cloud-client");
    legacy.put("CAMUNDA_OPTIMIZE_CLIENT_AUDIENCE", "optimize");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

    assertThat(env.getProperty("camunda.security.http-headers.hsts.max-age-in-seconds"))
        .isEqualTo("31536000");
    assertThat(env.getProperty("camunda.security.http-headers.hsts.disabled")).isNull();
  }

  @Test
  void shouldDisableHeaderWhenHstsMaxAgeIsNegative() {
    final Map<String, Object> legacy = cslEnabledConfig();
    legacy.put("CAMUNDA_OPTIMIZE_SECURITY_RESPONSE_HEADERS_HSTS_MAX_AGE", "-1");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

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
    processor.postProcessEnvironment(env, null);

    // then the bridged default does not override them
    assertThat(env.getProperty(SessionConfiguration.PERSISTENT_ENABLED_PROPERTY))
        .isEqualTo("false");
  }
}
