/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security.mtls;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.zeebe.util.tls.PeerIdentityMatcher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import jakarta.servlet.DispatcherType;
import java.util.EnumSet;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.web.server.Ssl.ClientAuth;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * Adds the client certificate identity check to the REST API and web applications port when Spring
 * requests client certificates ({@code server.ssl.client-auth=want|need}).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = Type.SERVLET)
public class ClientCertificateIdentityConfiguration {

  @Bean
  public FilterRegistrationBean<ClientCertificateIdentityFilter> clientCertificateIdentityFilter(
      final UnifiedConfiguration configuration,
      final Environment environment,
      final ObjectProvider<MeterRegistry> meterRegistry) {
    final var settings = configuration.getCamunda().getApi().getRest().getClientCertificate();
    final var matcher =
        new PeerIdentityMatcher(settings.getAllowedIdentities(), settings.isCommonNameFallback());
    final var clientAuth =
        environment.getProperty("server.ssl.client-auth", ClientAuth.class, ClientAuth.NONE);
    final var sslEnabled = environment.getProperty("server.ssl.enabled", Boolean.class, true);
    final var certificatesRequested = sslEnabled && clientAuth != ClientAuth.NONE;

    if (!certificatesRequested && !settings.getAllowedIdentities().isEmpty()) {
      // fail closed: without client certificates the allow-list would silently not apply
      throw new IllegalStateException(
          "Expected server.ssl.client-auth to be 'want' or 'need' when"
              + " camunda.api.rest.client-certificate.allowed-identities is set, but it is '%s'%s"
                  .formatted(clientAuth, sslEnabled ? "" : " and TLS is disabled"));
    }

    final var registration =
        new FilterRegistrationBean<>(
            new ClientCertificateIdentityFilter(
                matcher, meterRegistry.getIfAvailable(() -> Metrics.globalRegistry)));
    registration.setName("clientCertificateIdentityFilter");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    registration.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
    registration.setEnabled(certificatesRequested);
    return registration;
  }
}
