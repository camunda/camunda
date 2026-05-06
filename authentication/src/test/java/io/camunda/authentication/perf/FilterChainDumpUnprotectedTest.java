/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.perf;

import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the unprotected-API security configuration ({@code unprotected-api=true}) and prints every
 * {@link org.springframework.security.web.SecurityFilterChain} bound to the {@link
 * FilterChainProxy}.
 *
 * <p>Pair with {@link FilterChainDumpProtectedTest}: the diff between the two outputs is the filter
 * set that runs only when authentication is enabled — the candidate auth-specific cost surface.
 */
@SuppressWarnings({"SpringBootApplicationProperties", "WrongPropertyKeyValueDelimiter"})
@SpringBootTest(
    classes = {WebSecurityConfigTestContext.class, WebSecurityConfig.class},
    properties = {
      "camunda.security.authentication.unprotected-api=true",
      "camunda.security.csrf.enabled=false",
    })
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@ActiveProfiles("consolidated-auth")
@Disabled("Run manually to capture filter-chain composition; pair with the protected variant.")
public class FilterChainDumpUnprotectedTest {

  @Autowired private FilterChainProxy filterChainProxy;

  @Test
  void dumpFilterChains() {
    FilterChainDump.dump("UNPROTECTED (unprotected-api=true)", filterChainProxy);
  }
}
