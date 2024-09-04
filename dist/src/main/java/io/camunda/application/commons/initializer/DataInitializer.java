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
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.client.protocol.rest.UserWithPasswordRequest;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRestGatewayEnabled
public class DataInitializer implements ApplicationListener<ApplicationReadyEvent> {
  private static final Logger LOGGER = LoggerFactory.getLogger("io.camunda.application");
  private final UserServices<UserRecord> userServices;
  private final PasswordEncoder passwordEncoder;
  private final SpringBrokerBridge brokerBridge;
  private final InitDataProperties initDataProperties;

  public DataInitializer(
      final UserServices<UserRecord> userServices,
      final PasswordEncoder passwordEncoder,
      @Autowired(required = false) final SpringBrokerBridge brokerBridge,
      final InitDataProperties initDataProperties) {
    this.userServices = userServices;
    this.passwordEncoder = passwordEncoder;
    this.brokerBridge = brokerBridge;
    this.initDataProperties = initDataProperties;
  }

  @Override
  public void onApplicationEvent(final ApplicationReadyEvent event) {
    final Authentication authentication = RequestMapper.getAuthenticationNoTenant();
    new SimpleAsyncTaskExecutor().execute(() -> initialize(authentication));
  }

  public void initialize(final Authentication authentication) {
    try {
      if (isPrerequisitesAvailable()) {
        initDataProperties
            .getUsers()
            .forEach(
                usersRequest -> {
                  userServices
                      .withAuthentication(authentication)
                      .findByUsername(usersRequest.getUsername())
                      .ifPresentOrElse(
                          userEntity -> {
                            LOGGER.info("User {} already exists", usersRequest.getUsername());
                          },
                          () -> createUser(usersRequest, authentication));
                });
      }
    } catch (final Exception e) {
      LOGGER.error("User creation has failed.", e);
    }
  }

  private boolean isPrerequisitesAvailable() throws InterruptedException {
    if (brokerBridge == null) {
      LOGGER.error("Broker is not available! data initialization has been skipped!");
      return false;
    }
    var isBrokerHealthy = false;
    while (!isBrokerHealthy) {
      isBrokerHealthy =
          brokerBridge
              .getBrokerHealthCheckService()
              .map(BrokerHealthCheckService::isBrokerHealthy)
              .orElse(false);
      Thread.sleep(10);
    }
    return true;
  }

  private void createUser(
      final UserWithPasswordRequest usersRequest, final Authentication authentication) {
    try {
      userServices
          .withAuthentication(authentication)
          .createUser(
              usersRequest.getUsername(),
              usersRequest.getName() != null ? usersRequest.getName() : usersRequest.getUsername(),
              usersRequest.getEmail() != null
                  ? usersRequest.getEmail()
                  : usersRequest.getUsername(),
              passwordEncoder.encode(usersRequest.getPassword()))
          .get();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }
}
