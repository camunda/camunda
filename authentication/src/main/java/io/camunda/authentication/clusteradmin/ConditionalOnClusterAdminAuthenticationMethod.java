/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.clusteradmin;

import io.camunda.authentication.clusteradmin.ClusterApiSettings.ClusterAdminAuthenticationMethod;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when the effective cluster-admin authentication method (see {@link
 * ClusterApiSettings#load}) equals {@link #value()}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(ConditionalOnClusterAdminAuthenticationMethod.OnClusterAdminMethod.class)
public @interface ConditionalOnClusterAdminAuthenticationMethod {

  ClusterAdminAuthenticationMethod value();

  final class OnClusterAdminMethod implements Condition {
    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final Map<String, Object> attributes =
          metadata.getAnnotationAttributes(
              ConditionalOnClusterAdminAuthenticationMethod.class.getName());
      if (attributes == null) {
        return false;
      }
      return attributes.get("value")
          == ClusterApiSettings.load(context.getEnvironment()).authenticationMethod();
    }
  }
}
