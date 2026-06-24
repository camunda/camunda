/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.gateway.impl.configuration.InterceptorCfg;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
public class ApiGrpcBrokerInterceptorsTest {

  private InterceptorCfg createInterceptorCfg(
      final String id, final String jarPath, final String className) {
    final var interceptorCfg = new InterceptorCfg();
    interceptorCfg.setId(id);
    interceptorCfg.setJarPath(jarPath);
    interceptorCfg.setClassName(className);
    return interceptorCfg;
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.api.grpc.interceptors.0.id=0IdNew",
        "camunda.api.grpc.interceptors.0.jar-path=0JarPathNew",
        "camunda.api.grpc.interceptors.0.class-name=0ClassNameNew",
        "camunda.api.grpc.interceptors.1.id=1IdNew",
        "camunda.api.grpc.interceptors.1.jar-path=1JarPathNew",
        "camunda.api.grpc.interceptors.1.class-name=1ClassNameNew"
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetInterceptors() {
      final var expectedInterceptorCfg0 =
          createInterceptorCfg("0IdNew", "0JarPathNew", "0ClassNameNew");
      final var expectedInterceptorCfg1 =
          createInterceptorCfg("1IdNew", "1JarPathNew", "1ClassNameNew");

      assertThat(brokerCfg.getGateway().getInterceptors())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(expectedInterceptorCfg0, expectedInterceptorCfg1);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.gateway.interceptors.0.id=0IdLegacyGateway",
        "zeebe.gateway.interceptors.0.jarPath=0JarPathLegacyGateway",
        "zeebe.gateway.interceptors.0.className=0ClassNameLegacyGateway",
        "zeebe.gateway.interceptors.1.id=1IdLegacyGateway",
        "zeebe.gateway.interceptors.1.jarPath=1JarPathLegacyGateway",
        "zeebe.gateway.interceptors.1.className=1ClassNameLegacyGateway"
      })
  class WithOnlyLegacyGatewayInterceptorsSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacyGatewayInterceptorsSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldNotSetInterceptorsFromLegacyGatewayInterceptors() {
      assertThat(brokerCfg.getGateway().getInterceptors()).isEmpty();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.gateway.interceptors.0.id=0IdLegacyBroker",
        "zeebe.broker.gateway.interceptors.0.jarPath=0JarPathLegacyBroker",
        "zeebe.broker.gateway.interceptors.0.className=0ClassNameLegacyBroker",
        "zeebe.broker.gateway.interceptors.1.id=1IdLegacyBroker",
        "zeebe.broker.gateway.interceptors.1.jarPath=1JarPathLegacyBroker",
        "zeebe.broker.gateway.interceptors.1.className=1ClassNameLegacyBroker"
      })
  class WithOnlyLegacyBrokerInterceptorsSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacyBrokerInterceptorsSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetInterceptorsFromLegacyBrokerInterceptors() {
      final var expectedInterceptorCfg0 =
          createInterceptorCfg("0IdLegacyBroker", "0JarPathLegacyBroker", "0ClassNameLegacyBroker");
      final var expectedInterceptorCfg1 =
          createInterceptorCfg("1IdLegacyBroker", "1JarPathLegacyBroker", "1ClassNameLegacyBroker");

      assertThat(brokerCfg.getGateway().getInterceptors())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(expectedInterceptorCfg0, expectedInterceptorCfg1);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.api.grpc.interceptors.0.id=0IdNew",
        "camunda.api.grpc.interceptors.0.jar-path=0JarPathNew",
        "camunda.api.grpc.interceptors.0.class-name=0ClassNameNew",
        "camunda.api.grpc.interceptors.1.id=1IdNew",
        "camunda.api.grpc.interceptors.1.jar-path=1JarPathNew",
        "camunda.api.grpc.interceptors.1.class-name=1ClassNameNew",
        // legacy gateway interceptors
        "zeebe.gateway.interceptors.0.id=0IdLegacyGateway",
        "zeebe.gateway.interceptors.0.jarPath=0JarPathLegacyGateway",
        "zeebe.gateway.interceptors.0.className=0ClassNameLegacyGateway",
        "zeebe.gateway.interceptors.1.id=1IdLegacyGateway",
        "zeebe.gateway.interceptors.1.jarPath=1JarPathLegacyGateway",
        "zeebe.gateway.interceptors.1.className=1ClassNameLegacyGateway",
        // legacy broker interceptors
        "zeebe.broker.gateway.interceptors.0.id=0IdLegacyBroker",
        "zeebe.broker.gateway.interceptors.0.jarPath=0JarPathLegacyBroker",
        "zeebe.broker.gateway.interceptors.0.className=0ClassNameLegacyBroker",
        "zeebe.broker.gateway.interceptors.1.id=1IdLegacyBroker",
        "zeebe.broker.gateway.interceptors.1.jarPath=1JarPathLegacyBroker",
        "zeebe.broker.gateway.interceptors.1.className=1ClassNameLegacyBroker",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetInterceptorsFromNew() {
      final var expectedInterceptorCfg0 =
          createInterceptorCfg("0IdNew", "0JarPathNew", "0ClassNameNew");
      final var expectedInterceptorCfg1 =
          createInterceptorCfg("1IdNew", "1JarPathNew", "1ClassNameNew");

      assertThat(brokerCfg.getGateway().getInterceptors())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(expectedInterceptorCfg0, expectedInterceptorCfg1);
    }
  }
}
