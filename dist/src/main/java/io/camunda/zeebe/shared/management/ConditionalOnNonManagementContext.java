/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.management;

import static io.camunda.zeebe.shared.management.ConditionalOnManagementContext.OnManagementContextCondition.MANAGEMENT_ONLY_BEAN_NAME;

import io.camunda.zeebe.shared.management.ConditionalOnNonManagementContext.OnNonManagementContextCondition;
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

/** This annotation provides the inverse of {@link ConditionalOnManagementContext} */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(OnNonManagementContextCondition.class)
public @interface ConditionalOnNonManagementContext {
  /**
   * A condition which matches if the management context is disabled or the same as the base server
   * context. If the management context is on a different server, it will match iff for the absence
   * of a management specific bean on the current context.
   */
  final class OnNonManagementContextCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(
        final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final var managementPortType = ManagementPortType.get(context.getEnvironment());
      return switch (managementPortType) {
        case DISABLED, SAME -> ConditionOutcome.match();
        case DIFFERENT ->
            context.getRegistry().containsBeanDefinition(MANAGEMENT_ONLY_BEAN_NAME)
                ? ConditionOutcome.noMatch("management context")
                : ConditionOutcome.match();
      };
    }
  }
}
