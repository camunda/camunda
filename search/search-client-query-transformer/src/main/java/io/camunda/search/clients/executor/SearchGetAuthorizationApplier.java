/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.executor;

import io.camunda.security.auth.SecurityContext;
import io.camunda.security.auth.TypedPermissionCheck;
import io.camunda.security.impl.AuthorizationChecker;
import java.util.function.Function;

public class SearchGetAuthorizationApplier<RES> {

  private final SecurityContext<RES> securityContext;
  private final AuthorizationChecker authorizationChecker;

  public SearchGetAuthorizationApplier(
      final SecurityContext<RES> securityContext, final AuthorizationChecker authorizationChecker) {
    this.securityContext = securityContext;
    this.authorizationChecker = authorizationChecker;
  }

  public boolean check(final RES document) {
    final TypedPermissionCheck<RES> permissionCheck = securityContext.typedPermissionCheck();
    final var fn = (Function<RES, String>) securityContext.authorization().fn();
    System.out.println(fn.apply(document));
    final var resourceId = permissionCheck.resourceIdSupplier().apply(document);
    return authorizationChecker.isAuthorized(resourceId, securityContext);
  }
  ;
}
