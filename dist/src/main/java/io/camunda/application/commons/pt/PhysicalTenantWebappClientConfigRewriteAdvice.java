/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Prefixes the {@code contextPath} and {@code baseName} fields of the served {@code
 * client-config.js} for physical-tenant-prefixed webapp requests so the runtime SPA config reports
 * PT-prefixed paths.
 *
 * <p>Operate and Tasklist publish their bootstrap config from {@code getClientConfig} on {@link
 * io.camunda.operate.webapp.rest.ClientConfigRestService} and {@link
 * io.camunda.tasklist.webapp.rest.ClientConfigRestService} respectively, returning a {@code
 * window.clientConfig = {...};} document with {@code "contextPath":""} and {@code
 * "baseName":"/<app>"}. The SPA feeds {@code baseName} to react-router as its basename; without
 * this rewrite the router rejects any URL outside the unprefixed {@code /operate} (or {@code
 * /tasklist}) space — including the PT-prefixed entry path.
 *
 * <p>Approach: a string-level rewrite of the two known JSON fields on the body Spring just
 * serialised. The field names and value types are fixed by {@code ClientConfig}, so capturing the
 * current value and re-emitting it with the prefix is safe; it deliberately avoids re-parsing the
 * body as JSON.
 *
 * <p>Active only for PT-prefixed requests ({@link
 * PhysicalTenantContext#getPhysicalTenantId(jakarta.servlet.http.HttpServletRequest)} non-null);
 * cluster requests are passed through unchanged.
 *
 * <p>Admin's client config ({@code AdminClientConfigController#getClientConfig}, {@code
 * /admin/config.js}) emits neither {@code contextPath} nor {@code baseName} and is therefore out of
 * scope here.
 */
@NullMarked
@ControllerAdvice
public class PhysicalTenantWebappClientConfigRewriteAdvice implements ResponseBodyAdvice<String> {

  private static final Set<Class<?>> CLIENT_CONFIG_SERVICES =
      Set.of(
          io.camunda.operate.webapp.rest.ClientConfigRestService.class,
          io.camunda.tasklist.webapp.rest.ClientConfigRestService.class);
  private static final String GET_CLIENT_CONFIG = "getClientConfig";

  private static final Pattern CONTEXT_PATH = Pattern.compile("(\"contextPath\":\")([^\"]*)(\")");
  private static final Pattern BASE_NAME = Pattern.compile("(\"baseName\":\")([^\"]*)(\")");

  @Override
  public boolean supports(
      final MethodParameter returnType,
      final Class<? extends HttpMessageConverter<?>> converterType) {
    final var method = returnType.getMethod();
    return method != null
        && CLIENT_CONFIG_SERVICES.contains(returnType.getDeclaringClass())
        && GET_CLIENT_CONFIG.equals(method.getName());
  }

  @Override
  public @Nullable String beforeBodyWrite(
      final @Nullable String body,
      final MethodParameter returnType,
      final MediaType selectedContentType,
      final Class<? extends HttpMessageConverter<?>> selectedConverterType,
      final ServerHttpRequest request,
      final ServerHttpResponse response) {
    if (body == null || !(request instanceof final ServletServerHttpRequest servletRequest)) {
      return body;
    }
    final String physicalTenantId =
        PhysicalTenantContext.getPhysicalTenantId(servletRequest.getServletRequest());
    if (physicalTenantId == null) {
      return body;
    }
    final String prefix = PhysicalTenantContext.PHYSICAL_TENANTS_PATH_SEGMENT + physicalTenantId;
    return prefixField(prefixField(body, CONTEXT_PATH, prefix), BASE_NAME, prefix);
  }

  private static String prefixField(final String body, final Pattern field, final String prefix) {
    final Matcher matcher = field.matcher(body);
    if (!matcher.find()) {
      return body;
    }
    // Re-emit the matched field with the captured value verbatim, prefix-prepended.
    return matcher.replaceFirst(
        Matcher.quoteReplacement(matcher.group(1) + prefix + matcher.group(2) + matcher.group(3)));
  }
}
