/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.configuration;

import io.camunda.application.commons.configuration.DataInitializationConfiguration.InitDataProperties;
import io.camunda.zeebe.client.protocol.rest.UserWithPasswordRequest;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InitDataProperties.class)
public class DataInitializationConfiguration {

  @ConfigurationProperties("camunda.init")
  public static final class InitDataProperties {
    private List<UserWithPasswordRequest> users;

    public List<UserWithPasswordRequest> getUsers() {
      return users;
    }

    public void setUsers(final List<UserWithPasswordRequest> users) {
      this.users = users;
    }
  }
}
