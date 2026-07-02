/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Tomcat to pass encoded slashes ({@code %2F}) through to Spring MVC instead of
 * rejecting them with a 400 error. This is required to support entity IDs that contain forward
 * slashes (e.g., OIDC group IDs like {@code /myGroup} from Keycloak).
 *
 * <p>Uses {@link EncodedSolidusHandling#PASS_THROUGH} so that {@code %2F} reaches Spring MVC as-is.
 * Spring MVC path matching (configured via {@link EncodedSlashMvcConfig} with {@code
 * urlDecode=false}) then treats {@code %2FmyGroup} as a single path segment rather than a path
 * separator. After the route is matched, Spring's {@code UrlPathHelper.decodePathVariables()}
 * decodes {@code %2F} → {@code /}, so {@code @PathVariable} parameters receive {@code /myGroup}
 * (with the slash intact).
 *
 * <p>Note: {@code DECODE} mode (used in an earlier fix attempt) converted {@code %2F} → {@code /}
 * at the Tomcat level, which caused Tomcat's path normalizer to collapse {@code //myGroup} → {@code
 * myGroup}, silently stripping the leading slash before Spring MVC ever saw it.
 *
 * <p>Note: Spring Security's {@code StrictHttpFirewall} also rejects {@code %2F} independently. The
 * firewall fix lives in {@code WebSecurityConfig.encodedSlashFirewallCustomizer()} (authentication
 * module), configured via {@code WebSecurityCustomizer}.
 *
 * @see EncodedSlashMvcConfig
 * @see <a href="https://github.com/camunda/camunda/issues/45215">Issue #45215</a>
 */
@Configuration
public class TomcatEncodedSlashConfig {

  @Bean
  public WebServerFactoryCustomizer<TomcatServletWebServerFactory> encodedSlashCustomizer() {
    return factory ->
        factory.addConnectorCustomizers(
            connector ->
                connector.setEncodedSolidusHandling(
                    EncodedSolidusHandling.PASS_THROUGH.getValue()));
  }
}
