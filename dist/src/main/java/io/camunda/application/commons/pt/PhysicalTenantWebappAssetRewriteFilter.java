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
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rewrites the request URI for two flavours of PT-prefixed URLs that have no PT-prefixed handler
 * but whose unprefixed equivalents are already served:
 *
 * <ol>
 *   <li><b>Webapp static resources</b> ({@code
 *       /physical-tenant/<id>/<app>/(assets/...|favicon.ico)}) — after {@link
 *       PhysicalTenantWebappContextPathInterceptor} rewrites the SPA's {@code <base href>} to the
 *       PT-prefixed URL, the browser emits asset requests under that prefix. Spring Boot's default
 *       static-resource handler is wired to the unprefixed {@code /<app>/**}, so the PT-prefixed
 *       URL would 404.
 *   <li><b>Cluster-scoped REST endpoints</b> ({@code /physical-tenant/<id>/v(1|2)/(license|status
 *       |topology|setup/user|rest-api.yaml)}) — controllers annotated with {@code @ClusterScoped}
 *       are deliberately not given PT-prefixed siblings by {@code
 *       PhysicalTenantRequestMappingHandlerMapping}, but the SPA happily prefixes every API call
 *       with the (PT-aware) contextPath and 404s on these. The cluster-scoped list is kept by
 *       convention in sync with the {@code @ClusterScoped} controllers in {@code
 *       zeebe/gateway-rest/.../controller/} (Status, License, Topology, GlobalError) plus the
 *       non-controller paths under {@code SecurityPathAdapter#unprotectedApiPaths}.
 * </ol>
 *
 * <p>Both flavours are handled the same way: wrap the request with an {@link
 * HttpServletRequestWrapper} that reports the unprefixed URI and continue the filter chain. The
 * dispatcher resolves the request against the unprefixed URI and the existing handler (resource
 * handler / cluster-scoped controller) serves it — no forward, no second dispatch.
 *
 * <p>Filter order: {@link Ordered#LOWEST_PRECEDENCE LOWEST_PRECEDENCE} - 100. Spring Security's
 * {@code FilterChainProxy} runs much earlier (default order around {@code
 * REQUEST_WRAPPER_FILTER_MAX_PRECEDENCE - 100}), so by the time we get the request the PT chain has
 * already authenticated it on the original PT-prefixed URI — exactly what we want for the cookie
 * path scope.
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

  private static final Pattern PT_CLUSTER_API =
      Pattern.compile(
          "^/physical-tenant/[^/]+(/v[12]/(license|status|topology|setup/user|rest-api\\.yaml))$");

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
      throws ServletException, IOException {
    final String unprefixed = rewriteIfMatch(request.getRequestURI());
    if (unprefixed == null) {
      chain.doFilter(request, response);
      return;
    }
    chain.doFilter(new RewrittenUriRequest(request, unprefixed), response);
  }

  private static @Nullable String rewriteIfMatch(final String uri) {
    final Matcher asset = PT_ASSET.matcher(uri);
    if (asset.matches()) {
      return "/" + asset.group(1) + "/" + asset.group(2);
    }
    final Matcher cluster = PT_CLUSTER_API.matcher(uri);
    if (cluster.matches()) {
      return cluster.group(1);
    }
    return null;
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
