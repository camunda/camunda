/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.utils;

import io.camunda.spring.utils.ConditionalOnWebappEnabled.OnWebappEnabledCondition;
import io.camunda.spring.utils.ConditionalOnWebappUiEnabled.OnWebappUiEnabledCondition;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
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
      final Set<String> result = new HashSet<>();
      result.addAll(super.getRequiredProperties(webappName));
      result.add("camunda.webapps." + webappName + ".ui-enabled");
      return result;
    }
  }
}
