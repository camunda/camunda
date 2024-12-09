/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.csrf;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

public class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
  private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
  private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

  @Override
  public void handle(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Supplier<CsrfToken> csrfToken) {
    xor.handle(request, response, csrfToken);
    csrfToken.get();
  }

  @Override
  public String resolveCsrfTokenValue(final HttpServletRequest request, final CsrfToken csrfToken) {
    final String headerValue = request.getHeader(csrfToken.getHeaderName());
    return (StringUtils.hasText(headerValue) ? plain : xor)
        .resolveCsrfTokenValue(request, csrfToken);
  }
}
