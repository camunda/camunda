/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config.sm;

import static io.camunda.migration.identity.config.IdentityMigrationProperties.PROP_CAMUNDA_MIGRATION_IDENTITY_MODE;

import io.camunda.migration.identity.config.IdentityMigrationProperties.Mode;
import io.camunda.migration.identity.config.sm.ConditionalOnOidc.ConditionalOnOidcCondition;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(ConditionalOnOidcCondition.class)
public @interface ConditionalOnOidc {

  final class ConditionalOnOidcCondition implements Condition {

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final Mode mode =
          Optional.ofNullable(
                  context.getEnvironment().getProperty(PROP_CAMUNDA_MIGRATION_IDENTITY_MODE))
              .map(Mode::valueOf)
              .orElse(Mode.CLOUD);
      return Mode.OIDC.equals(mode);
    }
  }
}
