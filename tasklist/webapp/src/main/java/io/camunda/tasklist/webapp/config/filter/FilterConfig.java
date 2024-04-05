/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.config.filter;

import static io.camunda.tasklist.webapp.security.TasklistURIs.REST_V1_API;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FilterConfig {
  @Bean
  public FilterRegistrationBean<LoggingFilter> loggingFilter() {
    final FilterRegistrationBean<LoggingFilter> registrationBean = new FilterRegistrationBean<>();
    registrationBean.setFilter(new LoggingFilter());
    registrationBean.addUrlPatterns(REST_V1_API + "*");
    return registrationBean;
  }
}
