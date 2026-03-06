/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.holder;

import io.camunda.auth.domain.model.CamundaAuthentication;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public class RequestContextBasedAuthenticationHolder implements CamundaAuthenticationHolder {

  private static final String ATTRIBUTE_KEY = "camunda.authentication";

  @Override
  public boolean supports() {
    return RequestContextHolder.getRequestAttributes() != null;
  }

  @Override
  public void set(final CamundaAuthentication authentication) {
    final RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes != null) {
      attributes.setAttribute(ATTRIBUTE_KEY, authentication, RequestAttributes.SCOPE_REQUEST);
    }
  }

  @Override
  public CamundaAuthentication get() {
    final RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes != null) {
      return (CamundaAuthentication)
          attributes.getAttribute(ATTRIBUTE_KEY, RequestAttributes.SCOPE_REQUEST);
    }
    return null;
  }
}
