/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.handler;

import io.camunda.service.AuthorizationServices;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;

public class CustomMethodSecurityExpressionHandler extends DefaultMethodSecurityExpressionHandler {
  private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();
  private final AuthorizationServices<AuthorizationRecord> authorizationServices;

  public CustomMethodSecurityExpressionHandler(
      final AuthorizationServices<AuthorizationRecord> authorizationServices) {
    this.authorizationServices = authorizationServices;
  }

  @Override
  protected MethodSecurityExpressionOperations createSecurityExpressionRoot(
      final Authentication authentication, final MethodInvocation invocation) {
    final CustomMethodSecurityExpressionRoot root =
        new CustomMethodSecurityExpressionRoot(authentication, authorizationServices);
    root.setPermissionEvaluator(getPermissionEvaluator());
    root.setTrustResolver(trustResolver);
    root.setRoleHierarchy(getRoleHierarchy());
    return root;
  }
}
