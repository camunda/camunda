/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.conditions;

import com.google.common.collect.Sets;
import io.camunda.configuration.conditions.ConditionalOnWebappEnabled.OnWebappEnabledCondition;
import io.camunda.configuration.conditions.ConditionalOnWebappUiEnabled.OnWebappUiEnabledCondition;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.Conditional;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnWebappUiEnabledCondition.class)
public @interface ConditionalOnWebappUiEnabled {

  String[] value();

  class OnWebappUiEnabledCondition extends OnWebappEnabledCondition implements Condition {

    @Override
    protected String getConditionalClassName() {
      return ConditionalOnWebappUiEnabled.class.getName();
    }

    @Override
    protected Set<String> getRequiredProperties(final String webappName) {
      return Sets.union(
          super.getRequiredProperties(webappName),
          Set.of("camunda.webapps." + webappName + ".ui-enabled"));
    }
  }
}
