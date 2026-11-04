/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.ecv.EcvApi.CommitResponse;
import io.camunda.zeebe.broker.ecv.EcvApi.EcvState;
import io.camunda.zeebe.broker.ecv.EcvManagement;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.stereotype.Component;

/**
 * POC of the ECV commit API and the ECV state read on the broker's management port. The API itself
 * is owned by APIs &amp; Process Automation and goes to the REST API of the gateway; this endpoint
 * only exercises what Distributed Systems does after the gateway (design section 5).
 *
 * <ul>
 *   <li>{@code GET /actuator/ecv}: the ECV state as the system partition leader sees it
 *   <li>{@code POST /actuator/ecv}: commit; returns when the decision is in the system partition
 * </ul>
 */
@Component
@WebEndpoint(id = "ecv")
public final class EcvEndpoint {
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private final ObjectProvider<SpringBrokerBridge> brokerBridge;

  public EcvEndpoint(final ObjectProvider<SpringBrokerBridge> brokerBridge) {
    this.brokerBridge = brokerBridge;
  }

  @ReadOperation
  public WebEndpointResponse<?> state() {
    final var ecvManagement = ecvManagement();
    if (ecvManagement.isEmpty()) {
      return unavailable();
    }
    try {
      final EcvState state =
          ecvManagement.get().state().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      return state == null
          ? new WebEndpointResponse<>(
              "No system partition leader answered; try again",
              WebEndpointResponse.STATUS_SERVICE_UNAVAILABLE)
          : new WebEndpointResponse<>(state);
    } catch (final Exception e) {
      return new WebEndpointResponse<>(
          e.getMessage(), WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    }
  }

  @WriteOperation
  public WebEndpointResponse<?> commit() {
    final var ecvManagement = ecvManagement();
    if (ecvManagement.isEmpty()) {
      return unavailable();
    }
    try {
      final CommitResponse response =
          ecvManagement.get().commit().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      final var status =
          switch (response.status()) {
            case COMMITTED, NO_COMMIT_AVAILABLE -> WebEndpointResponse.STATUS_OK;
            case REJECTED -> 409;
            case UNAVAILABLE -> WebEndpointResponse.STATUS_SERVICE_UNAVAILABLE;
          };
      return new WebEndpointResponse<>(response, status);
    } catch (final Exception e) {
      return new WebEndpointResponse<>(
          e.getMessage(), WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    }
  }

  private Optional<EcvManagement> ecvManagement() {
    return Optional.ofNullable(brokerBridge.getIfAvailable())
        .flatMap(SpringBrokerBridge::getEcvManagement);
  }

  private static WebEndpointResponse<String> unavailable() {
    return new WebEndpointResponse<>(
        "The ECV management runs only on a broker with the system partition enabled",
        WebEndpointResponse.STATUS_SERVICE_UNAVAILABLE);
  }
}
