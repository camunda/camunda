/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.shared.management.ConditionalOnManagementContext.OnManagementContextCondition;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * This conditional annotation only works for {@link
 * org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration} factories
 * registered in {@code
 * /META-INF/spring/org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration.imports}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(OnManagementContextCondition.class)
public @interface ConditionalOnManagementContext {

  /**
   * A condition which matches if the management context is the same as the base server context or
   * if it's a different one, and we're currently configuring it.
   */
  final class OnManagementContextCondition extends SpringBootCondition {

    static final String MANAGEMENT_ONLY_BEAN_NAME = "ManagementContextWebServerFactory";

    @Override
    public ConditionOutcome getMatchOutcome(
        final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final var managementPortType = ManagementPortType.get(context.getEnvironment());
      return switch (managementPortType) {
        case DISABLED -> ConditionOutcome.noMatch("no management application defined");
        case SAME -> ConditionOutcome.match();
        case DIFFERENT ->
            context.getRegistry().containsBeanDefinition(MANAGEMENT_ONLY_BEAN_NAME)
                ? ConditionOutcome.match()
                : ConditionOutcome.noMatch("not the management context");
      };
    }
  }
}
