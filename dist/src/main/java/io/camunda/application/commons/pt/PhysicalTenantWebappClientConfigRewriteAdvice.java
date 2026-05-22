/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.application.commons.identity.PhysicalTenantsConfiguredCondition;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Rewrites the {@code client-config.js} response for PT-prefixed webapp requests so the embedded
 * {@code contextPath} and {@code baseName} carry the PT prefix. Operate and Tasklist publish their
 * bootstrap config from a {@code ClientConfigRestService#getClientConfig} method that hardcodes
 * {@code contextPath = servletContextPath} and {@code baseName = servletContextPath + "/<app>"};
 * the SPA feeds {@code baseName} to {@code react-router} as its basename, so without this rewrite
 * the router rejects any URL outside the unprefixed {@code /operate} (or {@code /tasklist}) prefix
 * — including the PT- prefixed entry path.
 *
 * <p>Approach: a {@link ResponseBodyAdvice} for any {@code @ResponseBody}/{@code @RestController}
 * String return on a class whose simple name is {@code ClientConfigRestService} (covers both
 * operate's and tasklist's variants). When the request URI matches {@code
 * /physical-tenant/{tenantId}/{operate|tasklist|admin}/client-config.js} we rewrite the two JSON
 * fields in place. The substitution is string-level on a JSON document Spring just serialised,
 * which is fragile in theory but safe in practice — the field names and types are fixed by {@code
 * ClientConfig} and the produced JSON has no embedded structures that could shadow the patterns.
 *
 * <p>Conditional on {@code camunda.physical-tenants.*} being configured.
 */
@ControllerAdvice
@Conditional(PhysicalTenantsConfiguredCondition.class)
public class PhysicalTenantWebappClientConfigRewriteAdvice implements ResponseBodyAdvice<String> {

  private static final Pattern PT_CLIENT_CONFIG =
      Pattern.compile("^/physical-tenant/([^/]+)/(operate|tasklist|admin)/client-config\\.js$");

  @Override
  public boolean supports(
      final MethodParameter returnType,
      final Class<? extends HttpMessageConverter<?>> converterType) {
    final var declaring = returnType.getDeclaringClass();
    final var method = returnType.getMethod();
    return method != null
        && "ClientConfigRestService".equals(declaring.getSimpleName())
        && "getClientConfig".equals(method.getName());
  }

  @Override
  public String beforeBodyWrite(
      final String body,
      final MethodParameter returnType,
      final MediaType selectedContentType,
      final Class<? extends HttpMessageConverter<?>> selectedConverterType,
      final ServerHttpRequest request,
      final ServerHttpResponse response) {
    if (body == null) {
      return null;
    }
    final Matcher m = PT_CLIENT_CONFIG.matcher(request.getURI().getPath());
    if (!m.matches()) {
      return body;
    }
    final String tenantPrefix = "/physical-tenant/" + m.group(1);
    final String app = m.group(2);
    // Operate/Tasklist's ClientConfig#asJson serialises this in a deterministic order via Jackson;
    // contextPath and baseName are both String fields with the host's servlet context path baked
    // in (empty in Spring Boot's embedded Tomcat). String-level replace on the two known field
    // values is the simplest forward path that doesn't require parsing the body as JSON.
    return body.replace("\"contextPath\":\"\"", "\"contextPath\":\"" + tenantPrefix + "\"")
        .replace(
            "\"baseName\":\"/" + app + "\"", "\"baseName\":\"" + tenantPrefix + "/" + app + "\"");
  }
}
