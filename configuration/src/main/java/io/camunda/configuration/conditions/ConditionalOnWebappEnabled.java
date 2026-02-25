/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.conditions;

import io.camunda.configuration.conditions.ConditionalOnWebappEnabled.OnWebappEnabledCondition;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnWebappEnabledCondition.class)
public @interface ConditionalOnWebappEnabled {

  String[] value();

  class OnWebappEnabledCondition implements Condition {

    protected String getConditionalClassName() {
      return ConditionalOnWebappEnabled.class.getName();
    }

    protected Set<String> getRequiredProperties(final String webappName) {
      return Set.of(
          "camunda." + webappName + ".webapp-enabled", // legacy
          "camunda.webapps." + webappName + ".enabled"); // unified config
    }

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final Map<String, Object> attributes =
          metadata.getAnnotationAttributes(getConditionalClassName());

      if (attributes == null) {
        throw new RuntimeException(
            "Failed to retrieve annotation attributes for " + getConditionalClassName());
      }

      final String[] webappNames = (String[]) attributes.get("value");
      if (webappNames == null || webappNames.length == 0) {
        throw new RuntimeException(
            "Failed to retrieve webapp name from " + getConditionalClassName() + " annotation");
      }

      for (String webappName : webappNames) {
        webappName = webappName.trim().toLowerCase();
        for (String property : getRequiredProperties(webappName)) {
          final boolean value = context.getEnvironment().getProperty(property, Boolean.class, true);
          if (!value) {
            return false;
          }
        }
      }

      return true;
    }
  }
}
