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
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Target({TYPE, METHOD})
@Retention(RUNTIME)
@Documented
@Conditional(ConditionalOnCamundaGroupsEnabled.GroupsClaimAbsentCondition.class)
public @interface ConditionalOnCamundaGroupsEnabled {

  /**
   * Matches when the OIDC groups-claim property is not configured, meaning groups are managed
   * internally. {@code GROUPS_CLAIM_PROPERTY} is camelCase ({@code groupsClaim}); querying the
   * {@link ConditionContext#getEnvironment()} with that exact string only matches a
   * camelCase-configured property. Canonicalizing it first makes the lookup match a kebab-case
   * ({@code groups-claim}) YAML key too, relying on Spring Boot's relaxed property binding.
   */
  class GroupsClaimAbsentCondition implements Condition {

    private static final String CANONICAL_GROUPS_CLAIM_PROPERTY =
        ConfigurationPropertyName.adapt(GROUPS_CLAIM_PROPERTY, '.').toString();

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final String groupsClaim =
          context.getEnvironment().getProperty(CANONICAL_GROUPS_CLAIM_PROPERTY);
      return groupsClaim == null || groupsClaim.isEmpty();
    }
  }
}
