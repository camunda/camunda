/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Tomcat to pass encoded slashes ({@code %2F}) through to the application instead of
 * rejecting them with a 400 error. This is required to support entity IDs that contain forward
 * slashes (e.g., OIDC group IDs like {@code /myGroup} from Keycloak).
 *
 * <p>Spring Boot's {@code PathPatternParser} matches on raw/encoded URI segments and only decodes
 * for {@code @PathVariable} binding, so {@code %2F} stays intact during route matching and is
 * decoded to {@code /} when bound to the Java parameter.
 *
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
