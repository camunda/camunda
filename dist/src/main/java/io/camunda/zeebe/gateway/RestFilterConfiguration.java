/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import io.camunda.zeebe.gateway.impl.configuration.FilterCfg;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

public class RestFilterConfiguration implements BeanDefinitionRegistryPostProcessor {

  private final List<FilterCfg> filterCfgs;

  public RestFilterConfiguration(final List<FilterCfg> filterCfgs) {
    this.filterCfgs = filterCfgs;
  }

  @Override
  public void postProcessBeanDefinitionRegistry(final BeanDefinitionRegistry registry)
      throws BeansException {
    final AtomicInteger counter = new AtomicInteger(0);
    final FilterRepository filterRepository = new FilterRepository();
    filterRepository
        .load(filterCfgs)
        .instantiate()
        .forEach(
            customFilter -> {
              final GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
              beanDefinition.setBeanClass(FilterRegistrationBean.class);
              beanDefinition.getPropertyValues().add("filter", customFilter);
              registry.registerBeanDefinition(
                  String.valueOf(counter.getAndIncrement()), beanDefinition);
            });
  }

  @Override
  public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory)
      throws BeansException {
    // do nothing
  }
}
