/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.security.oauth;

import lombok.SneakyThrows;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.camunda.optimize.service.util.configuration.security.CloudAuthConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@Order(1)
@Conditional(CCSaaSCondition.class)
public class CCSaaSPublicAPIConfigurerAdapter extends AbstractPublicAPIConfigurerAdapter {

  private final String audience;
  private final String clusterId;

  public CCSaaSPublicAPIConfigurerAdapter(final ConfigurationService configurationService) {
    super(configurationService);
    clusterId = getAuth0Configuration().getClusterId();
    audience = getAuth0Configuration().getAudience();
  }

  @Bean
  @Override
  @SneakyThrows
  public JwtDecoder jwtDecoder() {
      NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(getJwtSetUri()).build();
      OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(audience);
      OAuth2TokenValidator<Jwt> clusterIdValidator = new ClusterIdValidator(clusterId);
      OAuth2TokenValidator<Jwt> audienceAndClusterIdValidation = new DelegatingOAuth2TokenValidator<>(audienceValidator, clusterIdValidator);
      jwtDecoder.setJwtValidator(audienceAndClusterIdValidation);
      return jwtDecoder;
  }

  private CloudAuthConfiguration getAuth0Configuration() {
    return configurationService.getAuthConfiguration().getCloudAuthConfiguration();
  }
}
