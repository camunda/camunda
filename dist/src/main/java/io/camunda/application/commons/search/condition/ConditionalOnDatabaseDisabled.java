/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search.condition;

import io.camunda.application.commons.search.condition.ConditionalOnDatabaseDisabled.OnDatabaseDisabled;
import io.camunda.application.commons.search.condition.ConditionalOnDatabaseEnabled.OnDatabaseEnabledCondition;
import java.lang.annotation.*;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(OnDatabaseDisabled.class)
public @interface ConditionalOnDatabaseDisabled {

  class OnDatabaseDisabled implements Condition {
    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      return !new OnDatabaseEnabledCondition().matches(context, metadata);
    }
  }
}
