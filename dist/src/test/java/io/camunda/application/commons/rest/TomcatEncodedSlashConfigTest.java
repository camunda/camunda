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

class TomcatEncodedSlashConfigTest {

  @Test
  void shouldConfigureDecodeForEncodedSlashes() {
    // given
    final var config = new TomcatEncodedSlashConfig();
    final var customizer = config.encodedSlashConnectorCustomizer();
    final var connector = mock(Connector.class);

    // when
    customizer.customize(connector);

    // then
    verify(connector).setEncodedSolidusHandling(EncodedSolidusHandling.DECODE.getValue());
  }
}
