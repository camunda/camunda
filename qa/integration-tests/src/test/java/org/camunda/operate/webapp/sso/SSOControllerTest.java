/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.sso;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;

import org.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.*;

@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = {
      TestApplicationWithNoBeans.class,SSOWebSecurityConfig.class,SSOController.class
  },
  properties = {
      "camunda.operate.auth0.clientId=1",
      "camunda.operate.auth0.clientSecret=2",
      "camunda.operate.auth0.organization=3"
  },
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("sso-auth")
public class SSOControllerTest {

  @LocalServerPort
  int randomServerPort;
  
  @Autowired
  TestRestTemplate testRestTemplate;

  @Autowired
  SSOController ssoController;
  
  @MockBean
  TokenAuthentication tokenAuthentication;
  
  @Test
  public void testNoPermission() {
    // given
    ResponseEntity<String> response = testRestTemplate.getForEntity(SSOWebSecurityConfig.NO_PERMISSION, String.class,new HashMap<>());
    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("No Permission");
  }

  @Test
  public void testUserNotAuthenticatedRoot() {
    // given
    // when
    ResponseEntity<String> response = testRestTemplate.getForEntity(SSOWebSecurityConfig.ROOT, String.class,new HashMap<>());
    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(response.getHeaders().getLocation().toString()).isEqualTo(serverUrlFor(SSOWebSecurityConfig.LOGIN_RESOURCE));
  }
  
  @Test
  public void testLoginWillBeRedirectedAndContainsCallbackUrl() {
    // given
    String loginProviderUrl="http://loginprovider.org?callbackUrl=";
    String callbackUrl = serverUrlFor(SSOWebSecurityConfig.CALLBACK_URI);
    given(tokenAuthentication.getAuthorizeUrl(notNull(),eq(callbackUrl))).willReturn(loginProviderUrl+callbackUrl);
    // when
    ResponseEntity<String> response = testRestTemplate.getForEntity(SSOWebSecurityConfig.LOGIN_RESOURCE, String.class,new HashMap<>());
    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    String redirectLocation = response.getHeaders().getLocation().toString();
    assertThat(redirectLocation).isEqualTo(loginProviderUrl+callbackUrl);
  }

  @Test
  public void testLoggedInCallbackIsSuccessful() throws Exception {
    // when
    ResponseEntity<String> response = testRestTemplate.getForEntity(SSOWebSecurityConfig.CALLBACK_URI, String.class,new HashMap<>());
    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    String redirectLocation = response.getHeaders().getLocation().toString();
    assertThat(redirectLocation).isEqualTo(serverUrlFor(SSOWebSecurityConfig.ROOT));
  }
  
  @Test
  public void testLoggedInCallbackIsNotSufficient() throws Exception {
    // when
    doThrow(new InsufficientAuthenticationException("No access")).when(tokenAuthentication).authenticate(notNull());
    ResponseEntity<String> response = testRestTemplate.getForEntity(SSOWebSecurityConfig.CALLBACK_URI, String.class,new HashMap<>());
    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    String redirectLocation = response.getHeaders().getLocation().toString();
    String auth0LogoutUrl = ssoController.getLogoutUrlFor(serverUrlFor(SSOWebSecurityConfig.NO_PERMISSION));
    String callbackUrlPart = "returnTo="+serverUrlFor(SSOWebSecurityConfig.NO_PERMISSION);
    assertThat(redirectLocation).contains(auth0LogoutUrl,callbackUrlPart);
  }
  
  @Test
  public void testLoggedInCallbackException() throws Exception {
    // when
    doThrow(new RuntimeException("Wrong credentials")).when(tokenAuthentication).authenticate(notNull());
    ResponseEntity<String> response = testRestTemplate.getForEntity(SSOWebSecurityConfig.CALLBACK_URI, String.class,new HashMap<>());
    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    String redirectLocation = response.getHeaders().getLocation().toString();
    assertThat(redirectLocation).isEqualTo(serverUrlFor(SSOWebSecurityConfig.NO_PERMISSION));
  }
  
  @Test
  public void testLogout() {
    ResponseEntity<String> response = testRestTemplate.getForEntity(SSOWebSecurityConfig.LOGOUT_RESOURCE, String.class,new HashMap<>());
    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    String redirectLocation = response.getHeaders().getLocation().toString();
    String auth0LogoutUrl = ssoController.getLogoutUrlFor(serverUrlFor(SSOWebSecurityConfig.ROOT));
    String callbackUrlPart = "returnTo="+serverUrlFor(SSOWebSecurityConfig.ROOT);
    assertThat(redirectLocation).contains(auth0LogoutUrl,callbackUrlPart);
  }
  
  protected String serverUrlFor(String path) {
    return "http://localhost:"+randomServerPort+path;
  }
}
