/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.tomcat;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * Request wrapper that overrides path-related methods to return a rewritten URI. This ensures that
 * all downstream components (including Spring Security 7's {@code PathPatternRequestMatcher}) see
 * the modified path rather than the original request URI.
 *
 * <p>This is required because Spring Security 7's {@code PathPatternRequestMatcher} evaluates the
 * original request URI, so using {@link RequestDispatcher#forward} to rewrite paths no longer
 * works. See <a
 * href="https://docs.spring.io/spring-security/reference/6.5/migration-7/web.html#use-path-pattern">Spring
 * Security Migration Guide</a> for details on the path matching changes.
 *
 * <p><b>Forward dispatch handling:</b> On a {@link RequestDispatcher#forward FORWARD} dispatch,
 * Tomcat inserts its own {@code ApplicationHttpRequest} wrapper <i>below</i> this wrapper in the
 * chain, with {@code getRequestURI()} returning the forward target path (e.g., {@code
 * /index.html}). This wrapper detects forward dispatches by checking for the {@link
 * RequestDispatcher#FORWARD_REQUEST_URI} attribute and delegates to {@code super.getRequestURI()}
 * so that Tomcat's forward target URI is visible to the {@code DispatcherServlet}. Without this
 * delegation, the rewritten URI (e.g., {@code /}) would always be returned, causing {@code
 * WelcomePageHandlerMapping} to match on every forward and creating an infinite forward loop
 * ({@code StackOverflowError}).
 *
 * <p><b>Note:</b> {@code getServletPath()} is intentionally NOT overridden. Overriding it would
 * break Tomcat's internal resource resolution during forward dispatches.
 */
public class PathRewritingRequestWrapper extends HttpServletRequestWrapper {

  private final String rewrittenURI;

  public PathRewritingRequestWrapper(final HttpServletRequest request, final String rewrittenURI) {
    super(request);
    this.rewrittenURI = rewrittenURI;
  }

  @Override
  public String getRequestURI() {
    // On FORWARD dispatches, Tomcat's ApplicationHttpRequest (inserted below this wrapper in the
    // chain) sets the correct URI for the forward target. We must delegate to it rather than
    // returning our rewritten URI, otherwise the DispatcherServlet sees "/" again and re-triggers
    // WelcomePageHandlerMapping, causing an infinite forward loop (StackOverflowError).
    if (getAttribute(RequestDispatcher.FORWARD_REQUEST_URI) != null) {
      return super.getRequestURI();
    }
    return rewrittenURI;
  }

  @Override
  public StringBuffer getRequestURL() {
    // Build URL from getRequestURI() which already handles forward delegation
    final StringBuffer url = new StringBuffer();
    final String scheme = getScheme();
    final int port = getServerPort();
    url.append(scheme).append("://").append(getServerName());
    if (("http".equals(scheme) && port != 80) || ("https".equals(scheme) && port != 443)) {
      url.append(':').append(port);
    }
    url.append(getRequestURI());
    return url;
  }

  @Override
  public RequestDispatcher getRequestDispatcher(final String path) {
    // ServletRequestWrapper.getRequestDispatcher() delegates to the original (wrapped) request.
    // For relative paths (no leading '/'), the servlet container resolves them against the
    // original request's URI. Since our wrapper rewrites the URI, we must convert relative paths
    // to absolute ones based on the rewritten URI so the dispatch target is correct.
    //
    // Example: WelcomePageHandlerMapping forwards "/" to "index.html" (relative).
    // Without this fix, "index.html" resolves against the original URI "/<clusterId>/" →
    // "/<clusterId>/index.html" (wrong). With this fix, it resolves against "/" → "/index.html".
    if (path != null && !path.startsWith("/")) {
      final int lastSlash = rewrittenURI.lastIndexOf('/');
      final String absolutePath = rewrittenURI.substring(0, lastSlash + 1) + path;
      return super.getRequestDispatcher(absolutePath);
    }
    return super.getRequestDispatcher(path);
  }
}
