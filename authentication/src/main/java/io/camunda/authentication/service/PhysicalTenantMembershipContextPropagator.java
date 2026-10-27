/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import io.camunda.security.api.context.MembershipResolutionContextPropagator;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.List;
import java.util.function.Supplier;

/**
 * Host implementation of CSL's {@link MembershipResolutionContextPropagator} that carries the
 * physical tenant resolved while building the authentication onto whatever thread or scope later
 * materialises a lazy membership list.
 *
 * <p>{@link DefaultMembershipService} routes each membership query to a per-tenant service via
 * {@link PhysicalTenantContext#current()}. Without this propagator that call would throw when the
 * list resolves outside the request scope — most visibly when persistent web sessions serialise the
 * {@code CamundaAuthentication} after the request has been dispatched. By capturing the tenant at
 * construction time (in scope) and rebinding it around the deferred lookup, the routing key is
 * available wherever resolution happens to run.
 */
public final class PhysicalTenantMembershipContextPropagator
    implements MembershipResolutionContextPropagator {

  @Override
  public Supplier<List<String>> decorate(final Supplier<List<String>> supplier) {
    return PhysicalTenantContext.propagateCurrent(supplier);
  }
}
