/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.ccsm;

import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import jakarta.servlet.RequestDispatcher;
import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;

@Component
@Conditional(CCSMCondition.class)
public class CCSMErrorAttributes extends DefaultErrorAttributes {

  @Override
  public Map<String, Object> getErrorAttributes(
      final WebRequest webRequest, final ErrorAttributeOptions options) {
    final Map<String, Object> errorAttributes = super.getErrorAttributes(webRequest, options);
    if (isClientError(webRequest)) {
      final Object errorMessage =
          webRequest.getAttribute(RequestDispatcher.ERROR_MESSAGE, RequestAttributes.SCOPE_REQUEST);
      if (errorMessage instanceof final String message && !message.isBlank()) {
        errorAttributes.put("message", message);
      }
    }
    return errorAttributes;
  }

  private boolean isClientError(final WebRequest webRequest) {
    final Object status =
        webRequest.getAttribute(
            RequestDispatcher.ERROR_STATUS_CODE, RequestAttributes.SCOPE_REQUEST);
    if (status instanceof final Integer intStatus) {
      return intStatus >= 400 && intStatus < 500;
    }
    return false;
  }
}
