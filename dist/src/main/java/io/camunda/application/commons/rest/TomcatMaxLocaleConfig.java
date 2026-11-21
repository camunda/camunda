/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Raises Tomcat's per-request locale limit so a request carrying more than 10 {@code
 * Accept-Language} values is served instead of failing.
 *
 * <p>Tomcat's default {@code maxLocaleCount} is 10; a request above it makes {@code
 * Request.parseLocales} throw {@code IllegalArgumentException}, the error propagates through
 * {@code RequestContextFilter}, and the gateway logs an ERROR and returns a 500 for what is a
 * client input shape the connector can simply cap. Setting the limit to -1 (no limit) restores the
 * pre-limit Tomcat behavior; requests keep working regardless of how many locales the client
 * sends.
 *
 * @see <a href="https://github.com/camunda/camunda/issues/68071">Issue #68071</a>
 */
@Configuration
public class TomcatMaxLocaleConfig {

  @Bean
  public WebServerFactoryCustomizer<TomcatServletWebServerFactory> maxLocaleCountCustomizer() {
    return factory -> factory.addConnectorCustomizers(connector -> connector.setMaxLocaleCount(-1));
  }
}
