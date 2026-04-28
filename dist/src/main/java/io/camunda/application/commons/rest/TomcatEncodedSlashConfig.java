/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Tomcat to decode encoded slashes ({@code %2F}) instead of rejecting them with a 400
 * error. This is required to support entity IDs that contain forward slashes (e.g., OIDC group IDs
 * like {@code /myGroup} from Keycloak).
 *
 * <p>Uses {@link EncodedSolidusHandling#DECODE} rather than {@code PASS_THROUGH} because Tomcat
 * 10.1+ rejects {@code %2F} during path normalization even in {@code PASS_THROUGH} mode. {@code
 * DECODE} converts {@code %2F} → {@code /} at the connector level, before the normalization check.
 *
 * <p>Note: Spring Security's {@code StrictHttpFirewall} also rejects {@code %2F} independently. The
 * firewall fix lives in {@code WebSecurityConfig.encodedSlashFirewallCustomizer()} (authentication
 * module), configured via {@code WebSecurityCustomizer}.
 *
 * @see <a href="https://github.com/camunda/camunda/issues/45215">Issue #45215</a>
 */
@Configuration
public class TomcatEncodedSlashConfig {

  @Bean
  public TomcatConnectorCustomizer encodedSlashConnectorCustomizer() {
    return connector ->
        connector.setEncodedSolidusHandling(EncodedSolidusHandling.DECODE.getValue());
  }
}
