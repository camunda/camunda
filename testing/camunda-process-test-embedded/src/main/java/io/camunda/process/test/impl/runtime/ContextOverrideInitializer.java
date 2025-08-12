/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.runtime;

import java.util.Map;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * Context initializers are called before beans are resolved/autowired, and allows us to prepare the
 * context, so to speak. That way, we can pre-register some beans so they will be autowired first,
 * e.g. programmatic configuration.
 */
public final class ContextOverrideInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final Map<String, Bean<?>> beans;
  private final Map<String, Object> properties;

  public ContextOverrideInitializer(
      final Map<String, Bean<?>> beans, final Map<String, Object> properties) {
    this.beans = beans;
    this.properties = properties;
  }

  @Override
  public void initialize(final ConfigurableApplicationContext applicationContext) {
    final var environment = applicationContext.getEnvironment();
    final var sources = environment.getPropertySources();
    final var beanFactory = applicationContext.getBeanFactory();

    beans.forEach((qualifier, bean) -> overrideBean(beanFactory, qualifier, bean.value, bean.type));

    sources.addFirst(new MapPropertySource("test properties", properties));
  }

  private void overrideBean(
      final ConfigurableListableBeanFactory beanFactory,
      final String qualifier,
      final Object object,
      final Class<?> type) {
    if (object == null) {
      return;
    }

    beanFactory.registerResolvableDependency(type, object);
    beanFactory.registerSingleton(qualifier, object);
  }

  public record Bean<T>(T value, Class<T> type) {}
}
