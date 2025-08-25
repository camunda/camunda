/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.authentication.config.AuthenticationProperties;
import io.camunda.security.configuration.AuthenticationConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(ConditionalOnCertificateAuthEnabled.CertificateAuthCondition.class)
public @interface ConditionalOnCertificateAuthEnabled {

  class CertificateAuthCondition implements Condition {
    private static final String CERT_AUTH_ENABLED_PROPERTY = "camunda.security.cert-auth.enabled";
    private static final String CERT_AUTH_ENABLED_ENV_VAR = "CAMUNDA_SECURITY_CERT_AUTH_ENABLED";

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final Environment env = context.getEnvironment();
      final String propValue = env.getProperty(CERT_AUTH_ENABLED_PROPERTY);
      final String envValue = env.getProperty(CERT_AUTH_ENABLED_ENV_VAR);
      final boolean certAuthEnabled =
          env.getProperty(CERT_AUTH_ENABLED_PROPERTY, Boolean.class, false)
              || env.getProperty(CERT_AUTH_ENABLED_ENV_VAR, Boolean.class, false);

      // Check if mTLS is also enabled (for user management even with BASIC auth)
      final boolean mtlsEnabled =
          env.getProperty("camunda.security.authentication.mtls.enabled", Boolean.class, false)
              || env.getProperty(
                  "CAMUNDA_SECURITY_AUTHENTICATION_MTLS_ENABLED", Boolean.class, false);

      // Certificate service should be available if:
      // 1. Certificate auth is enabled AND auth method is not BASIC (for OIDC certificate flows)
      // 2. OR mTLS is enabled (for user management regardless of auth method)
      final AuthenticationMethod currentMethod =
          AuthenticationMethod.parse(env.getProperty(AuthenticationProperties.METHOD))
              .orElse(AuthenticationConfiguration.DEFAULT_METHOD);
      final boolean notBasicAuth = currentMethod != AuthenticationMethod.BASIC;

      final boolean enabled = (certAuthEnabled && notBasicAuth) || mtlsEnabled;
      return enabled;
    }
  }
}
