/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.CONTEXT_PATH;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.tomcat.ResponseSecurityHeaderFilter;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class OptimizeTomcatConfig {

  public static final String EXTERNAL_SUB_PATH = "/external";
  private static final Logger LOG = LoggerFactory.getLogger(OptimizeTomcatConfig.class);

  @Autowired private ConfigurationService configurationService;
  @Autowired private Environment environment;

  @Bean
  FilterRegistrationBean<ResponseSecurityHeaderFilter> responseHeadersInjector() {
    LOG.debug("Registering filter 'responseHeadersInjector'...");
    final ResponseSecurityHeaderFilter responseSecurityHeaderFilter =
        new ResponseSecurityHeaderFilter(configurationService);
    final FilterRegistrationBean<ResponseSecurityHeaderFilter> registrationBean =
        new FilterRegistrationBean<>();
    registrationBean.addUrlPatterns("/*");
    registrationBean.setFilter(responseSecurityHeaderFilter);
    return registrationBean;
  }

  public Optional<String> getContextPath() {
    // If the property is set by env var (the case when starting a new Optimize in ITs), this takes
    // precedence over config
    final Optional<String> contextPath = Optional.ofNullable(environment.getProperty(CONTEXT_PATH));
    if (contextPath.isEmpty()) {
      return configurationService.getContextPath();
    }
    return contextPath;
  }
}
