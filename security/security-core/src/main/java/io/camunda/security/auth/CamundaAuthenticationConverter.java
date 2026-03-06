/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import io.camunda.auth.domain.model.CamundaAuthentication;

public interface CamundaAuthenticationConverter<T> {

  boolean supports(final T authentication);

  CamundaAuthentication convert(final T authentication);
}
