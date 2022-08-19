/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.security.cloud;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.camunda.optimize.rest.security.AuthenticationCookieFilter;
import org.camunda.optimize.rest.security.AuthenticationCookieRefreshFilter;
import org.camunda.optimize.rest.security.CustomPreAuthenticatedAuthenticationProvider;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import org.camunda.optimize.service.util.configuration.security.CCSMAuthConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;

import static org.camunda.optimize.OptimizeJettyServerCustomizer.EXTERNAL_SUB_PATH;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.STATIC_RESOURCE_PATH;
import static org.camunda.optimize.rest.AuthenticationRestService.AUTHENTICATION_PATH;
import static org.camunda.optimize.rest.AuthenticationRestService.CALLBACK;
import static org.camunda.optimize.rest.HealthRestService.READYZ_PATH;
import static org.camunda.optimize.rest.LocalizationRestService.LOCALIZATION_PATH;
import static org.camunda.optimize.rest.UIConfigurationRestService.UI_CONFIGURATION_PATH;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.ACTUATOR_ENDPOINT;
import static org.camunda.optimize.rest.constants.RestConstants.PROMETHEUS_ENDPOINT;
import static org.camunda.optimize.rest.security.platform.PlatformWebSecurityConfigurerAdapter.DEEP_SUB_PATH_ANY;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@Conditional(CCSMCondition.class)
@Order(2)
public class CCSMWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {
  private final SessionService sessionService;
  private final ConfigurationService configurationService;
  private final AuthenticationCookieRefreshFilter authenticationCookieRefreshFilter;
  private final CustomPreAuthenticatedAuthenticationProvider preAuthenticatedAuthenticationProvider;

  @Override
  public void configure(AuthenticationManagerBuilder auth) throws Exception {
    auth.authenticationProvider(preAuthenticatedAuthenticationProvider);
  }

  @Bean
  public AuthenticationCookieFilter authenticationCookieFilter() throws Exception {
    return new AuthenticationCookieFilter(sessionService, authenticationManager());
  }

  @Bean
  public Identity identity() {
    return new Identity(identityConfiguration());
  }

  @SneakyThrows
  @Override
  protected void configure(HttpSecurity http) {
    //@formatter:off
    http
      // csrf is not used but the same-site property of the auth cookie, see AuthCookieService#createNewOptimizeAuthCookie
      .csrf().disable()
      .httpBasic().disable()
      // spring session management is not needed as we have stateless session handling using a JWT token stored as
      // cookie
      .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      .and()
      .authorizeRequests()
        // ready endpoint is public
        .antMatchers(createApiPath(READYZ_PATH)).permitAll()
        // Identity callback request handling is public
        .antMatchers(createApiPath(AUTHENTICATION_PATH + CALLBACK)).permitAll()
        // public share resources
        .antMatchers(EXTERNAL_SUB_PATH + "/", EXTERNAL_SUB_PATH + "/index*",
                   EXTERNAL_SUB_PATH + STATIC_RESOURCE_PATH + "/**", EXTERNAL_SUB_PATH + "/*.js",
                   EXTERNAL_SUB_PATH + "/*.ico").permitAll()
        // public share related resources (API)
        .antMatchers(createApiPath(EXTERNAL_SUB_PATH + DEEP_SUB_PATH_ANY)).permitAll()
        // common public api resources
        .antMatchers(
          createApiPath(UI_CONFIGURATION_PATH),
          createApiPath(LOCALIZATION_PATH)
        ).permitAll()
      .antMatchers(getPrometheusEndpoint()).permitAll()
      .anyRequest().authenticated()
      .and()
      .addFilterBefore(authenticationCookieFilter(), AbstractPreAuthenticatedProcessingFilter.class)
      .exceptionHandling()
        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), new AntPathRequestMatcher(REST_API_PATH + "/**"))
        .defaultAuthenticationEntryPointFor(this::redirectToIdentity, new AntPathRequestMatcher("/**"));
    //@formatter:on
  }

  private String getPrometheusEndpoint() {
    return ACTUATOR_ENDPOINT + PROMETHEUS_ENDPOINT;
  }

  private void redirectToIdentity(final HttpServletRequest request, final HttpServletResponse response,
                                  final AuthenticationException e) throws IOException {
    final URI authorizeUri = identity()
      .authentication()
      .authorizeUriBuilder(
        buildAuthorizeCallbackUri(request, createApiPath(AUTHENTICATION_PATH + CALLBACK))
      )
      .build();
    response.sendRedirect(authorizeUri.toString());
  }

  private static String buildAuthorizeCallbackUri(final HttpServletRequest req, String subPath) {
    String redirectUri = req.getScheme() + "://" + req.getServerName();
    if ((req.getScheme().equals("http") && req.getServerPort() != 80) || (
      req.getScheme().equals("https") && req.getServerPort() != 443)) {
      redirectUri += ":" + req.getServerPort();
    }
    return redirectUri + req.getContextPath() + subPath;
  }

  private String createApiPath(final String... subPath) {
    return REST_API_PATH + String.join("", subPath);
  }

  private IdentityConfiguration identityConfiguration() {
    final CCSMAuthConfiguration ccsmAuthConfig = getCcsmAuthConfiguration();
    return new IdentityConfiguration(
        ccsmAuthConfig.getIssuerUrl(), ccsmAuthConfig.getIssuerBackendUrl(),
        ccsmAuthConfig.getClientId(), ccsmAuthConfig.getClientSecret(), ccsmAuthConfig.getAudience()
    );
  }

  private CCSMAuthConfiguration getCcsmAuthConfiguration() {
    return configurationService.getAuthConfiguration().getCcsmAuthConfiguration();
  }

}
