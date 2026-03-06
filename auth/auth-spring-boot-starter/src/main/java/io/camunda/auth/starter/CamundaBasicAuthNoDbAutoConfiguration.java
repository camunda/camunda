/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter;

import io.camunda.auth.domain.exception.BasicAuthenticationNotSupportedException;
import io.camunda.auth.domain.model.AuthenticationMethod;
import io.camunda.auth.starter.condition.ConditionalOnAuthenticationMethod;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Fail-fast auto-configuration that prevents startup when basic auth is configured without
 * secondary storage (database).
 */
@AutoConfiguration(after = CamundaAuthAutoConfiguration.class)
@ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
@ConditionalOnProperty(
    name = "camunda.auth.basic.secondary-storage-available",
    havingValue = "false",
    matchIfMissing = true)
public class CamundaBasicAuthNoDbAutoConfiguration {

  @PostConstruct
  public void failFast() {
    throw new BasicAuthenticationNotSupportedException();
  }
}
