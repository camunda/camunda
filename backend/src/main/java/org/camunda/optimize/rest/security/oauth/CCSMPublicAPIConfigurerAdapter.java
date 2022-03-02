/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.security.oauth;

import lombok.Getter;
import org.camunda.optimize.rest.security.platform.OptimizeStaticTokenDecoder;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;


@Component
@Conditional(CCSMCondition.class)
public class CCSMPublicAPIConfigurerAdapter extends AbstractPublicAPIConfigurerAdapter {
  private static final String JWT_DECODING_SET_URI_METHOD = "jwt";
  private static final String JWT_DECODING_STATIC_TOKEN_METHOD = "static";

  @Getter
  private final String jwtDecodingMethod;

  public CCSMPublicAPIConfigurerAdapter(final ConfigurationService configurationService) {
    super(configurationService);
    /* If we have configuration for a resource server, then we use it. If not, then the static token
     method will be used
     */
    if (!getJwtSetUri().isEmpty()) {
      this.jwtDecodingMethod = JWT_DECODING_SET_URI_METHOD;
    } else {
      this.jwtDecodingMethod = JWT_DECODING_STATIC_TOKEN_METHOD;
    }
  }

  @Bean
  @Override
  public JwtDecoder jwtDecoder() {
    if (this.getJwtDecodingMethod().equals(JWT_DECODING_STATIC_TOKEN_METHOD)) {
      return new OptimizeStaticTokenDecoder(configurationService);
    } else {
      return NimbusJwtDecoder.withJwkSetUri(getJwtSetUri()).build();
    }
  }
}
