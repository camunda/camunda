/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import io.camunda.zeebe.gateway.impl.configuration.FilterCfg;
import jakarta.servlet.Filter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestInterceptorConfiguration implements ApplicationContextAware, InitializingBean {

  private static ApplicationContext context;

  @Override
  public void afterPropertiesSet() throws Exception {

    final List<FilterCfg> filterCfgs =
        RestInterceptorConfiguration.context
            .getBean(GatewayConfiguration.class)
            .config()
            .getFilters();

    final AtomicInteger counter = new AtomicInteger(0);
    final FilterRepository filterRepository = new FilterRepository();
    filterRepository
        .load(filterCfgs)
        .instantiate()
        .forEach(
            customFilter -> {
              final FilterRegistrationBean<Filter> bean =
                  new FilterRegistrationBean<>(customFilter);
              RestInterceptorConfiguration.context
                  .getAutowireCapableBeanFactory()
                  .initializeBean(bean, String.valueOf(counter.getAndIncrement()));
            });
  }

  @Override
  public void setApplicationContext(final ApplicationContext applicationContext)
      throws BeansException {
    RestInterceptorConfiguration.context = applicationContext;
  }
}
