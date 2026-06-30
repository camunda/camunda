/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.spring.context.holder.HttpSessionBasedAuthenticationHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Forces materialisation of lazy membership lists in the session-held {@link CamundaAuthentication}
 * during {@code afterCompletion} — while {@code RequestContextHolder} (and therefore {@code
 * PhysicalTenantContext}) is still bound to the current request thread — so that Java serialisation
 * of the session attribute during {@code SessionRepositoryFilter.commitSession} (which runs after
 * {@code DispatcherServlet} resets the request scope) can succeed without invoking the suppliers.
 *
 * <p>Without this, {@code LazyList.writeReplace()} calls the membership supplier during
 * serialisation; the supplier calls {@code PhysicalTenantContext.current()}, which throws because
 * the request scope is already torn down, causing a 500 {@code ConversionFailedException}.
 */
class SessionCamundaAuthenticationMaterializationInterceptor implements HandlerInterceptor {

  @Override
  public void afterCompletion(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Object handler,
      final Exception ex) {
    final var session = request.getSession(false);
    if (session == null) {
      return;
    }
    final var attr =
        session.getAttribute(
            HttpSessionBasedAuthenticationHolder.CAMUNDA_AUTHENTICATION_SESSION_HOLDER_KEY);
    if (!(attr instanceof final CamundaAuthentication auth)) {
      return;
    }
    // Touch each list to trigger LazyList.resolve() while the request scope is still active.
    // LazyList memoises the result and clears the supplier, so the subsequent writeReplace()
    // during commitSession returns the cached value without needing PhysicalTenantContext.
    auth.authenticatedGroupIds().size();
    auth.authenticatedRoleIds().size();
    auth.authenticatedTenantIds().size();
    auth.authenticatedMappingRuleIds().size();
  }
}
