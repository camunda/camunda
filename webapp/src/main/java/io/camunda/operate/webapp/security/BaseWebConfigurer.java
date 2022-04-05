/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security;

import static io.camunda.operate.webapp.security.OperateURIs.API;
import static io.camunda.operate.webapp.security.OperateURIs.AUTH_WHITELIST;
import static io.camunda.operate.webapp.security.OperateURIs.COOKIE_JSESSIONID;
import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.LOGOUT_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.PUBLIC_API;
import static io.camunda.operate.webapp.security.OperateURIs.REQUESTED_URL;
import static io.camunda.operate.webapp.security.OperateURIs.RESPONSE_CHARACTER_ENCODING;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.security.oauth2.OAuth2WebConfigurer;
import java.io.IOException;
import java.io.PrintWriter;
import javax.json.Json;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

public abstract class BaseWebConfigurer extends WebSecurityConfigurerAdapter {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  @Autowired
  protected OperateProperties operateProperties;

  @Autowired
  OperateProfileService errorMessageService;

  @Autowired private OAuth2WebConfigurer oAuth2WebConfigurer;

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.csrf().disable()
        .authorizeRequests()
        .antMatchers(AUTH_WHITELIST).permitAll()
        .antMatchers(API , PUBLIC_API).authenticated()
        .and()
        .formLogin()
        .loginProcessingUrl(LOGIN_RESOURCE)
        .successHandler(this::successHandler)
        .failureHandler(this::failureHandler)
        .permitAll()
        .and()
        .logout()
        .logoutUrl(LOGOUT_RESOURCE)
        .logoutSuccessHandler(this::logoutSuccessHandler)
        .permitAll()
        .deleteCookies(COOKIE_JSESSIONID)
        .clearAuthentication(true)
        .invalidateHttpSession(true)
        .and()
        .exceptionHandling().authenticationEntryPoint(this::failureHandler);
    oAuth2WebConfigurer.configure(http);
  }

  protected void logoutSuccessHandler(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) {
    response.setStatus(NO_CONTENT.value());
  }

  protected void failureHandler(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException ex) throws IOException {
    String requestedUrl = request.getRequestURI();
    if (requestedUrl.contains("api")) {
      sendError(request,response, ex);
    } else {
      storeRequestedUrlAndRedirectToLogin(request, response, requestedUrl);
    }
  }

  private void storeRequestedUrlAndRedirectToLogin(final HttpServletRequest request, final HttpServletResponse response,
      String requestedUrl) throws IOException {
    if(request.getQueryString() !=null && !request.getQueryString().isEmpty()) {
      requestedUrl = requestedUrl + "?" + request.getQueryString();
    }
    logger.debug("Try to access protected resource {}. Save it for later redirect", requestedUrl);
    request.getSession(true).setAttribute(REQUESTED_URL, requestedUrl);
    response.sendRedirect(request.getContextPath() + LOGIN_RESOURCE);
  }

  private void successHandler(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) {
    response.setStatus(NO_CONTENT.value());
  }

  protected void sendError(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException{
    request.getSession().invalidate();
    response.reset();
    response.setCharacterEncoding(RESPONSE_CHARACTER_ENCODING);

    PrintWriter writer = response.getWriter();
    response.setContentType(APPLICATION_JSON.getMimeType());

    String jsonResponse = Json.createObjectBuilder()
        .add("message", errorMessageService.getMessageByProfileFor(ex))
        .build()
        .toString();

    writer.append(jsonResponse);
    response.setStatus(UNAUTHORIZED.value());
  }


}
