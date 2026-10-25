/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.protocol.impl.record.value.consent.ConsentRecord;
import java.util.concurrent.CompletableFuture;

/**
 * Sends consent commands to the engine with the caller's authentication, through the broker request
 * path of an existing physical-tenant-scoped service, so they carry the same authorization and
 * partition group as every other command.
 *
 * <p>Proof-of-concept shortcut: it lives in this package to reach that path without changing the
 * service registry. A product build adds a {@code ConsentServices} to the registry instead.
 */
public final class ConsentServices {

  private final ApiServices<?> delegate;

  public ConsentServices(final ApiServices<?> delegate) {
    this.delegate = delegate;
  }

  public CompletableFuture<ConsentRecord> send(
      final BrokerRequest<ConsentRecord> request, final CamundaAuthentication authentication) {
    return delegate.sendBrokerRequest(request, authentication);
  }
}
