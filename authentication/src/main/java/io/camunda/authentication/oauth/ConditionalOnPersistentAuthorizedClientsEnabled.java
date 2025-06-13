/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.oauth;

import io.camunda.authentication.oauth.ConditionalOnPersistentAuthorizedClientsEnabled.PersistentAuthorizedClientsCondition;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(PersistentAuthorizedClientsCondition.class)
public @interface ConditionalOnPersistentAuthorizedClientsEnabled {

  final class PersistentAuthorizedClientsCondition implements Condition {

    private final List<String> properties =
        List.of(
            "camunda.persistent.authorizedClients.enabled"); // CAMUNDA_PERSISTENT_AUTHORIZED-CLIENTS_ENABLED

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
//      return properties.stream()
//          .map(p -> context.getEnvironment().getProperty(p))
//          .anyMatch(Boolean::parseBoolean);
      return true;
    }
  }
}
