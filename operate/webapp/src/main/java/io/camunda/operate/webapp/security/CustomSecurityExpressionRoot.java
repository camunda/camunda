/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security;

import java.util.List;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;

public class CustomSecurityExpressionRoot extends SecurityExpressionRoot
    implements MethodSecurityExpressionOperations {

  private UserService<? extends Authentication> userService;

  private Object filterObject;
  private Object returnObject;
  private Object target;

  public CustomSecurityExpressionRoot(Authentication authentication) {
    super(authentication);
  }

  // is used as SPEL for preauthorize annotation
  public boolean hasPermission(String permission) {
    final List<Permission> permissions = userService.getCurrentUser().getPermissions();
    return permissions != null && permissions.contains(Permission.fromString(permission));
  }

  public CustomSecurityExpressionRoot setUserService(
      final UserService<? extends Authentication> userService) {
    this.userService = userService;
    return this;
  }

  @Override
  public Object getFilterObject() {
    return filterObject;
  }

  @Override
  public void setFilterObject(Object filterObject) {
    this.filterObject = filterObject;
  }

  @Override
  public Object getReturnObject() {
    return returnObject;
  }

  @Override
  public void setReturnObject(Object returnObject) {
    this.returnObject = returnObject;
  }

  @Override
  public Object getThis() {
    return target;
  }

  public void setThis(Object target) {
    this.target = target;
  }
}
