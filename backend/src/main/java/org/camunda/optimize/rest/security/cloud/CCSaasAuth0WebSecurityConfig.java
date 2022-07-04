/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.security.cloud;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.camunda.optimize.service.util.configuration.security.CloudAuthConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.util.List;

@RequiredArgsConstructor
@Configuration
@Conditional(CCSaaSCondition.class)
public class CCSaasAuth0WebSecurityConfig {
  public static final String OAUTH_AUTH_ENDPOINT = "/sso";
  public static final String OAUTH_REDIRECT_ENDPOINT = "/sso-callback";
  public static final String AUTH0_JWKS_ENDPOINT = "/.well-known/jwks.json";
  public static final String URL_TEMPLATE = "https://%s%s";
  public static final String AUTH_0_CLIENT_REGISTRATION_ID = "auth0";
  private static final String AUTH0_AUTH_ENDPOINT = "/authorize";
  private static final String AUTH0_TOKEN_ENDPOINT = "/oauth/token";
  private static final String AUTH0_USERINFO_ENDPOINT = "/userinfo";
  private final ConfigurationService configurationService;

  @Bean
  public OAuth2AuthorizedClientService authorizedClientService() {
    return new InMemoryOAuth2AuthorizedClientService(
      clientRegistrationRepository()
    );
  }

  @Bean
  public ClientRegistrationRepository clientRegistrationRepository() {
    final ClientRegistration.Builder builder = ClientRegistration.withRegistrationId(AUTH_0_CLIENT_REGISTRATION_ID)
      .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
      .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
      // For allowed redirect urls auth0 is not supporting wildcards within the actual path.
      // Thus the clusterId is passed along as query parameter and will get picked up by the cloud ingress proxy
      // which redirects the callback to the particular Optimize instance of the cluster the login was issued from.
      .redirectUri("{baseUrl}" + OAUTH_REDIRECT_ENDPOINT + "?uuid=" + getAuth0Configuration().getClusterId())
      .authorizationUri(buildAuth0CustomDomainUrl(
        AUTH0_AUTH_ENDPOINT + "?audience=" + getAuth0Configuration().getUserAccessTokenAudience().orElse("")
      ))
      .tokenUri(buildAuth0DomainUrl(AUTH0_TOKEN_ENDPOINT))
      .userInfoUri(buildAuth0DomainUrl(AUTH0_USERINFO_ENDPOINT))
      .scope("openid", "profile")
      .userNameAttributeName(getAuth0Configuration().getUserIdAttributeName())
      .clientId(getAuth0Configuration().getClientId())
      .clientSecret(getAuth0Configuration().getClientSecret())
      .jwkSetUri(String.format(URL_TEMPLATE, getAuth0Configuration().getDomain(), AUTH0_JWKS_ENDPOINT));
    return new InMemoryClientRegistrationRepository(List.of(builder.build()));
  }

  private String buildAuth0DomainUrl(final String path) {
    return String.format(URL_TEMPLATE, getAuth0Configuration().getDomain(), path);
  }

  private String buildAuth0CustomDomainUrl(final String path) {
    return String.format(URL_TEMPLATE, getAuth0Configuration().getCustomDomain(), path);
  }

  private CloudAuthConfiguration getAuth0Configuration() {
    return configurationService.getAuthConfiguration().getCloudAuthConfiguration();
  }
}
