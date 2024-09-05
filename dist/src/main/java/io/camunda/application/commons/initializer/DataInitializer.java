/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.initializer;

import io.camunda.application.commons.configuration.DataInitializationConfiguration.InitDataProperties;
import io.camunda.search.connect.SearchClientConnectException;
import io.camunda.service.UserServices;
import io.camunda.service.search.core.SearchQueryExecutionException;
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
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRestGatewayEnabled
public class DataInitializer implements ApplicationListener<ApplicationReadyEvent> {

  public static final int MAXIMUM_RETRY = 20;
  public static final int SLEEP_TIME_MILLIS = 100;
  private static final Logger LOGGER = LoggerFactory.getLogger("io.camunda.application");
  private final UserServices<UserRecord> userServices;
  private final PasswordEncoder passwordEncoder;
  private final SpringBrokerBridge brokerBridge;
  private final InitDataProperties initDataProperties;
  private final TaskExecutor taskExecutor;

  public DataInitializer(
      final UserServices<UserRecord> userServices,
      final PasswordEncoder passwordEncoder,
      @Autowired(required = false) final SpringBrokerBridge brokerBridge,
      final InitDataProperties initDataProperties,
      final TaskExecutor taskExecutor) {
    this.userServices = userServices;
    this.passwordEncoder = passwordEncoder;
    this.brokerBridge = brokerBridge;
    this.initDataProperties = initDataProperties;
    this.taskExecutor = taskExecutor;
  }

  @Override
  public void onApplicationEvent(final ApplicationReadyEvent event) {
    taskExecutor.execute(() -> initialize());
  }

  public void initialize() {
    try {
      if (isPrerequisitesAvailable()) {
        initDataProperties
            .getUsers()
            .forEach(
                usersRequest -> {
                  try {
                    userServices
                        .withAuthentication(RequestMapper.getAuthenticationNoTenant())
                        .findByUsername(usersRequest.getUsername())
                        .ifPresentOrElse(
                            userEntity -> {
                              LOGGER.info("User {} already exists", usersRequest.getUsername());
                            },
                            () -> createUser(usersRequest));
                  } catch (final SearchQueryExecutionException e) {
                    createUser(usersRequest);
                  }
                });
      }
    } catch (final Exception e) {
      LOGGER.error("User creation has failed.", e);
    }
  }

  private boolean isPrerequisitesAvailable() throws InterruptedException {
    if (initDataProperties.getUsers().isEmpty()) {
      LOGGER.info("No data is available for initialization");
      return false;
    }
    if (brokerBridge == null) {
      LOGGER.error("Broker is not available! data initialization has been skipped!");
      return false;
    }
    if (!isBrokerHealthy()) {
      LOGGER.error(
          "Broker is not healthy after {} retry! data initialization has been skipped!",
          MAXIMUM_RETRY);
      return false;
    }

    if (!isSearchServiceAvailable()) {
      LOGGER.error(
          "Search service is not available after {} retry! data initialization has been skipped!",
          MAXIMUM_RETRY);
      return false;
    }
    return true;
  }

  private boolean isSearchServiceAvailable() throws InterruptedException {
    var retry = 0;
    var searchAvailable = false;
    while (!searchAvailable && retry++ < MAXIMUM_RETRY) {
      try {
        userServices
            .withAuthentication(RequestMapper.getAuthenticationNoTenant())
            .findByUsername("username");
        searchAvailable = true;
      } catch (final SearchClientConnectException ignored) {
        Thread.sleep(SLEEP_TIME_MILLIS);
      } catch (final Exception ignored) {
        searchAvailable = true;
      }
    }
    return searchAvailable;
  }

  private boolean isBrokerHealthy() throws InterruptedException {
    var isBrokerHealthy = false;
    var retry = 0;
    while (!isBrokerHealthy && retry++ < MAXIMUM_RETRY) {
      isBrokerHealthy =
          brokerBridge
              .getBrokerHealthCheckService()
              .map(BrokerHealthCheckService::isBrokerHealthy)
              .orElse(false);
      Thread.sleep(SLEEP_TIME_MILLIS);
    }
    return isBrokerHealthy;
  }

  private void createUser(final UserWithPasswordRequest usersRequest) {
    try {
      userServices
          .withAuthentication(RequestMapper.getAuthenticationNoTenant())
          .createUser(
              usersRequest.getUsername(),
              usersRequest.getName() != null ? usersRequest.getName() : usersRequest.getUsername(),
              usersRequest.getEmail() != null
                  ? usersRequest.getEmail()
                  : usersRequest.getUsername(),
              passwordEncoder.encode(usersRequest.getPassword()))
          .get();
    } catch (final Exception e) {
      LOGGER.error("Creat user {} failed.", usersRequest.getUsername());
    }
  }
}
