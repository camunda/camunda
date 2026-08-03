/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beans;

import io.camunda.cluster.SecondaryStorageReadiness;
import io.camunda.zeebe.gateway.rest.interceptor.SecondaryStorageInterceptor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!restore")
public class LegacySecondaryStorageInterceptor extends SecondaryStorageInterceptor {

  public LegacySecondaryStorageInterceptor(final SecondaryStorageReadiness readiness) {
    super(readiness);
  }
}
