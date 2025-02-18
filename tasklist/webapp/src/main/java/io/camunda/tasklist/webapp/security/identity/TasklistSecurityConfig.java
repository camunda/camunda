/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.identity;

import io.camunda.authentication.service.BasicCamundaUserService;
import io.camunda.authentication.service.CamundaUserService;
import io.camunda.authentication.service.OidcCamundaUserService;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for providing security-related beans required by the service layer.
 *
 * <p>This class serves to replace the Bean declarations previously found in
 * TasklistSecurityStubsConfiguration.
 *
 * <p>Currently, there is no unified configuration for security-related services in the service
 * layer. To avoid confusion with stub implementations, this class was created to separate real,
 * fully implemented beans from stubbed or placeholder configurations.
 */
@Configuration
public class TasklistSecurityConfig {
  @Autowired private SecurityConfiguration securityConfiguration;

  @Bean
  public IdentityAuthorizationService identityAuthorizationService() {
    return new IdentityAuthorizationServiceImpl();
  }

  @Bean
  public CamundaUserService camundaUserService() {
    if (securityConfiguration.getAuthentication().getMethod() == AuthenticationMethod.BASIC) {
      return new BasicCamundaUserService();
    } else {
      return new OidcCamundaUserService();
    }
  }
}
