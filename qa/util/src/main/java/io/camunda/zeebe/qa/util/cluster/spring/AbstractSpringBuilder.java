/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.cluster.spring;

import io.camunda.zeebe.qa.util.cluster.spring.ContextOverrideInitializer.Bean;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.prometheus.client.CollectorRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

public abstract class AbstractSpringBuilder<
    Node, Config, Builder extends AbstractSpringBuilder<Node, Config, Builder>> {
  protected Config config;

  private final Map<String, Bean<?>> beans = new HashMap<>();
  private final Map<String, Object> properties = new HashMap<>();
  private final Config defaultConfig;

  protected AbstractSpringBuilder(final Config defaultConfig) {
    this.defaultConfig = Objects.requireNonNull(defaultConfig, "must provide a default config");
    config = defaultConfig;
  }

  public <U> Builder withBean(final String qualifier, final U bean, final Class<U> beanType) {
    beans.put(qualifier, new Bean<>(bean, beanType));
    return self();
  }

  public Builder withProperty(final String name, final Object value) {
    properties.put(name, value);
    return self();
  }

  public Builder withConfig(final Config config) {
    if (config == null) {
      return withConfig(defaultConfig);
    }

    this.config = config;
    return self();
  }

  public Builder withConfig(final Consumer<Config> modifier) {
    modifier.accept(config);
    return self();
  }

  public Node build() {
    if (!beans.containsKey("config")) {
      beans.put("config", new Bean<>(config, (Class<Config>) config.getClass()));
    }

    if (!beans.containsKey("collectorRegistry")) {
      beans.put("collectorRegistry", collectorRegistryBean());
    }

    if (!properties.containsKey("server.port")) {
      final var monitoringPort = getMonitoringPort();
      properties.put("server.port", monitoringPort);
    }

    final var builder = defaultBuilder();
    return createNode(builder);
  }

  public Config config() {
    return config;
  }

  protected abstract Node createNode(final SpringApplicationBuilder builder);

  protected SpringApplicationBuilder defaultBuilder() {
    return new SpringApplicationBuilder()
        .web(WebApplicationType.REACTIVE)
        .bannerMode(Mode.OFF)
        .lazyInitialization(true)
        .registerShutdownHook(false)
        .initializers(new ContextOverrideInitializer(beans, properties))
        .profiles("test");
  }

  protected int getMonitoringPort() {
    return SocketUtil.getNextAddress().getPort();
  }

  protected Bean<CollectorRegistry> collectorRegistryBean() {
    return new Bean<>(new RelaxedCollectorRegistry(), CollectorRegistry.class);
  }

  private Builder self() {
    return (Builder) this;
  }
}
