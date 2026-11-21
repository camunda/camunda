/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;

class TomcatMaxLocaleConfigTest {

  @Test
  void shouldRemoveTheLocaleCountLimit() {
    // given
    final var config = new TomcatMaxLocaleConfig();
    final WebServerFactoryCustomizer<TomcatServletWebServerFactory> customizer =
        config.maxLocaleCountCustomizer();
    final var factory = mock(TomcatServletWebServerFactory.class);

    // when
    customizer.customize(factory);

    // then
    final var captor = ArgumentCaptor.forClass(TomcatConnectorCustomizer.class);
    verify(factory).addConnectorCustomizers(captor.capture());

    final var connector = mock(Connector.class);
    captor.getValue().customize(connector);
    // -1 disables the limit: a request with any number of Accept-Language
    // values is served instead of failing with IllegalArgumentException (#68071).
    verify(connector).setMaxLocaleCount(-1);
  }
}
