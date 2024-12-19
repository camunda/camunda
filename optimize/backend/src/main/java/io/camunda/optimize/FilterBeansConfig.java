/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.STATIC_RESOURCE_PATH;

import io.camunda.optimize.tomcat.JavaScriptMainLicenseEnricherFilter;
import io.camunda.optimize.tomcat.NoCachingFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The name for each {@link FilterRegistrationBean} has to be set in order to avoid conflicts in
 * case multiple Filters are of the same class, e.g. {@code
 * registrationBean.setName("variableIngestionMaxRequestSizeFilter");}
 */
@Configuration
public class FilterBeansConfig {

  @Bean
  public JavaScriptMainLicenseEnricherFilter javaScriptMainLicenseEnricherFilter() {
    return new JavaScriptMainLicenseEnricherFilter();
  }

  @Bean
  public NoCachingFilter noCachingFilter() {
    return new NoCachingFilter();
  }

  @Bean
  public FilterRegistrationBean<NoCachingFilter> noCachingFilterRegistrationBean(
      final NoCachingFilter noCachingFilter) {
    final FilterRegistrationBean<NoCachingFilter> registrationBean = new FilterRegistrationBean<>();

    registrationBean.setFilter(noCachingFilter);
    registrationBean.addUrlPatterns("/*");
    registrationBean.setDispatcherTypes(
        DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ERROR, DispatcherType.ASYNC);

    registrationBean.setName("noCachingFilter");

    return registrationBean;
  }

  @Bean
  public FilterRegistrationBean<JavaScriptMainLicenseEnricherFilter>
      javaScriptMainLicenseEnricherFilterRegistrationBean(
          final JavaScriptMainLicenseEnricherFilter javaScriptMainLicenseEnricherFilter) {
    final FilterRegistrationBean<JavaScriptMainLicenseEnricherFilter> registrationBean =
        new FilterRegistrationBean<>();

    registrationBean.setFilter(javaScriptMainLicenseEnricherFilter);
    registrationBean.addUrlPatterns(STATIC_RESOURCE_PATH + "/*");
    registrationBean.setDispatcherTypes(
        DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ERROR, DispatcherType.ASYNC);
    registrationBean.setName("javaScriptMainLicenseEnricherFilter");

    return registrationBean;
  }
}
