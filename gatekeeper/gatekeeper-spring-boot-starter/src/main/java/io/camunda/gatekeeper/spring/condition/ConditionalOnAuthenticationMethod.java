/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.condition;

import io.camunda.gatekeeper.model.identity.AuthenticationMethod;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(ConditionalOnAuthenticationMethod.AuthenticationMethodCondition.class)
public @interface ConditionalOnAuthenticationMethod {

  AuthenticationMethod value();

  /** Property key for the authentication method. */
  String METHOD_PROPERTY = "camunda.security.authentication.method";

  /** Default authentication method when no property is set. */
  AuthenticationMethod DEFAULT_METHOD = AuthenticationMethod.BASIC;

  final class AuthenticationMethodCondition implements Condition {
    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final Map<String, Object> attributes =
          metadata.getAnnotationAttributes(ConditionalOnAuthenticationMethod.class.getName());
      if (attributes == null) {
        throw new IllegalStateException(
            "%s must be used as a condition on %s"
                .formatted(
                    AuthenticationMethodCondition.class.getName(),
                    ConditionalOnAuthenticationMethod.class.getName()));
      }
      final Environment env = context.getEnvironment();
      return AuthenticationMethod.parse(env.getProperty(METHOD_PROPERTY)).orElse(DEFAULT_METHOD)
          == attributes.get("value");
    }
  }
}
