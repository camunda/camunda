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
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRestGatewayEnabled
public class DataInitializer implements ApplicationListener<ApplicationReadyEvent> {
  private static final Logger LOGGER = LoggerFactory.getLogger("io.camunda.application");
  private final UserServices<UserRecord> userServices;
  private final InitDataProperties initDataProperties;

  public DataInitializer(
      final UserServices<UserRecord> userServices, final InitDataProperties initDataProperties) {
    this.userServices = userServices;
    this.initDataProperties = initDataProperties;
  }

  @Override
  public void onApplicationEvent(final ApplicationReadyEvent event) {
    initialize();
  }

  private void initialize() {
    try {
      initDataProperties
          .getUsers()
          .forEach(
              usersRequest -> {
                userServices
                    .findByUsername(usersRequest.getUsername())
                    .ifPresentOrElse(
                        userEntity -> {
                          LOGGER.info("Default user {} already exists", usersRequest.getUsername());
                        },
                        () ->
                            userServices.createUser(
                                usersRequest.getUsername(),
                                usersRequest.getName() != null
                                    ? usersRequest.getName()
                                    : usersRequest.getUsername(),
                                usersRequest.getEmail() != null
                                    ? usersRequest.getEmail()
                                    : usersRequest.getUsername(),
                                usersRequest.getPassword()));
              });
    } catch (final Exception e) {
      LOGGER.error("Default user creation has failed.", e);
    }
  }
}
