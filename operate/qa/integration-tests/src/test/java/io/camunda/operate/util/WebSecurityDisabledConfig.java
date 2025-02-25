/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import io.camunda.operate.OperateProfileService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;

@Profile(
    "!"
        + OperateProfileService.AUTH_PROFILE
        + " && !"
        + OperateProfileService.IDENTITY_AUTH_PROFILE
        + " && !"
        + OperateProfileService.CONSOLIDATED_AUTH)
@EnableWebSecurity
@Component("webSecurityConfig")
public class WebSecurityDisabledConfig {

  @Bean
  public SecurityFilterChain filterChain(final HttpSecurity http) throws Exception {
    return http.build();
  }
}
