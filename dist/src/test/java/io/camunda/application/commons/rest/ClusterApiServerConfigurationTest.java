/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.authentication.clusteradmin.ClusterApiSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.mock.env.MockEnvironment;

class ClusterApiServerConfigurationTest {

  private final MockEnvironment environment =
      new MockEnvironment().withProperty("camunda.api.cluster.port", "9700");

  @Test
  void shouldRefuseDedicatedPortWithoutClusterAdminChain() {
    // given — e.g. an application mode that does not register the cluster-admin chains
    final var beanFactory = new DefaultListableBeanFactory();

    // when / then — opening the port would serve the cluster API without authentication
    assertThatThrownBy(
            () ->
                new ClusterApiServerConfiguration()
                    .clusterApiConnectorCustomizer(environment, beanFactory))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no cluster-admin security chain");
  }

  @Test
  void shouldOpenDedicatedPortWithClusterAdminChain() {
    // given
    final var beanFactory = new DefaultListableBeanFactory();
    beanFactory.registerBeanDefinition(
        ClusterApiSettings.BASIC_CHAIN_BEAN, new RootBeanDefinition(Object.class));

    // when
    final var customizer =
        new ClusterApiServerConfiguration().clusterApiConnectorCustomizer(environment, beanFactory);

    // then
    assertThat(customizer).isNotNull();
  }
}
