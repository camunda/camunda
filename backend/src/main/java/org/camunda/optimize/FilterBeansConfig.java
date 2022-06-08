/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.jetty.IngestionQoSFilter;
import org.camunda.optimize.jetty.JavaScriptMainLicenseEnricherFilter;
import org.camunda.optimize.jetty.LicenseFilter;
import org.camunda.optimize.jetty.MaxRequestSizeFilter;
import org.camunda.optimize.jetty.NoCachingFilter;
import org.camunda.optimize.plugin.AuthenticationExtractorProvider;
import org.camunda.optimize.rest.security.SingleSignOnRequestFilter;
import org.camunda.optimize.service.license.LicenseManager;
import org.camunda.optimize.service.security.ApplicationAuthorizationService;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import javax.servlet.DispatcherType;
import java.util.concurrent.Callable;

import static org.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.STATIC_RESOURCE_PATH;
import static org.camunda.optimize.rest.IngestionRestService.EVENT_BATCH_SUB_PATH;
import static org.camunda.optimize.rest.IngestionRestService.INGESTION_PATH;
import static org.camunda.optimize.rest.IngestionRestService.VARIABLE_SUB_PATH;

/**
 * The name for each {@link FilterRegistrationBean} has to be set in order to avoid conflicts in case multiple
 * Filters are of the same class, e.g. {@code registrationBean.setName("variableIngestionMaxRequestSizeFilter");}
 */

@Configuration
public class FilterBeansConfig {
  @Bean
  public JavaScriptMainLicenseEnricherFilter javaScriptMainLicenseEnricherFilter() {
    return new JavaScriptMainLicenseEnricherFilter();
  }

  @Bean
  public LicenseFilter licenseFilter(LicenseManager licenseManager, ApplicationContext applicationContext) {
    return new LicenseFilter(licenseManager, applicationContext);
  }

  @Bean
  public FilterRegistrationBean<LicenseFilter> licenseFilterRegistrationBean(LicenseFilter licenseFilter) {
    FilterRegistrationBean<LicenseFilter> registrationBean
      = new FilterRegistrationBean<>();

    registrationBean.setFilter(licenseFilter);
    registrationBean.addUrlPatterns("/*");
    registrationBean.setDispatcherTypes(
      DispatcherType.REQUEST,
      DispatcherType.FORWARD,
      DispatcherType.ERROR,
      DispatcherType.ASYNC
    );
    registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);

    registrationBean.setName("licenseFilter");
    return registrationBean;
  }

  @Bean
  public SingleSignOnRequestFilter singleSignOnFilter(AuthenticationExtractorProvider authenticationExtractorProvider,
                                                      ApplicationAuthorizationService applicationAuthorizationService,
                                                      SessionService sessionService,
                                                      AuthCookieService authCookieService) {
    return new SingleSignOnRequestFilter(
      authenticationExtractorProvider,
      applicationAuthorizationService,
      sessionService,
      authCookieService
    );
  }

  @Bean
  public FilterRegistrationBean<SingleSignOnRequestFilter> singleSignOnFilterRegistrationBean(SingleSignOnRequestFilter singleSignOnRequestFilter) {
    FilterRegistrationBean<SingleSignOnRequestFilter> registrationBean
      = new FilterRegistrationBean<>();

    registrationBean.setFilter(singleSignOnRequestFilter);
    registrationBean.addUrlPatterns("/*");
    registrationBean.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);

    registrationBean.setName("singleSignOnFilter");

    return registrationBean;
  }

  @Bean
  public NoCachingFilter noCachingFilter() {
    return new NoCachingFilter();
  }

  @Bean
  public FilterRegistrationBean<NoCachingFilter> noCachingFilterRegistrationBean(NoCachingFilter noCachingFilter) {
    FilterRegistrationBean<NoCachingFilter> registrationBean
      = new FilterRegistrationBean<>();

    registrationBean.setFilter(noCachingFilter);
    registrationBean.addUrlPatterns("/*");
    registrationBean.setDispatcherTypes(
      DispatcherType.REQUEST,
      DispatcherType.FORWARD,
      DispatcherType.ERROR,
      DispatcherType.ASYNC
    );

    registrationBean.setName("noCachingFilter");

    return registrationBean;
  }

  @Bean
  public MaxRequestSizeFilter maxRequestSizeFilter(@Qualifier("optimizeMapper") ObjectMapper objectMapper,
                                                   ConfigurationService configurationService) {
    return new MaxRequestSizeFilter(
      () -> objectMapper,
      () -> configurationService.getEventIngestionConfiguration().getMaxBatchRequestBytes()
    );
  }

  @Bean
  public FilterRegistrationBean<MaxRequestSizeFilter> maxRequestSizeFilterRegistrationBean(MaxRequestSizeFilter maxRequestSizeFilter) {
    FilterRegistrationBean<MaxRequestSizeFilter> registrationBean
      = new FilterRegistrationBean<>();

    registrationBean.setFilter(maxRequestSizeFilter);
    registrationBean.addUrlPatterns(REST_API_PATH + INGESTION_PATH + EVENT_BATCH_SUB_PATH);
    registrationBean.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);

    registrationBean.setName("eventIngestionMaxRequestSizeFilter");

    return registrationBean;
  }

  @Bean
  public FilterRegistrationBean<IngestionQoSFilter> variableIngestionQoSFilterRegistrationBean(ConfigurationService configurationService) {
    return getIngestionQoSFilterRegistrationBean(
      () -> configurationService.getVariableIngestionConfiguration().getMaxRequests(),
      VARIABLE_SUB_PATH, "variableIngestionQoSFilter"
    );
  }

  @Bean
  public FilterRegistrationBean<IngestionQoSFilter> eventIngestionQoSFilterRegistrationBean(ConfigurationService configurationService) {
    return getIngestionQoSFilterRegistrationBean(
      () -> configurationService.getEventIngestionConfiguration().getMaxRequests(),
      EVENT_BATCH_SUB_PATH, "eventIngestionQoSFilter"
    );
  }

  private FilterRegistrationBean<IngestionQoSFilter> getIngestionQoSFilterRegistrationBean(Callable<Integer> provider,
                                                                                           String subPath, String name) {
    IngestionQoSFilter ingestionQoSFilter = new IngestionQoSFilter(provider);

    FilterRegistrationBean<IngestionQoSFilter> registrationBean
      = new FilterRegistrationBean<>();

    registrationBean.setFilter(ingestionQoSFilter);
    registrationBean.addUrlPatterns(REST_API_PATH + INGESTION_PATH + subPath);
    registrationBean.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
    registrationBean.setName(name);
    return registrationBean;
  }

  @Bean
  public FilterRegistrationBean<JavaScriptMainLicenseEnricherFilter> javaScriptMainLicenseEnricherFilterRegistrationBean(JavaScriptMainLicenseEnricherFilter javaScriptMainLicenseEnricherFilter) {
    FilterRegistrationBean<JavaScriptMainLicenseEnricherFilter> registrationBean
      = new FilterRegistrationBean<>();

    registrationBean.setFilter(javaScriptMainLicenseEnricherFilter);
    registrationBean.addUrlPatterns(STATIC_RESOURCE_PATH + "/*");
    registrationBean.setDispatcherTypes(
      DispatcherType.REQUEST,
      DispatcherType.FORWARD,
      DispatcherType.ERROR,
      DispatcherType.ASYNC
    );
    registrationBean.setName("javaScriptMainLicenseEnricherFilter");

    return registrationBean;
  }

  @Bean
  public FilterRegistrationBean<MaxRequestSizeFilter> variableIngestionRequestLimitFilterRegistrationBean(
    ConfigurationService configurationService, ObjectMapper objectMapper) {

    MaxRequestSizeFilter variableIngestionRequestLimitFilter = new MaxRequestSizeFilter(
      () -> objectMapper,
      () -> configurationService.getVariableIngestionConfiguration().getMaxBatchRequestBytes()
    );

    FilterRegistrationBean<MaxRequestSizeFilter> registrationBean
      = new FilterRegistrationBean<>();

    registrationBean.setFilter(variableIngestionRequestLimitFilter);
    registrationBean.addUrlPatterns(REST_API_PATH + INGESTION_PATH + VARIABLE_SUB_PATH);
    registrationBean.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);

    registrationBean.setName("variableIngestionMaxRequestSizeFilter");

    return registrationBean;
  }
}
