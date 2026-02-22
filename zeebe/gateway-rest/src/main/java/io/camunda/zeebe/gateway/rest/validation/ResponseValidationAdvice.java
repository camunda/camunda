/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Optional response validation for the Gateway REST API.
 *
 * <p>When enabled via {@code camunda.rest.response-validation.enabled=true}, this advice validates
 * every response body against the bean validation constraints generated from the OpenAPI
 * specification. This catches specification violations at runtime (e.g., required fields being
 * null, invalid patterns, etc.).
 *
 * <p>This is intended for development and testing only. The validation is driven purely by the
 * specification — the constraint annotations on the generated DTOs come from the OpenAPI spec and
 * require no manual code.
 *
 * <p>When disabled (default), this bean is not created at all, resulting in zero performance
 * impact.
 */
@ControllerAdvice(annotations = RestController.class)
@ConditionalOnProperty(
    name = "camunda.rest.response-validation.enabled",
    havingValue = "true",
    matchIfMissing = false)
public class ResponseValidationAdvice implements ResponseBodyAdvice<Object> {

  private static final Logger LOG = LoggerFactory.getLogger(ResponseValidationAdvice.class);

  private final Validator validator;

  public ResponseValidationAdvice(final Validator validator) {
    this.validator = validator;
    LOG.warn(
        "Gateway response validation is ENABLED. All API responses will be validated against the "
            + "specification. This is intended for development and testing only.");
  }

  @Override
  public boolean supports(
      final MethodParameter returnType,
      final Class<? extends HttpMessageConverter<?>> converterType) {
    // Validate all responses from REST controllers
    return true;
  }

  @Override
  public Object beforeBodyWrite(
      final Object body,
      final MethodParameter returnType,
      final MediaType selectedContentType,
      final Class<? extends HttpMessageConverter<?>> selectedConverterType,
      final ServerHttpRequest request,
      final ServerHttpResponse response) {

    if (body == null) {
      return null;
    }

    // Skip validation for error responses (ProblemDetail) to avoid infinite recursion:
    // if validation fails → exception handler returns ProblemDetail → advice validates it → fails
    if (body instanceof ProblemDetail) {
      return body;
    }

    final Set<ConstraintViolation<Object>> violations = validator.validate(body);
    if (!violations.isEmpty()) {
      LOG.error(
          "Response validation failed for {} {} — {} violation(s) detected: {}",
          request.getMethod(),
          request.getURI().getPath(),
          violations.size(),
          violations);
      throw new ResponseValidationException(violations);
    }

    return body;
  }
}
