/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Rewrites the {@code contextPath} model attribute on webapp index controllers (e.g. operate's
 * {@code OperateIndexController#tasklist}) so that, when the request URL is PT-prefixed, the
 * rendered SPA emits PT-prefixed URLs for its assets and API calls — keeping subsequent navigation
 * inside the PT cookie scope.
 *
 * <p>Operate's controller sets {@code model.contextPath = context.getContextPath() + "/operate/"}
 * unconditionally. Without this interceptor the rendered HTML's base href would always be {@code
 * /operate/}, so even after a PT-prefixed entry path the browser would drop back to the unprefixed
 * {@code /operate/...} URL space, where the PT session cookie ({@code Path=/physical- tenant/<id>})
 * does not apply.
 *
 * <p>Scope: runs only on requests whose URI starts with {@code /physical-tenant/<id>/}. For
 * unprefixed access (e.g. plain {@code /operate}) the model attribute is left untouched.
 *
 * <p>The interceptor pairs with {@link PhysicalTenantWebappRequestMappingHandlerMapping}, which
 * registers the PT-prefixed sibling mappings; together they make a PT-prefixed entry path render a
 * fully PT-scoped SPA without changes to operate's own controller.
 */
public class PhysicalTenantWebappContextPathInterceptor implements HandlerInterceptor {

  private static final String PT_PATH_PREFIX = "/physical-tenant/";
  private static final String CONTEXT_PATH_ATTRIBUTE = "contextPath";

  @Override
  public void postHandle(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Object handler,
      @Nullable final ModelAndView modelAndView) {
    if (modelAndView == null) {
      return;
    }
    final String tenantPrefix = extractTenantPrefix(originalRequestUri(request));
    if (tenantPrefix == null) {
      return;
    }
    final Object existing = modelAndView.getModel().get(CONTEXT_PATH_ATTRIBUTE);
    if (existing instanceof final String existingPath) {
      modelAndView.addObject(CONTEXT_PATH_ATTRIBUTE, tenantPrefix + existingPath);
    }
  }

  /**
   * Returns the URI the browser actually requested, transparently looking through any internal
   * {@link RequestDispatcher#forward} chain. {@code OperateIndexController#forwardToOperate}
   * returns {@code "forward:/operate"} for any SPA sub-path, which makes Spring forward to {@code
   * /operate} internally; {@link HttpServletRequest#getRequestURI()} then reports {@code /operate}
   * on the forwarded request, losing the PT prefix. The original request URI is preserved on the
   * request attribute {@link RequestDispatcher#FORWARD_REQUEST_URI}.
   */
  private static String originalRequestUri(final HttpServletRequest request) {
    final Object forwardUri = request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
    if (forwardUri instanceof final String forwarded) {
      return forwarded;
    }
    return request.getRequestURI();
  }

  /**
   * If {@code requestUri} matches {@code /physical-tenant/<id>/<anything>} returns {@code
   * /physical-tenant/<id>}; otherwise returns {@code null}.
   */
  private static @Nullable String extractTenantPrefix(final String requestUri) {
    if (requestUri == null || !requestUri.startsWith(PT_PATH_PREFIX)) {
      return null;
    }
    final int afterPrefix = PT_PATH_PREFIX.length();
    final int nextSlash = requestUri.indexOf('/', afterPrefix);
    if (nextSlash < 0 || nextSlash == afterPrefix) {
      return null;
    }
    return requestUri.substring(0, nextSlash);
  }
}
