/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.tomcat.filter.support;

import static java.util.Objects.requireNonNull;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import io.camunda.optimize.Main;
import io.camunda.optimize.tomcat.CCSMRequestAdjustmentFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * Shared base for CCSM filter-chain integration tests.
 *
 * <p>Boots the full Spring Boot application (Tomcat + all filters + Spring Security) but replaces
 * every infrastructure bean that requires a live database, Zeebe, or external auth server with a
 * lightweight mock. This gives tests a real HTTP pipeline — real filter ordering, real Security
 * matchers — without any external dependencies.
 *
 * <p>Subclasses must activate the {@code ccsm} profile via {@code @ActiveProfiles("ccsm")}. For
 * SaaS (cloud) mode tests, see {@link io.camunda.optimize.tomcat.SaasFilterChainIT}, which is
 * self-contained and does not extend this class.
 */
@SpringBootTest(
    classes = {Main.class},
    properties = {
      "optimize.integration-tests=true",
      "spring.task.scheduling.enabled=false",
      "optimize.import.enabled=false",
      "spring.main.allow-bean-definition-overriding=true",
    })
@Tag("ccsm-test")
public abstract class FilterIntegrationTestBase {

  @Autowired protected WebApplicationContext wac;
  protected MockMvc mvc;

  @Autowired
  protected FilterRegistrationBean<?> responseHeadersInjector; // ResponseSecurityHeaderFilter

  @Autowired protected FilterRegistrationBean<?> urlRedirector; // URLRedirectFilter

  @Autowired protected FilterRegistrationBean<?> responseTimezoneFilter; // ResponseTimezoneFilter

  @Autowired protected FilterRegistrationBean<?> noCachingFilterRegistrationBean; // NoCachingFilter

  @Autowired
  protected FilterRegistrationBean<CCSMRequestAdjustmentFilter>
      ccsmRequestAdjuster; // CCSMRequestAdjustmentFilter (CCSM only)

  @BeforeEach
  protected void setUpMockMvc() {
    final FilterChainProxy securityChain =
        wac.getBean("springSecurityFilterChain", FilterChainProxy.class);

    mvc =
        webAppContextSetup(wac)
            .addFilters(
                requireNonNull(
                    responseHeadersInjector
                        .getFilter()), // ResponseSecurityHeaderFilter (MAX_VALUE-5)
                requireNonNull(
                    responseTimezoneFilter
                        .getFilter()), //  ResponseTimezoneFilter       (MAX_VALUE-5)
                requireNonNull(
                    urlRedirector
                        .getFilter()), //           URLRedirectFilter             (MAX_VALUE-5)
                requireNonNull(
                    noCachingFilterRegistrationBean
                        .getFilter()), // NoCachingFilter      (MAX_VALUE-5)
                requireNonNull(
                    ccsmRequestAdjuster
                        .getFilter()), //    CCSMRequestAdjustmentFilter   (MAX_VALUE-5)
                securityChain) //                                       Spring Security
            //  (MAX_VALUE) — LAST
            .build();
  }
}
