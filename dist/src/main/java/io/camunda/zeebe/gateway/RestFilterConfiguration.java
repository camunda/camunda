/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import io.camunda.zeebe.gateway.impl.configuration.FilterCfg;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class RestFilterConfiguration
    implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

  private List<FilterCfg> filterCfgs;
  private Environment environment;

  @Override
  public void postProcessBeanDefinitionRegistry(final BeanDefinitionRegistry registry)
      throws BeansException {
    final Binder binder = Binder.get(environment);
    filterCfgs =
        binder
            .bind("zeebe.gateway.filters", Bindable.listOf(FilterCfg.class))
            .orElse(Collections.emptyList());
    final AtomicInteger counter = new AtomicInteger(1);
    final FilterRepository filterRepository = new FilterRepository();
    filterRepository
        .load(filterCfgs)
        .instantiate()
        .forEach(
            customFilter -> {
              final GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
              beanDefinition.setBeanClass(FilterRegistrationBean.class);
              beanDefinition
                  .getPropertyValues()
                  .add("filter", customFilter)
                  .add("order", counter.getAndIncrement());
              registry.registerBeanDefinition(
                  String.valueOf(counter.getAndIncrement()), beanDefinition);
            });
  }

  @Override
  public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory)
      throws BeansException {
    // do nothing
  }

  @Override
  public void setEnvironment(final Environment environment) {
    this.environment = environment;
  }
}
