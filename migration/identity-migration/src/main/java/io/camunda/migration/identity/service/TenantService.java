/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.service;

import io.camunda.search.entities.TenantEntity;
import io.camunda.zeebe.client.ZeebeClient;
import java.util.Collections;
import org.springframework.stereotype.Service;

@Service
public class TenantService {

  private final ZeebeClient zeebeClient;

  public TenantService(final ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
  }

  public TenantEntity create(final String tenantId, final String tenantName) {
    final var create =
        zeebeClient.newCreateTenantCommand().name(tenantName).tenantId(tenantId).send().join();
    return new TenantEntity(create.getTenantKey(), tenantId, tenantName, Collections.emptySet());
  }
}
