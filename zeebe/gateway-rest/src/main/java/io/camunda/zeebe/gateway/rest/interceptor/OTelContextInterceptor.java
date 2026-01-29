/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.interceptor;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/** Interceptor that enriches OpenTelemetry spans by adding camunda related attributes. */
@Component
public class OTelContextInterceptor implements HandlerInterceptor {

  public static final String CAMUNDA_GATEWAY_REST_METHOD = "camunda.gateway.rest.method";

  @Override
  public boolean preHandle(
      final HttpServletRequest request, final HttpServletResponse response, final Object handler) {

    if (handler instanceof final HandlerMethod handlerMethod) {

      final Span span = Span.current();
      if (!span.getSpanContext().isValid()) {
        return true;
      }

      // Add handler method name as span attribute
      span.setAttribute(CAMUNDA_GATEWAY_REST_METHOD, handlerMethod.getMethod().getName());

      addRequestParameters(request, handlerMethod, span);
    }

    return true;
  }

  private void addRequestParameters(
      final HttpServletRequest request, final HandlerMethod handlerMethod, final Span span) {

    final Map<String, String> pathVariables =
        (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

    if (pathVariables == null || pathVariables.isEmpty()) {
      return;
    }

    span.setAttribute("camunda.process.instance.key", pathVariables.get("processInstanceKey"));
    span.setAttribute("camunda.process.definition.key", pathVariables.get("processDefinitionKey"));
  }
}
