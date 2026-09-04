/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static io.camunda.optimize.rest.security.csl.SaasOrgAndClusterIdBackfill.LEGACY_CLUSTER_ID_KEY;
import static io.camunda.optimize.rest.security.csl.SaasOrgAndClusterIdBackfill.LEGACY_ORGANIZATION_ID_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import io.camunda.optimize.service.util.configuration.security.CloudAuthConfiguration;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Pins the CSL-only CCSaaS configuration: {@code camunda.security.saas.*} alone must drive every
 * consumer of the legacy cluster id and organization id, so the legacy pair can be dropped from the
 * SaaS control plane and the behaviour survives the removal of {@link
 * OptimizeSecurityConfigCompatibilityPostProcessor} in 8.11 (camunda/camunda#58485).
 */
class SaasOrgAndClusterIdBackfillTest {

  private static final String CLUSTER_ID = "cluster-from-csl";
  private static final String ORGANIZATION_ID = "org-from-csl";
  private static final String DEV_DEFAULT = "dev";

  @AfterEach
  void clearLegacySystemProperties() {
    System.clearProperty(LEGACY_CLUSTER_ID_KEY);
    System.clearProperty(LEGACY_ORGANIZATION_ID_KEY);
  }

  @Test
  void shouldBackfillEveryConsumerFromCamundaSecuritySaas() {
    // given
    final ConfigurableEnvironment environment =
        environmentWith(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.security.saas.cluster-id", CLUSTER_ID,
                    "camunda.security.saas.organization-id", ORGANIZATION_ID)));

    // when
    final ConfigurationService configuration = backfilledConfiguration(environment);

    // then
    assertThat(cloudAuth(configuration).getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(cloudAuth(configuration).getOrganizationId()).isEqualTo(ORGANIZATION_ID);
    assertThat(configuration.getAnalytics().getMixpanel().getProperties().getClusterId())
        .isEqualTo(CLUSTER_ID);
    assertThat(configuration.getAnalytics().getMixpanel().getProperties().getOrganizationId())
        .isEqualTo(ORGANIZATION_ID);
    assertThat(configuration.getOnboarding().getProperties().getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(configuration.getOnboarding().getProperties().getOrganizationId())
        .isEqualTo(ORGANIZATION_ID);
  }

  @Test
  void shouldBackfillFromTheEnvironmentVariableSpellingTheControlPlaneEmits() {
    // given the spelling camunda/camunda-operator emits, which only relaxed binding resolves
    final ConfigurableEnvironment environment =
        environmentWith(
            new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of(
                    "CAMUNDA_SECURITY_SAAS_CLUSTERID", CLUSTER_ID,
                    "CAMUNDA_SECURITY_SAAS_ORGANIZATIONID", ORGANIZATION_ID)));

    // when
    final ConfigurationService configuration = backfilledConfiguration(environment);

    // then
    assertThat(cloudAuth(configuration).getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(cloudAuth(configuration).getOrganizationId()).isEqualTo(ORGANIZATION_ID);
  }

  @Test
  void shouldKeepLegacyValuesWhenBothPairsAreSet() {
    // given
    System.setProperty(LEGACY_CLUSTER_ID_KEY, "cluster-from-legacy");
    System.setProperty(LEGACY_ORGANIZATION_ID_KEY, "org-from-legacy");
    final ConfigurableEnvironment environment =
        environmentWith(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.security.saas.cluster-id", CLUSTER_ID,
                    "camunda.security.saas.organization-id", ORGANIZATION_ID)));

    // when
    final ConfigurationService configuration = backfilledConfiguration(environment);

    // then
    assertThat(cloudAuth(configuration).getClusterId()).isEqualTo("cluster-from-legacy");
    assertThat(cloudAuth(configuration).getOrganizationId()).isEqualTo("org-from-legacy");
    assertThat(configuration.getAnalytics().getMixpanel().getProperties().getClusterId())
        .isEqualTo("cluster-from-legacy");
    assertThat(configuration.getOnboarding().getProperties().getOrganizationId())
        .isEqualTo("org-from-legacy");
  }

  @Test
  void shouldKeepShippedDefaultsWhenNeitherPairIsSet() {
    // given
    final ConfigurableEnvironment environment =
        environmentWith(new MapPropertySource("test", Map.of()));

    // when
    final ConfigurationService configuration = backfilledConfiguration(environment);

    // then
    assertThat(cloudAuth(configuration).getClusterId()).isEmpty();
    assertThat(cloudAuth(configuration).getOrganizationId()).isEmpty();
    assertThat(configuration.getAnalytics().getMixpanel().getProperties().getClusterId())
        .isEqualTo(DEV_DEFAULT);
    assertThat(configuration.getOnboarding().getProperties().getOrganizationId())
        .isEqualTo(DEV_DEFAULT);
  }

  @Test
  void shouldRejectForeignClusterTokenWhenConfiguredOnlyByCamundaSecuritySaas() {
    // given
    final ConfigurableEnvironment environment =
        environmentWith(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.security.saas.cluster-id", CLUSTER_ID,
                    "camunda.security.saas.organization-id", ORGANIZATION_ID)));
    final ConfigurationService configuration = backfilledConfiguration(environment);

    // when
    final OAuth2TokenValidator<Jwt> validator = clusterValidator(configuration);

    // then
    assertThat(
            validator
                .validate(jwt(Map.of(OptimizeCloudClusterValidator.CLUSTER_CLAIM, CLUSTER_ID)))
                .hasErrors())
        .isFalse();
    assertThat(
            validator
                .validate(jwt(Map.of(OptimizeCloudClusterValidator.CLUSTER_CLAIM, "other-cluster")))
                .hasErrors())
        .isTrue();
  }

  @Test
  void shouldApplyTheBackfillToTheConfigurationServiceBeanTheApplicationUses() {
    // given the real bean factory and the backfill registered as a BeanPostProcessor, so this
    // covers what the other tests cannot: that Spring runs the backfill against the
    // ConfigurationService every consumer is injected with, rather than a hand-built one
    new ApplicationContextRunner()
        .withUserConfiguration(ConfigurationServiceBuilder.class, SaasOrgAndClusterIdBackfill.class)
        .withPropertyValues(
            "camunda.security.saas.cluster-id=" + CLUSTER_ID,
            "camunda.security.saas.organization-id=" + ORGANIZATION_ID)

        // when
        .run(
            context -> {
              // then
              assertThat(context).hasNotFailed();
              final ConfigurationService configuration =
                  context.getBean(ConfigurationService.class);
              assertThat(cloudAuth(configuration).getClusterId()).isEqualTo(CLUSTER_ID);
              assertThat(cloudAuth(configuration).getOrganizationId()).isEqualTo(ORGANIZATION_ID);
              assertThat(configuration.getOnboarding().getProperties().getClusterId())
                  .isEqualTo(CLUSTER_ID);
            });
  }

  private static ConfigurationService backfilledConfiguration(
      final ConfigurableEnvironment environment) {
    final ConfigurationService configuration =
        ConfigurationServiceBuilder.createDefaultConfiguration();
    new SaasOrgAndClusterIdBackfill(environment)
        .postProcessAfterInitialization(configuration, "configurationService");
    return configuration;
  }

  // The real system environment is dropped so a CAMUNDA_SECURITY_SAAS_* variable set on the machine
  // running the tests cannot decide the outcome. System properties stay, because they are how a
  // test feeds the legacy keys to both ConfigurationParser and the backfill.
  private static ConfigurableEnvironment environmentWith(final PropertySource<?> source) {
    final StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    environment.getPropertySources().addFirst(source);
    return environment;
  }

  private static CloudAuthConfiguration cloudAuth(final ConfigurationService configuration) {
    return configuration.getAuthConfiguration().getCloudAuthConfiguration();
  }

  private static OAuth2TokenValidator<Jwt> clusterValidator(
      final ConfigurationService configuration) {
    final OidcProviderConfigurationPort oidcProviderConfigurationPort =
        mock(OidcProviderConfigurationPort.class);
    when(oidcProviderConfigurationPort.getOidcAuthenticationConfigurations()).thenReturn(Map.of());
    final TokenValidatorFactory factory =
        new OptimizeCloudSecurityConfiguration()
            .tokenValidatorFactory(
                oidcProviderConfigurationPort,
                configuration,
                new CamundaSecurityLibraryProperties());
    return factory.createTokenValidator(clientRegistration());
  }

  private static ClientRegistration clientRegistration() {
    return ClientRegistration.withRegistrationId("oidc")
        .clientId("optimize")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/api/authentication/callback")
        .authorizationUri("http://idp/authorize")
        .tokenUri("http://idp/token")
        .build();
  }

  private static Jwt jwt(final Map<String, Object> claims) {
    final Instant now = Instant.now();
    final Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .subject("user");
    claims.forEach(builder::claim);
    return builder.build();
  }
}
