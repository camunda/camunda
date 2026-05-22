/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.application.commons.identity.PhysicalTenantsConfiguredCondition;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rewrites the request URI for PT-prefixed webapp static-resource paths so Spring Boot's default
 * static-resource handler (mapped to {@code /operate/**}, {@code /tasklist/**}, {@code /admin/**})
 * can serve the file. After {@link PhysicalTenantWebappContextPathInterceptor} sets the rendered
 * SPA's {@code <base href>} to {@code /physical-tenant/<id>/operate/}, the browser issues asset
 * requests under that prefix; the dispatcher then has nowhere to send them (no controller, no
 * resource handler mapped at the PT-prefixed path).
 *
 * <p>This filter intercepts requests of the form {@code
 * /physical-tenant/{tenantId}/{operate|tasklist|admin}/(assets/...|favicon.ico)}, wraps them with
 * an {@link HttpServletRequestWrapper} that reports the unprefixed URI from {@link
 * HttpServletRequest#getRequestURI()} and {@link HttpServletRequest#getServletPath()}, and
 * continues the filter chain. The dispatcher resolves the request against the unprefixed URI and
 * the standard static-resource handler serves the bytes — no forward, no second dispatch.
 *
 * <p>Filter order: {@link Ordered#LOWEST_PRECEDENCE LOWEST_PRECEDENCE} - 100. Spring Security's
 * {@code FilterChainProxy} runs much earlier (default order is around {@code
 * REQUEST_WRAPPER_FILTER_MAX_PRECEDENCE - 100}), so by the time we get the request the PT chain has
 * already authenticated it on the original PT-prefixed URI — exactly what we want.
 *
 * <p>Conditional on {@code camunda.physical-tenants.*} being configured.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
@Conditional(PhysicalTenantsConfiguredCondition.class)
public class PhysicalTenantWebappAssetRewriteFilter extends OncePerRequestFilter {

  private static final Pattern PT_ASSET =
      Pattern.compile(
          "^/physical-tenant/[^/]+/(operate|tasklist|admin)/(assets/.+|favicon\\.ico)$");

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
      throws ServletException, IOException {
    final Matcher m = PT_ASSET.matcher(request.getRequestURI());
    if (!m.matches()) {
      chain.doFilter(request, response);
      return;
    }
    final String unprefixed = "/" + m.group(1) + "/" + m.group(2);
    chain.doFilter(new RewrittenUriRequest(request, unprefixed), response);
  }

  private static final class RewrittenUriRequest extends HttpServletRequestWrapper {

    private final String uri;

    RewrittenUriRequest(final HttpServletRequest request, final String uri) {
      super(request);
      this.uri = uri;
    }

    @Override
    public String getRequestURI() {
      return uri;
    }

    @Override
    public String getServletPath() {
      // The default DispatcherServlet is mapped at "/", so the servlet path equals the request
      // URI minus the (empty) context path. Returning the rewritten URI keeps Spring's path
      // helpers consistent — handler mapping and resource lookup both read from here.
      return uri;
    }
  }
}
