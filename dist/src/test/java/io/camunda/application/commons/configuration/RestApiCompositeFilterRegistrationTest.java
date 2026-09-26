/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.configuration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.GatewayBasedProperties;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.NodeInstance;
import io.camunda.zeebe.dynamic.nodeid.Version;
import io.camunda.zeebe.gateway.impl.configuration.FilterCfg;
import io.camunda.zeebe.gateway.rest.impl.filters.FilterLoadException;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.context.LifecycleProperties;

/**
 * The composite filter wrapping user-defined REST API filters must not reach the request path when
 * no such filters are configured — the common case. Registering it unconditionally used to put an
 * empty composite filter in front of every request, including static webapp assets.
 *
 * <p>A deployment that does configure filters must keep seeing them on every request: the
 * registration sets no URL pattern of its own and so falls back to {@code FilterRegistrationBean}'s
 * default {@code /*} mapping, matching what Spring Boot applied when the composite filter was still
 * a plain {@code Filter} bean. The tests below drive {@code onStartup} against the servlet context
 * rather than reading {@code isEnabled()}, so both the mapping and its absence are observed the way
 * the container sees them.
 *
 * <p>A filter that cannot be loaded is not silently skipped: {@code FilterRepository.load(List)}
 * rethrows, so the registration never reaches the "no filters" branch by way of a failed load. The
 * disabled-registration approach is therefore chosen for its own sake rather than to absorb load
 * failures, and the two cases below pin that startup still fails loudly.
 *
 * @see <a href="https://github.com/camunda/camunda/issues/35067">Issue #35067</a>
 */
final class RestApiCompositeFilterRegistrationTest {

  /** {@code AbstractFilterRegistrationBean}'s fallback when no URL pattern is set. */
  private static final String DEFAULT_URL_MAPPING = "/*";

  @TempDir private Path workingDirectory;

  @Test
  void shouldNotRegisterBrokerCompositeFilterWhenNoFiltersConfigured() throws ServletException {
    // given
    final var registration = brokerConfiguration(List.of()).restApiCompositeFilter();
    final var servletContext = mock(ServletContext.class);

    // when
    registration.onStartup(servletContext);

    // then
    verify(servletContext, never()).addFilter(anyString(), any(Filter.class));
  }

  @Test
  void shouldMapBrokerCompositeFilterToAllRequestsWhenFilterConfigured() throws ServletException {
    // given
    final var registration = brokerConfiguration(List.of(filterCfg())).restApiCompositeFilter();
    final var servletContext = mock(ServletContext.class);
    final var dynamic = mock(FilterRegistration.Dynamic.class);
    when(servletContext.addFilter(anyString(), any(Filter.class))).thenReturn(dynamic);

    // when
    registration.onStartup(servletContext);

    // then
    verify(dynamic)
        .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, DEFAULT_URL_MAPPING);
  }

  @Test
  void shouldNotRegisterGatewayCompositeFilterWhenNoFiltersConfigured() throws ServletException {
    // given
    final var registration = gatewayConfiguration(List.of()).restApiCompositeFilter();
    final var servletContext = mock(ServletContext.class);

    // when
    registration.onStartup(servletContext);

    // then
    verify(servletContext, never()).addFilter(anyString(), any(Filter.class));
  }

  @Test
  void shouldMapGatewayCompositeFilterToAllRequestsWhenFilterConfigured() throws ServletException {
    // given
    final var registration = gatewayConfiguration(List.of(filterCfg())).restApiCompositeFilter();
    final var servletContext = mock(ServletContext.class);
    final var dynamic = mock(FilterRegistration.Dynamic.class);
    when(servletContext.addFilter(anyString(), any(Filter.class))).thenReturn(dynamic);

    // when
    registration.onStartup(servletContext);

    // then
    verify(dynamic)
        .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, DEFAULT_URL_MAPPING);
  }

  @Test
  void shouldFailFastWhenBrokerFilterCannotBeLoaded() {
    // given
    final var configuration = brokerConfiguration(List.of(unloadableFilterCfg()));

    // when / then
    assertThatThrownBy(configuration::restApiCompositeFilter)
        .isInstanceOf(FilterLoadException.class)
        .hasMessageContaining("cannot load specified class");
  }

  @Test
  void shouldFailFastWhenGatewayFilterCannotBeLoaded() {
    // given
    final var configuration = gatewayConfiguration(List.of(unloadableFilterCfg()));

    // when / then
    assertThatThrownBy(configuration::restApiCompositeFilter)
        .isInstanceOf(FilterLoadException.class)
        .hasMessageContaining("cannot load specified class");
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

  private static FilterCfg unloadableFilterCfg() {
    final var cfg = new FilterCfg();
    cfg.setId("missing");
    cfg.setClassName("io.camunda.does.not.Exist");
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
