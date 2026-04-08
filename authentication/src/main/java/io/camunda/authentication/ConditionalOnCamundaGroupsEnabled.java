/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static io.camunda.security.configuration.OidcAuthenticationConfiguration.GROUPS_CLAIM_PROPERTY;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition that matches when the OIDC groups-claim property is <b>not</b> configured, meaning the
 * platform should manage groups internally (Groups API enabled, Groups UI visible).
 */
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@Documented
@Conditional(ConditionalOnCamundaGroupsEnabled.OnGroupsClaimAbsentCondition.class)
public @interface ConditionalOnCamundaGroupsEnabled {

  class OnGroupsClaimAbsentCondition implements Condition {
    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final String groupsClaim =
          context.getEnvironment().getProperty(GROUPS_CLAIM_PROPERTY);
      return groupsClaim == null || groupsClaim.isEmpty();
    }
  }
}
