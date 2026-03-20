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
import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;

class TomcatEncodedSlashConfigTest {

  @Test
  void shouldConfigurePassthroughForEncodedSlashes() {
    // given
    final var config = new TomcatEncodedSlashConfig();
    final WebServerFactoryCustomizer<TomcatServletWebServerFactory> customizer =
        config.encodedSlashCustomizer();
    final var factory = mock(TomcatServletWebServerFactory.class);

    // when
    customizer.customize(factory);

    // then
    final var captor = ArgumentCaptor.forClass(TomcatConnectorCustomizer.class);
    verify(factory).addConnectorCustomizers(captor.capture());

    final var connector = mock(Connector.class);
    captor.getValue().customize(connector);
    verify(connector).setEncodedSolidusHandling(EncodedSolidusHandling.PASS_THROUGH.getValue());
  }
}
