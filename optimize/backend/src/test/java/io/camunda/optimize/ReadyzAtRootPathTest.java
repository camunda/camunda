/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.CONTEXT_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.core.env.Environment;

/**
 * CSL mode derives a {@code /<clusterId>} servlet context path on CCSaaS, but the SaaS readiness
 * and liveness probes target {@code /api/readyz} on the main connector without that prefix.
 * Optimize rewrites that path in the Tomcat engine pipeline so those probes keep working unchanged.
 */
@ExtendWith(MockitoExtension.class)
class ReadyzAtRootPathTest {

  private static final String CONTEXT = "/cluster-1";
  private static final String READYZ = "/api/readyz";
  private static final String OTHER_ENDPOINT = "/api/ui-configuration";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

  @Mock private Environment environment;
  @Mock private ConfigurationService configurationService;
  @Mock private TomcatServletWebServerFactory factory;
  @InjectMocks private OptimizeTomcatConfig optimizeTomcatConfig;

  @Test
  void shouldServeReadyzWithAndWithoutTheContextPath() throws Exception {
    final TomcatServletWebServerFactory realFactory = new TomcatServletWebServerFactory(0);
    realFactory.setContextPath(CONTEXT);
    realFactory.addEngineValves(OptimizeTomcatConfig.readyzAtRootValve(CONTEXT));

    final WebServer server = realFactory.getWebServer(ReadyzAtRootPathTest::registerServlets);
    server.start();
    try {
      assertThat(statusOf(server.getPort(), CONTEXT + READYZ)).isEqualTo(200);
      assertThat(statusOf(server.getPort(), READYZ))
          .describedAs("the legacy probe path must keep working behind a context path")
          .isEqualTo(200);

      // Control for the assertion below: this endpoint is served under the context path, so a 404
      // at the root can only mean the request was not rewritten.
      assertThat(statusOf(server.getPort(), CONTEXT + OTHER_ENDPOINT)).isEqualTo(200);
      assertThat(statusOf(server.getPort(), OTHER_ENDPOINT))
          .describedAs("only the readiness endpoint is rewritten, this is not a prefix strip")
          .isEqualTo(404);
    } finally {
      server.stop();
    }
  }

  @Test
  void shouldRegisterTheValveInCslModeWithAContextPath() {
    givenContextPath(CONTEXT);
    givenCslEnabled(true);

    optimizeTomcatConfig.tomcatFactoryCustomizer().customize(factory);

    verify(factory).setContextPath(CONTEXT);
    verify(factory).addEngineValves(any());
  }

  @Test
  void shouldNotRegisterTheValveWhenCslIsDisabled() {
    givenContextPath(CONTEXT);
    givenCslEnabled(false);

    optimizeTomcatConfig.tomcatFactoryCustomizer().customize(factory);

    verify(factory).setContextPath(CONTEXT);
    verify(factory, never()).addEngineValves(any());
  }

  @Test
  void shouldNotRegisterTheValveWithoutAContextPath() {
    givenContextPath(null);
    when(configurationService.getContextPath()).thenReturn(Optional.empty());
    givenCslEnabled(true);

    optimizeTomcatConfig.tomcatFactoryCustomizer().customize(factory);

    verify(factory, never()).setContextPath(any());
    verify(factory, never()).addEngineValves(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  ", "/"})
  void shouldNotRegisterTheValveForARootContextPath(final String contextPath) {
    // given — the app already serves the readiness endpoint at the root, so a rewrite is pointless
    givenContextPath(contextPath);
    givenCslEnabled(true);

    optimizeTomcatConfig.tomcatFactoryCustomizer().customize(factory);

    verify(factory, never()).addEngineValves(any());
  }

  private void givenContextPath(final String contextPath) {
    lenient().when(environment.getProperty(CONTEXT_PATH)).thenReturn(contextPath);
  }

  private void givenCslEnabled(final boolean enabled) {
    lenient()
        .when(environment.getProperty("optimize.security.csl.enabled", Boolean.class, true))
        .thenReturn(enabled);
    // Keep the connector setup in customize() inert: no HTTP or HTTPS port configured.
    lenient()
        .when(environment.getProperty(EnvironmentPropertiesConstants.HTTP_PORT_KEY))
        .thenReturn(null);
    lenient()
        .when(environment.getProperty(EnvironmentPropertiesConstants.HTTPS_PORT_KEY))
        .thenReturn(null);
  }

  private static void registerServlets(final ServletContext servletContext) {
    registerOkServlet(servletContext, "readyz", READYZ);
    registerOkServlet(servletContext, "uiConfiguration", OTHER_ENDPOINT);
  }

  private static void registerOkServlet(
      final ServletContext servletContext, final String name, final String mapping) {
    servletContext
        .addServlet(
            name,
            new HttpServlet() {
              @Override
              protected void doGet(
                  final HttpServletRequest request, final HttpServletResponse response)
                  throws IOException {
                response.setStatus(200);
                response.getWriter().write("ok");
              }
            })
        .addMapping(mapping);
  }

  private static int statusOf(final int port, final String path) throws Exception {
    // Bounded so a failed server start or a hung request fails this test instead of the suite.
    final HttpClient client = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    final HttpResponse<String> response =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(REQUEST_TIMEOUT)
                .build(),
            HttpResponse.BodyHandlers.ofString());
    return response.statusCode();
  }
}
