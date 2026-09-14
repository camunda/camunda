/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.security.api.model.config.SaasConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Makes {@code camunda.security.saas.cluster-id} and {@code camunda.security.saas.organization-id}
 * sufficient on their own, so a CCSaaS deployment no longer has to set the legacy {@code
 * CAMUNDA_OPTIMIZE_CLIENT_CLUSTERID} / {@code CAMUNDA_OPTIMIZE_AUTH0_ORGANIZATION} pair as well
 * (camunda/camunda#61604).
 *
 * <p>Three blocks of {@code service-config.yaml} read the legacy pair: {@code security.auth.cloud},
 * {@code analytics.mixpanel.properties} and {@code onboarding.properties}. Without the legacy keys
 * the first resolves to an empty string, which fails startup in {@link
 * OptimizeCloudSecurityConfiguration}, while the other two fall back to their {@code dev} defaults
 * and report a wrong organization and cluster id to the UI.
 *
 * <p>The fallback cannot live in {@code service-config.yaml}: its {@code ${...}} placeholders are
 * resolved by Optimize's own {@code ConfigurationParser} against system properties and environment
 * variables only, in a single pass, so the inner placeholder of a {@code
 * ${LEGACY:${camunda.security.saas.cluster-id:}}} chain would be stored verbatim.
 *
 * <p>Binds CSL's own {@link SaasConfiguration} under CSL's own prefix rather than looking the two
 * properties up by name. Relaxed binding accepts spellings {@code Environment#getProperty} does
 * not, and the SaaS control plane emits one of them ({@code CAMUNDA_SECURITY_SAAS_CLUSTERID}, with
 * no underscore before {@code ID}). Binding what CSL binds keeps the two in agreement whatever the
 * operator sets.
 *
 * <p>A legacy key that is set still wins, so existing CCSaaS deployments are unaffected, and the
 * {@code dev} defaults still apply when neither pair is set. "Set" is checked the same way {@code
 * ConfigurationParser} resolves it, {@code System.getProperty} then {@code System.getenv}, not
 * {@code Environment#getProperty}, which also sees command-line args, {@code application.yaml} and
 * {@code spring.config.import} — sources {@code ConfigurationParser} never reads, so a legacy key
 * set only through one of those would otherwise look "set" here while the value never actually
 * reached {@code ConfigurationService}.
 *
 * <p>Independent of {@link OptimizeSecurityConfigCompatibilityPostProcessor}, so it outlives that
 * bridge's removal in 8.11 (camunda/camunda#58485).
 */
@Component
public class SaasOrgAndClusterIdBackfill implements BeanPostProcessor {

  static final String LEGACY_CLUSTER_ID_KEY = "CAMUNDA_OPTIMIZE_CLIENT_CLUSTERID";
  static final String LEGACY_ORGANIZATION_ID_KEY = "CAMUNDA_OPTIMIZE_AUTH0_ORGANIZATION";

  private static final String SAAS_PREFIX = "camunda.security.saas";

  private static final Logger LOG = LoggerFactory.getLogger(SaasOrgAndClusterIdBackfill.class);

  private final Environment environment;

  public SaasOrgAndClusterIdBackfill(final Environment environment) {
    this.environment = environment;
  }

  @Override
  public Object postProcessAfterInitialization(final Object bean, final String beanName) {
    if (bean instanceof final ConfigurationService configurationService) {
      backfill(configurationService);
    }
    return bean;
  }

  private void backfill(final ConfigurationService configurationService) {
    final SaasConfiguration saas =
        Binder.get(environment).bind(SAAS_PREFIX, SaasConfiguration.class).orElse(null);
    if (saas == null) {
      return;
    }
    if (isBackfillable(saas.getClusterId(), LEGACY_CLUSTER_ID_KEY)) {
      setClusterId(configurationService, saas.getClusterId());
      LOG.info(
          "Optimize's cluster id was not set via '{}', backfilling it from '{}'",
          LEGACY_CLUSTER_ID_KEY,
          SAAS_PREFIX + ".cluster-id");
    }
    if (isBackfillable(saas.getOrganizationId(), LEGACY_ORGANIZATION_ID_KEY)) {
      setOrganizationId(configurationService, saas.getOrganizationId());
      LOG.info(
          "Optimize's organization id was not set via '{}', backfilling it from '{}'",
          LEGACY_ORGANIZATION_ID_KEY,
          SAAS_PREFIX + ".organization-id");
    }
  }

  private static void setClusterId(
      final ConfigurationService configurationService, final String clusterId) {
    configurationService.getAuthConfiguration().getCloudAuthConfiguration().setClusterId(clusterId);
    configurationService.getAnalytics().getMixpanel().getProperties().setClusterId(clusterId);
    configurationService.getOnboarding().getProperties().setClusterId(clusterId);
  }

  private static void setOrganizationId(
      final ConfigurationService configurationService, final String organizationId) {
    configurationService
        .getAuthConfiguration()
        .getCloudAuthConfiguration()
        .setOrganizationId(organizationId);
    configurationService
        .getAnalytics()
        .getMixpanel()
        .getProperties()
        .setOrganizationId(organizationId);
    configurationService.getOnboarding().getProperties().setOrganizationId(organizationId);
  }

  // Mirrors how ConfigurationParser resolves the legacy placeholders: System.getProperty, then
  // System.getenv, nothing else. environment.getProperty also sees command-line args,
  // application.yaml and spring.config.import, none of which ConfigurationParser reads, so a
  // legacy key set through one of those would make this treat the key as set while
  // ConfigurationService never actually received the value.
  private static boolean isBackfillable(final String saasValue, final String legacyKey) {
    return !isBlank(saasValue) && isBlank(legacyValue(legacyKey));
  }

  private static String legacyValue(final String legacyKey) {
    final String systemProperty = System.getProperty(legacyKey);
    return systemProperty != null ? systemProperty : System.getenv(legacyKey);
  }

  private static boolean isBlank(final String value) {
    return value == null || value.isBlank();
  }
}
