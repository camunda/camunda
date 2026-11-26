/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security.mtls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Verifies the REST API's client certificate identity check against a real Tomcat, configured the
 * Spring way: {@code server.ssl.client-auth} plus an SSL bundle decide whether a certificate is
 * requested and which CAs it must chain to; the filter only checks the identity. Fixtures come from
 * {@code src/test/resources/mtls/generate.sh}.
 */
final class ClientCertificateIdentityFilterTest {
  private static final String FIXTURES = "classpath:mtls/";

  private ConfigurableApplicationContext context;

  @AfterEach
  void tearDown() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  void shouldAcceptAllowedClient() throws Exception {
    // given
    start("want", "allowed-identities=worker.example.com");

    // when
    final var response = get("worker");

    // then
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(acceptedCount("worker.example.com")).isEqualTo(1);
  }

  @Test
  void shouldRejectDisallowedClientWithProblem() throws Exception {
    // given
    start("want", "allowed-identities=worker.example.com");

    // when
    final var response = get("intruder");

    // then
    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
    assertThat(response.body()).contains("identity is not allowed");
    assertThat(rejectedCount("IDENTITY_NOT_ALLOWED")).isEqualTo(1);
  }

  @Test
  void shouldLetClientWithoutCertificateThroughWhenWanted() throws Exception {
    // given - e.g. a browser on the web applications, which then logs in with OIDC
    start("want", "allowed-identities=worker.example.com");

    // when
    final var response = get(null);

    // then
    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  void shouldRejectClientWithoutCertificateAtHandshakeWhenNeeded() throws Exception {
    // given
    start("need", "allowed-identities=worker.example.com");

    // when - then
    assertThatThrownBy(() -> get(null)).isInstanceOf(IOException.class);
  }

  @Test
  void shouldRejectUntrustedClientAtHandshake() throws Exception {
    // given - chain validation stays with Tomcat and Spring's trust store
    start("want", "allowed-identities=worker.example.com");

    // when - then
    assertThatThrownBy(() -> get("untrusted")).isInstanceOf(IOException.class);
  }

  @Test
  void shouldAcceptClientsOfEveryTrustedCa() throws Exception {
    // given - a CA rotation in progress: the bundle holds both CAs
    start("need", "allowed-identities=worker.example.com");

    // when - then
    assertThat(get("worker").statusCode()).isEqualTo(200);
    assertThat(get("rotated").statusCode()).isEqualTo(200);
  }

  @Test
  void shouldMatchCommonNameOnlyWithFallback() throws Exception {
    // given - a legacy certificate without SANs, CN=worker.example.com
    start("need", "allowed-identities=worker.example.com", "common-name-fallback=true");

    // when - then
    assertThat(get("legacy").statusCode()).isEqualTo(200);
  }

  @Test
  void shouldNotMatchCommonNameWithoutFallback() throws Exception {
    // given
    start("need", "allowed-identities=worker.example.com");

    // when - then
    assertThat(get("legacy").statusCode()).isEqualTo(403);
  }

  @Test
  void shouldCheckIdentityOncePerTlsSession() throws Exception {
    // given
    start("need", "allowed-identities=worker.example.com");
    final var client = client("worker");

    // when - the client reuses its connection
    client.send(request(), BodyHandlers.ofString());
    client.send(request(), BodyHandlers.ofString());

    // then
    assertThat(acceptedCount("worker.example.com")).isEqualTo(1);
  }

  @Test
  void shouldNotStartWithAllowListButNoClientCertificates() {
    // when - then: fail closed, the allow-list would silently not apply
    assertThatThrownBy(() -> start("none", "allowed-identities=worker.example.com"))
        .rootCause()
        .hasMessageContaining("server.ssl.client-auth");
  }

  private void start(final String clientAuth, final String... clientCertificateSettings) {
    final var properties =
        new ArrayList<>(
            List.of(
                "server.port=0",
                "server.ssl.bundle=server",
                "server.ssl.client-auth=" + clientAuth,
                "spring.ssl.bundle.pem.server.keystore.certificate=" + FIXTURES + "server.crt",
                "spring.ssl.bundle.pem.server.keystore.private-key=" + FIXTURES + "server.key",
                "spring.ssl.bundle.pem.server.truststore.certificate="
                    + FIXTURES
                    + "trusted-client-cas.pem"));
    for (final var setting : clientCertificateSettings) {
      properties.add("camunda.api.rest.client-certificate." + setting);
    }

    context =
        new SpringApplicationBuilder(TestApplication.class)
            .web(WebApplicationType.SERVLET)
            .properties(properties.toArray(String[]::new))
            .run();
  }

  private HttpResponse<String> get(final @Nullable String client) throws Exception {
    return client(client).send(request(), BodyHandlers.ofString());
  }

  private HttpRequest request() {
    final var port = context.getEnvironment().getProperty("local.server.port");
    return HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/ok")).build();
  }

  private static HttpClient client(final @Nullable String identity) throws Exception {
    final var trustStore = KeyStore.getInstance("PKCS12");
    trustStore.load(null, null);
    try (final InputStream ca = resource("client-ca.crt")) {
      trustStore.setCertificateEntry(
          "server-ca", CertificateFactory.getInstance("X.509").generateCertificate(ca));
    }
    final var trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trust.init(trustStore);

    KeyManagerFactory keys = null;
    if (identity != null) {
      final var keyStore = KeyStore.getInstance("PKCS12");
      try (final InputStream p12 = resource(identity + ".p12")) {
        keyStore.load(p12, "changeit".toCharArray());
      }
      keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keys.init(keyStore, "changeit".toCharArray());
    }

    final var sslContext = SSLContext.getInstance("TLS");
    sslContext.init(keys == null ? null : keys.getKeyManagers(), trust.getTrustManagers(), null);
    return HttpClient.newBuilder().sslContext(sslContext).build();
  }

  private static InputStream resource(final String name) {
    return ClientCertificateIdentityFilterTest.class.getResourceAsStream("/mtls/" + name);
  }

  private double acceptedCount(final String identity) {
    final var counter =
        context
            .getBean(MeterRegistry.class)
            .find(ClientCertificateIdentityFilter.METRIC_PREFIX + ".accepted")
            .tag("identity", identity)
            .counter();
    return counter == null ? 0 : counter.count();
  }

  private double rejectedCount(final String reason) {
    final var counter =
        context
            .getBean(MeterRegistry.class)
            .find(ClientCertificateIdentityFilter.METRIC_PREFIX + ".rejected")
            .tag("reason", reason)
            .counter();
    return counter == null ? 0 : counter.count();
  }

  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration({
    SslAutoConfiguration.class,
    TomcatServletWebServerAutoConfiguration.class
  })
  @Import({
    UnifiedConfiguration.class,
    UnifiedConfigurationHelper.class,
    ClientCertificateIdentityConfiguration.class
  })
  static class TestApplication {

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    ServletRegistrationBean<HttpServlet> okServlet() {
      return new ServletRegistrationBean<>(
          new HttpServlet() {
            @Override
            protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
                throws IOException {
              resp.getWriter().write("ok");
            }
          },
          "/ok");
    }
  }
}
