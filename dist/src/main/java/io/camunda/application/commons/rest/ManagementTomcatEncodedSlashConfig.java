/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Applies the same {@link EncodedSolidusHandling#DECODE} configuration to the management server's
 * Tomcat connector that {@link TomcatEncodedSlashConfig} applies to the main server. Required so
 * that zone-aware broker IDs containing {@code /} (e.g. {@code zoneA/0}), sent as {@code %2F} in
 * actuator paths, are accepted rather than rejected with 400.
 */
@ManagementContextConfiguration(value = ManagementContextType.ANY, proxyBeanMethods = false)
public class ManagementTomcatEncodedSlashConfig {

  @Bean
  public WebServerFactoryCustomizer<TomcatServletWebServerFactory>
      managementEncodedSlashCustomizer() {
    return factory ->
        factory.addConnectorCustomizers(
            connector ->
                connector.setEncodedSolidusHandling(
                    EncodedSolidusHandling.PASS_THROUGH.getValue()));
  }
}
