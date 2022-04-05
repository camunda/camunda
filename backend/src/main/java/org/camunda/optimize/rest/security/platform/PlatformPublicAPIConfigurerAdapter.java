/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.security.platform;

import lombok.Getter;
import org.camunda.optimize.rest.security.oauth.AbstractPublicAPIConfigurerAdapter;
import org.camunda.optimize.rest.security.oauth.AudienceValidator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.rest.IngestionRestService.EVENT_BATCH_SUB_PATH;
import static org.camunda.optimize.rest.IngestionRestService.INGESTION_PATH;
import static org.camunda.optimize.rest.IngestionRestService.VARIABLE_SUB_PATH;

@Component
@Conditional(CamundaPlatformCondition.class)
public class PlatformPublicAPIConfigurerAdapter extends AbstractPublicAPIConfigurerAdapter {
  private static final String JWT_DECODING_SET_URI_METHOD = "jwt";
  private static final String JWT_DECODING_STATIC_TOKEN_METHOD = "static";

  @Getter
  private final String jwtDecodingMethod;

  public PlatformPublicAPIConfigurerAdapter(final ConfigurationService configurationService) {
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
      return createJwtDecoderWithAudience();
    }
  }

  private JwtDecoder createJwtDecoderWithAudience() {
    NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(getJwtSetUri()).build();
    OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(getAudienceFromConfiguration());
    jwtDecoder.setJwtValidator(audienceValidator);
    return jwtDecoder;
  }

  @Override
  public void configure(HttpSecurity http) throws Exception {
    http
      .requestMatchers()
      // Public APIs allowed in platform
      .antMatchers(PUBLIC_API_PATH,
                   createApiPath(INGESTION_PATH, EVENT_BATCH_SUB_PATH),
                   createApiPath(INGESTION_PATH, VARIABLE_SUB_PATH))
      .and()
      // since these calls will not be used in a browser, we can disable csrf
      .csrf().disable()
      .httpBasic().disable()
      // spring session management is not needed as we have stateless session handling using a JWT token
      .sessionManagement()
      .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      .and()
      .authorizeRequests()
      // everything requires authentication
      .anyRequest().authenticated()
      .and()
      .oauth2ResourceServer()
      .jwt();
  }

  private String getAudienceFromConfiguration() {
    return configurationService.getOptimizeApiConfiguration().getAudience();
  }
}
