/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.security.platform;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.HttpHeaders;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.camunda.optimize.rest.security.AuthenticationCookieFilter;
import org.camunda.optimize.rest.security.AuthenticationCookieRefreshFilter;
import org.camunda.optimize.rest.security.CustomPreAuthenticatedAuthenticationProvider;
import org.camunda.optimize.rest.security.SingleSignOnRequestFilter;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.session.SessionManagementFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import static org.camunda.optimize.OptimizeJettyServerCustomizer.EXTERNAL_SUB_PATH;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.INDEX_PAGE;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.STATIC_RESOURCE_PATH;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.STATUS_WEBSOCKET_PATH;
import static org.camunda.optimize.rest.AuthenticationRestService.AUTHENTICATION_PATH;
import static org.camunda.optimize.rest.HealthRestService.READYZ_PATH;
import static org.camunda.optimize.rest.LicenseCheckingRestService.LICENSE_PATH;
import static org.camunda.optimize.rest.LocalizationRestService.LOCALIZATION_PATH;
import static org.camunda.optimize.rest.StatusRestService.STATUS_PATH;
import static org.camunda.optimize.rest.UIConfigurationRestService.UI_CONFIGURATION_PATH;
import static org.springframework.http.HttpStatus.TEMPORARY_REDIRECT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Configuration
@AllArgsConstructor
@EnableWebSecurity
@Conditional(CamundaPlatformCondition.class)
public class PlatformWebSecurityConfigurerAdapter {

  private static final String CSV_SUFFIX = ".csv";
  private static final String SUB_PATH_ANY = "/*";
  public static final String DEEP_SUB_PATH_ANY = "/**";

  private final AuthCookieService authCookieService;
  private final SessionService sessionService;
  private final CustomPreAuthenticatedAuthenticationProvider preAuthenticatedAuthenticationProvider;
  private final AuthenticationCookieRefreshFilter authenticationCookieRefreshFilter;
  private final SingleSignOnRequestFilter singleSignOnRequestFilter;

  @Bean
  public AuthenticationCookieFilter authenticationCookieFilter(HttpSecurity http) throws Exception {
    return new AuthenticationCookieFilter(
      sessionService,
      http.getSharedObject(AuthenticationManagerBuilder.class)
        .authenticationProvider(preAuthenticatedAuthenticationProvider)
        .build()
    );
  }

  @SneakyThrows
  @Bean
  protected SecurityFilterChain configure(HttpSecurity http) {
    //@formatter:off
    return http
      // csrf is not used but the same-site property of the auth cookie, see AuthCookieService#createNewOptimizeAuthCookie
      .csrf().disable()
      .httpBasic().disable()
      // disable frame options so embed links work, it's not a risk disabling this globally as click-jacking
      // is prevented by the samesite flag being set to `strict` on the authentication cookie
      .headers().frameOptions().disable()
      .and()
      // spring session management is not needed as we have stateless session handling using a JWT token stored as cookie
      .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      .and()
      .authorizeHttpRequests((requests) ->
        requests
          // static resources
          .requestMatchers(
            new AntPathRequestMatcher("/"),
            new AntPathRequestMatcher("/index*"),
            new AntPathRequestMatcher(STATIC_RESOURCE_PATH + "/**"),
            new AntPathRequestMatcher("/*.js"),
            new AntPathRequestMatcher("/*.ico")).permitAll()
          // websocket
          .requestMatchers(new AntPathRequestMatcher(STATUS_WEBSOCKET_PATH)).permitAll()
          // public resources
          .requestMatchers(
            new AntPathRequestMatcher(EXTERNAL_SUB_PATH + "/"),
            new AntPathRequestMatcher(EXTERNAL_SUB_PATH + "/index*"),
            new AntPathRequestMatcher(EXTERNAL_SUB_PATH + STATIC_RESOURCE_PATH + "/**"),
            new AntPathRequestMatcher(EXTERNAL_SUB_PATH + "/*.js"),
            new AntPathRequestMatcher(EXTERNAL_SUB_PATH + "/*.ico")
          )
          .permitAll()
          // public share related resources (API)
          .requestMatchers(
            new AntPathRequestMatcher(createApiPath(EXTERNAL_SUB_PATH + DEEP_SUB_PATH_ANY))).permitAll()
          // common public api resources
          .requestMatchers(
            new AntPathRequestMatcher(createApiPath(READYZ_PATH)),
            new AntPathRequestMatcher(createApiPath(STATUS_PATH)),
            new AntPathRequestMatcher(createApiPath(UI_CONFIGURATION_PATH)),
            new AntPathRequestMatcher(createApiPath(LOCALIZATION_PATH)),
            new AntPathRequestMatcher(createApiPath(LICENSE_PATH, SUB_PATH_ANY)),
            new AntPathRequestMatcher(createApiPath(AUTHENTICATION_PATH))
          ).permitAll()
          // everything else requires authentication
          .anyRequest().authenticated()
      )
      .addFilterBefore(singleSignOnRequestFilter, AbstractPreAuthenticatedProcessingFilter.class)
      .addFilterBefore(authenticationCookieFilter(http), AbstractPreAuthenticatedProcessingFilter.class)
      .addFilterAfter(authenticationCookieRefreshFilter, SessionManagementFilter.class)
      .exceptionHandling().authenticationEntryPoint(this::failureHandler)
      .and().build();
    //@formatter:on
  }

  private void failureHandler(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) {
    if (isCSVRequest(request.getPathInfo())) {
      response.setStatus(TEMPORARY_REDIRECT.value());
      response.setHeader(HttpHeaders.LOCATION, INDEX_PAGE);
    } else {
      response.setStatus(UNAUTHORIZED.value());
      if (sessionService.isTokenPresent(request)) {
        // clear cookie
        response.addHeader(
          HttpHeaders.SET_COOKIE,
          authCookieService.createDeleteOptimizeAuthCookie(request.getScheme()).toString()
        );
      }
    }
  }

  private String createApiPath(final String... subPath) {
    return REST_API_PATH + String.join("", subPath);
  }

  private boolean isCSVRequest(final String path) {
    return path != null && path.endsWith(CSV_SUFFIX);
  }

}
