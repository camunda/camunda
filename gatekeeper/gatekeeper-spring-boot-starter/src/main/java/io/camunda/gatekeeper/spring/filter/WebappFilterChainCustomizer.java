/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.filter;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * Extension point for consuming applications to customize gatekeeper's webapp security filter
 * chains. Implementations are discovered via {@link
 * org.springframework.beans.factory.ObjectProvider} and applied to both OIDC and basic auth webapp
 * chains.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @Bean
 * public WebappFilterChainCustomizer webComponentFilter(...) {
 *     return http -> http.addFilterAfter(
 *         new MyAuthorizationFilter(...), AuthorizationFilter.class);
 * }
 * }</pre>
 */
@FunctionalInterface
public interface WebappFilterChainCustomizer {
  void customize(HttpSecurity http) throws Exception;
}
