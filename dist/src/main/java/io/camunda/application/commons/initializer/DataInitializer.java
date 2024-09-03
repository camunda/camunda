/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.initializer;

import io.camunda.application.commons.configuration.DataInitializationConfiguration.InitDataProperties;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRestGatewayEnabled
@Profile("broker")
public class DataInitializer implements ApplicationListener<ApplicationReadyEvent> {
  private final UserServices<UserRecord> userServices;
  private final SpringBrokerBridge brokerBridge;
  private final InitDataProperties initDataProperties;

  public DataInitializer(
      final UserServices<UserRecord> userServices,
      final SpringBrokerBridge brokerBridge,
      final InitDataProperties initDataProperties) {
    this.userServices = userServices;
    this.brokerBridge = brokerBridge;
    this.initDataProperties = initDataProperties;
  }

  @Override
  public void onApplicationEvent(final ApplicationReadyEvent event) {
    initialize();
  }

  private void initialize() {
    final var isBrokerReady =
        brokerBridge
            .getBrokerHealthCheckService()
            .map(BrokerHealthCheckService::isBrokerReady)
            .orElse(false);
    if (isBrokerReady) {
      initDataProperties
          .getUsers()
          .forEach(
              usersRequest -> {
                userServices
                    .findByUsername(usersRequest.getUsername())
                    .ifPresentOrElse(
                        userEntity -> {},
                        () ->
                            userServices.createUser(
                                usersRequest.getUsername(),
                                usersRequest.getName(),
                                usersRequest.getEmail(),
                                usersRequest.getPassword()));
              });
    }
  }
}
