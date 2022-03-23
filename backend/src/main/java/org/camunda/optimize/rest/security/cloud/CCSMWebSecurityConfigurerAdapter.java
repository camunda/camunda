/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.security.cloud;

import io.camunda.iam.sdk.IamApi;
import io.camunda.iam.sdk.IamApiConfiguration;
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
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.session.SessionManagementFilter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;

import static org.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;
import static org.camunda.optimize.rest.AuthenticationRestService.AUTHENTICATION_PATH;
import static org.camunda.optimize.rest.AuthenticationRestService.CALLBACK;
import static org.camunda.optimize.rest.HealthRestService.READYZ_PATH;

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
  public IamApi iamApi() {
    return new IamApi(iamApiConfiguration());
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
        // IAM callback request handling is public
        .antMatchers(createApiPath(AUTHENTICATION_PATH + CALLBACK)).permitAll()
      .anyRequest().authenticated()
      .and()
      .addFilterBefore(authenticationCookieFilter(), AbstractPreAuthenticatedProcessingFilter.class)
      .addFilterAfter(authenticationCookieRefreshFilter, SessionManagementFilter.class)
      .exceptionHandling().authenticationEntryPoint(this::redirectToIam);
    //@formatter:on
  }

  private void redirectToIam(final HttpServletRequest request, final HttpServletResponse response,
                             final AuthenticationException e) throws IOException {
    final URI iamUri = iamApi()
      .authentication()
      .authorizeUriBuilder(
        buildIamCallbackUri(request, createApiPath(AUTHENTICATION_PATH + CALLBACK))
      )
      .build();
    response.sendRedirect(iamUri.toString());
  }

  private static String buildIamCallbackUri(final HttpServletRequest req, String subPath) {
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

  private IamApiConfiguration iamApiConfiguration() {
    final CCSMAuthConfiguration ccsmAuthConfig = getCcsmAuthConfiguration();
    return new IamApiConfiguration(
      ccsmAuthConfig.getIssuerUrl(), ccsmAuthConfig.getClientId(), ccsmAuthConfig.getClientSecret()
    );
  }

  private CCSMAuthConfiguration getCcsmAuthConfiguration() {
    return configurationService.getAuthConfiguration().getCcsmAuthConfiguration();
  }

}
