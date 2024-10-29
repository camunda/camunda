/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import java.util.function.BiFunction;

public final class SecurityContextAwareDelegate<T> implements SecurityContextAware<T> {

  private final T delegate;

  private final BiFunction<T, SecurityContext, T> securityContextApplier;

  public SecurityContextAwareDelegate(
      final T delegate, final BiFunction<T, SecurityContext, T> securityContextApplier) {
    this.delegate = delegate;
    this.securityContextApplier = securityContextApplier;
  }

  @Override
  public T withSecurityContext(final SecurityContext securityContext) {
    return securityContextApplier.apply(delegate, securityContext);
  }
}
