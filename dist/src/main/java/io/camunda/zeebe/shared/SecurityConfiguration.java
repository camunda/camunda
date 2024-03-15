/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;

@Configuration
public class SecurityConfiguration {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public SecurityWebFilterChain filterChain(ServerHttpSecurity http) throws Exception {
    http.securityMatcher(new PathPatternParserServerWebExchangeMatcher("/api/v1/**"))
        .csrf(CsrfSpec::disable)
        .authorizeExchange((authz) -> authz.anyExchange().permitAll());
    return http.build();
  }
}
