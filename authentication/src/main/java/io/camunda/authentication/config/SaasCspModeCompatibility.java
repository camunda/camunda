/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.api.model.config.headers.ContentSecurityPolicyConfig;
import io.camunda.security.api.model.config.headers.ContentSecurityPolicyConfig.Mode;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Compatibility layer that flips the CSL Content-Security-Policy mode to {@link Mode#SAAS} when
 * OC's legacy {@code camunda.security.saas.organization-id} detection indicates a SaaS deployment
 * and the operator hasn't explicitly set the CSP mode. Preserves the previous behaviour where SaaS
 * deployments automatically picked the SaaS CSP default without needing to set the mode property.
 *
 * <p>The actual policy resolution is delegated to CSL's {@link
 * ContentSecurityPolicyConfig#resolvePolicy()}. This shim only adjusts the mode.
 *
 * <p>Implemented as a {@link BeanPostProcessor} on the {@link CamundaSecurityLibraryProperties}
 * bean so the mutation lands before the bean is injected into the CSL filter-chain configurations.
 */
@Configuration
@Profile("consolidated-auth")
public class SaasCspModeCompatibility {

  static final String CSP_MODE_KEY = "camunda.security.http-headers.content-security-policy.mode";
  static final String SAAS_ORGANIZATION_ID_KEY = "camunda.security.saas.organization-id";

  /**
   * Declared as a {@code static} factory method so the {@link BeanPostProcessor} can be
   * instantiated by Spring without requiring the enclosing {@code @Configuration} to be fully
   * resolved first — important because BPPs participate in the bean-creation lifecycle of every
   * other bean.
   */
  @Bean
  public static BeanPostProcessor saasCspModeBeanPostProcessor(final ApplicationContext context) {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(final Object bean, final String beanName) {
        if (!(bean instanceof final CamundaSecurityLibraryProperties props)) {
          return bean;
        }
        if (Binder.get(context.getEnvironment()).bind(CSP_MODE_KEY, String.class).isBound()) {
          // Operator-supplied mode wins, regardless of SaaS detection.
          return bean;
        }
        // Read the SaaS organization-id directly from the environment rather than via
        // SecurityConfiguration — some application contexts (e.g. standalone gateway) contain
        // multiple SecurityConfiguration beans, which makes a typed bean lookup ambiguous. The
        // property is the source of truth for SaaS detection either way.
        final var organizationId =
            Binder.get(context.getEnvironment())
                .bind(SAAS_ORGANIZATION_ID_KEY, String.class)
                .orElse(null);
        if (organizationId == null || organizationId.isBlank()) {
          // Not a SaaS deployment; let CSL's SELF_MANAGED default apply.
          return bean;
        }
        props.getHttpHeaders().getContentSecurityPolicy().setMode(Mode.SAAS);
        return bean;
      }
    };
  }
}
