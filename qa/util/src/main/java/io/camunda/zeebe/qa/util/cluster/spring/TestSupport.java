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
import java.util.Map;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

final class TestSupport {
  private TestSupport() {}

  static SpringApplicationBuilder defaultSpringBuilder(
      final Map<String, Bean<?>> beans, final Map<String, Object> properties) {
    if (!beans.containsKey("collectorRegistry")) {
      beans.put(
          "collectorRegistry", new Bean<>(new RelaxedCollectorRegistry(), CollectorRegistry.class));
    }

    if (!properties.containsKey("server.port")) {
      final var monitoringPort = SocketUtil.getNextAddress().getPort();
      properties.put("server.port", monitoringPort);
    }

    return new SpringApplicationBuilder()
        .web(WebApplicationType.REACTIVE)
        .bannerMode(Mode.OFF)
        .lazyInitialization(true)
        .registerShutdownHook(false)
        .initializers(new ContextOverrideInitializer(beans, properties))
        .profiles("test");
  }
}
