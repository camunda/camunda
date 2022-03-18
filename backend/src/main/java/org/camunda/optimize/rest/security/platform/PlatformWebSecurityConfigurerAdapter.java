/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.security.platform;

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
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
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
import javax.ws.rs.core.HttpHeaders;

import static org.camunda.optimize.jetty.EmbeddedCamundaOptimize.EXTERNAL_SUB_PATH;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.INDEX_PAGE;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.STATIC_RESOURCE_PATH;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.STATUS_WEBSOCKET_PATH;
import static org.camunda.optimize.rest.AssigneeRestService.ASSIGNEE_RESOURCE_PATH;
import static org.camunda.optimize.rest.AuthenticationRestService.AUTHENTICATION_PATH;
import static org.camunda.optimize.rest.CandidateGroupRestService.CANDIDATE_GROUP_RESOURCE_PATH;
import static org.camunda.optimize.rest.DecisionVariablesRestService.DECISION_INPUTS_NAMES_PATH;
import static org.camunda.optimize.rest.DecisionVariablesRestService.DECISION_OUTPUTS_NAMES_PATH;
import static org.camunda.optimize.rest.DecisionVariablesRestService.DECISION_VARIABLES_PATH;
import static org.camunda.optimize.rest.FlowNodeRestService.FLOW_NODE_NAMES_SUB_PATH;
import static org.camunda.optimize.rest.FlowNodeRestService.FLOW_NODE_PATH;
import static org.camunda.optimize.rest.HealthRestService.READYZ_PATH;
import static org.camunda.optimize.rest.LicenseCheckingRestService.LICENSE_PATH;
import static org.camunda.optimize.rest.LocalizationRestService.LOCALIZATION_PATH;
import static org.camunda.optimize.rest.ProcessVariableRestService.PROCESS_VARIABLES_PATH;
import static org.camunda.optimize.rest.SharingRestService.DASHBOARD_SUB_PATH;
import static org.camunda.optimize.rest.SharingRestService.EVALUATE_SUB_PATH;
import static org.camunda.optimize.rest.SharingRestService.REPORT_SUB_PATH;
import static org.camunda.optimize.rest.SharingRestService.SHARE_PATH;
import static org.camunda.optimize.rest.StatusRestService.STATUS_PATH;
import static org.camunda.optimize.rest.UIConfigurationRestService.UI_CONFIGURATION_PATH;
import static org.springframework.http.HttpStatus.TEMPORARY_REDIRECT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Configuration
@AllArgsConstructor
@EnableWebSecurity
@Conditional(CamundaPlatformCondition.class)
@Order(2)
public class PlatformWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {

  private static final String CSV_SUFFIX = ".csv";
  private static final String SUB_PATH_ANY = "/*";
  private static final String DEEP_SUB_PATH_ANY = "/**";

  private final AuthCookieService authCookieService;
  private final SessionService sessionService;
  private final CustomPreAuthenticatedAuthenticationProvider preAuthenticatedAuthenticationProvider;
  private final AuthenticationCookieRefreshFilter authenticationCookieRefreshFilter;
  private final SingleSignOnRequestFilter singleSignOnRequestFilter;

  @Override
  public void configure(AuthenticationManagerBuilder auth) throws Exception {
    auth
      .authenticationProvider(preAuthenticatedAuthenticationProvider);
  }

  @Bean
  public AuthenticationCookieFilter authenticationCookieFilter() throws Exception {
    return new AuthenticationCookieFilter(sessionService, authenticationManager());
  }

  @SneakyThrows
  @Override
  protected void configure(HttpSecurity http) {
    //@formatter:off
    http
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
      .authorizeRequests()
        // static resources
        .antMatchers("/", "/index*", STATIC_RESOURCE_PATH + "/**", "/*.js", "/*.ico").permitAll()
        // websocket
        .antMatchers(STATUS_WEBSOCKET_PATH).permitAll()
        // common public api resources
        .antMatchers(
          createApiPath(READYZ_PATH),
          createApiPath(STATUS_PATH),
          createApiPath(UI_CONFIGURATION_PATH),
          createApiPath(LOCALIZATION_PATH),
          createApiPath(LICENSE_PATH, SUB_PATH_ANY),
          createApiPath(AUTHENTICATION_PATH)
        ).permitAll()
      // public share related resources
        .antMatchers(EXTERNAL_SUB_PATH).permitAll()
        .antMatchers(
          HttpMethod.GET,
          createApiPath(CANDIDATE_GROUP_RESOURCE_PATH),
          createApiPath(ASSIGNEE_RESOURCE_PATH),
          createApiPath(SHARE_PATH, DASHBOARD_SUB_PATH, SUB_PATH_ANY, EVALUATE_SUB_PATH)
        ).permitAll()
        .antMatchers(
          HttpMethod.POST,
          createApiPath(SHARE_PATH, REPORT_SUB_PATH, SUB_PATH_ANY, EVALUATE_SUB_PATH),
          createApiPath(SHARE_PATH, DASHBOARD_SUB_PATH, SUB_PATH_ANY, REPORT_SUB_PATH, SUB_PATH_ANY, EVALUATE_SUB_PATH),
          createApiPath(FLOW_NODE_PATH, FLOW_NODE_NAMES_SUB_PATH),
          createApiPath(PROCESS_VARIABLES_PATH),
          createApiPath(DECISION_VARIABLES_PATH, DECISION_INPUTS_NAMES_PATH),
          createApiPath(DECISION_VARIABLES_PATH, DECISION_OUTPUTS_NAMES_PATH)
        ).permitAll()
        // everything else requires authentication
        .anyRequest().authenticated()
      .and()
      .addFilterBefore(singleSignOnRequestFilter, AbstractPreAuthenticatedProcessingFilter.class)
      .addFilterBefore(authenticationCookieFilter(), AbstractPreAuthenticatedProcessingFilter.class)
      .addFilterAfter(authenticationCookieRefreshFilter, SessionManagementFilter.class)
      .exceptionHandling().authenticationEntryPoint(this::failureHandler);
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
