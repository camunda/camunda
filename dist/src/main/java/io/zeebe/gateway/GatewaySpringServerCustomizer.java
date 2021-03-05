/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway;

import static java.net.InetAddress.getByName;

import io.zeebe.gateway.impl.configuration.GatewayCfg;
import io.zeebe.util.exception.UncheckedExecutionException;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.stereotype.Component;

@Component
public class GatewaySpringServerCustomizer
    implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

  @Autowired GatewayCfg gatewayCfg;

  @Override
  public void customize(ConfigurableServletWebServerFactory server) {
    if (!gatewayCfg.isInitialized()) {
      // trigger application of defaults so that the monitoring config no longer has null values
      gatewayCfg.init();
    }
    final var monitoringApiCfg = gatewayCfg.getMonitoring();

    try {
      server.setAddress(getByName(monitoringApiCfg.getHost()));
    } catch (UnknownHostException e) {
      throw new UncheckedExecutionException(e.getLocalizedMessage(), e);
    }
    server.setPort(monitoringApiCfg.getPort());
  }
}
