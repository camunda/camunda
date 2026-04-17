/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import io.camunda.gateway.protocol.model.ClockPinRequest;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.ClockServices;
import io.camunda.zeebe.gateway.rest.controller.generated.ClockServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultClockServiceAdapter implements ClockServiceAdapter {

  private final ClockServices clockServices;

  public DefaultClockServiceAdapter(final ClockServices clockServices) {
    this.clockServices = clockServices;
  }

  @Override
  public ResponseEntity<Void> pinClock(
      final ClockPinRequest clockPinRequest, final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(
        () -> clockServices.pinClock(clockPinRequest.getTimestamp(), authentication));
  }

  @Override
  public ResponseEntity<Void> resetClock(final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(() -> clockServices.resetClock(authentication));
  }
}
