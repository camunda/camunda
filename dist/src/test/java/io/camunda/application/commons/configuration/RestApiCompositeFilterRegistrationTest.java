/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.GatewayBasedProperties;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.NodeInstance;
import io.camunda.zeebe.dynamic.nodeid.Version;
import io.camunda.zeebe.gateway.impl.configuration.FilterCfg;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.context.LifecycleProperties;

/**
 * The composite filter wrapping user-defined REST API filters must not reach the request path when
 * no such filters are configured — the common case. Registering it unconditionally used to put an
 * empty composite filter in front of every request, including static webapp assets.
 *
 * @see <a href="https://github.com/camunda/camunda/issues/35067">Issue #35067</a>
 */
final class RestApiCompositeFilterRegistrationTest {

  @TempDir private Path workingDirectory;

  @Test
  void shouldNotRegisterBrokerCompositeFilterWhenNoFiltersConfigured() {
    // given
    final var configuration = brokerConfiguration(List.of());

    // when
    final var registration = configuration.restApiCompositeFilter();

    // then
    assertThat(registration.isEnabled()).isFalse();
  }

  @Test
  void shouldRegisterBrokerCompositeFilterWhenFilterConfigured() {
    // given
    final var configuration = brokerConfiguration(List.of(filterCfg()));

    // when
    final var registration = configuration.restApiCompositeFilter();

    // then
    assertThat(registration.isEnabled()).isTrue();
  }

  @Test
  void shouldNotRegisterGatewayCompositeFilterWhenNoFiltersConfigured() {
    // given
    final var configuration = gatewayConfiguration(List.of());

    // when
    final var registration = configuration.restApiCompositeFilter();

    // then
    assertThat(registration.isEnabled()).isFalse();
  }

  @Test
  void shouldRegisterGatewayCompositeFilterWhenFilterConfigured() {
    // given
    final var configuration = gatewayConfiguration(List.of(filterCfg()));

    // when
    final var registration = configuration.restApiCompositeFilter();

    // then
    assertThat(registration.isEnabled()).isTrue();
  }

  private BrokerBasedConfiguration brokerConfiguration(final List<FilterCfg> filters) {
    final var properties = new BrokerBasedProperties();
    properties.getGateway().setFilters(filters);

    final var nodeIdProvider = mock(NodeIdProvider.class);
    when(nodeIdProvider.currentNodeInstance()).thenReturn(new NodeInstance(0, Version.zero()));

    return new BrokerBasedConfiguration(
        new WorkingDirectory(workingDirectory, true),
        nodeIdProvider,
        properties,
        new LifecycleProperties());
  }

  private GatewayBasedConfiguration gatewayConfiguration(final List<FilterCfg> filters) {
    final var properties = new GatewayBasedProperties();
    properties.setFilters(filters);

    return new GatewayBasedConfiguration(properties, new LifecycleProperties());
  }

  private static FilterCfg filterCfg() {
    final var cfg = new FilterCfg();
    cfg.setId("noop");
    cfg.setClassName(NoopFilter.class.getName());
    return cfg;
  }

  public static final class NoopFilter implements Filter {
    @Override
    public void doFilter(
        final ServletRequest request, final ServletResponse response, final FilterChain chain)
        throws IOException, ServletException {
      chain.doFilter(request, response);
    }
  }
}
