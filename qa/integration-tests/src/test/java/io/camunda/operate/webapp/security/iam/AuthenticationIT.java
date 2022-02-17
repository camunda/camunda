/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security.iam;

import static io.camunda.operate.webapp.security.OperateProfileService.IAM_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.OperateURIs.IAM_CALLBACK_URI;
import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.NO_PERMISSION;
import static io.camunda.operate.webapp.security.OperateURIs.ROOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.auth0.jwt.exceptions.TokenExpiredException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.rest.AuthenticationRestService;
import io.camunda.operate.webapp.security.AuthenticationTestable;
import io.camunda.operate.webapp.security.oauth2.CCSaaSJwtAuthenticationTokenValidator;
import io.camunda.operate.webapp.security.oauth2.Jwt2AuthenticationTokenConverter;
import io.camunda.operate.webapp.security.oauth2.OAuth2WebConfigurer;
import io.camunda.operate.webapp.security.OperateProfileService;
import io.camunda.operate.webapp.security.OperateURIs;
import io.camunda.operate.webapp.security.Permission;
import io.camunda.operate.webapp.security.RolePermissionService;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
        TestApplicationWithNoBeans.class,
        OAuth2WebConfigurer.class,
        Jwt2AuthenticationTokenConverter.class,
        CCSaaSJwtAuthenticationTokenValidator.class,
        IAMWebSecurityConfig.class,
        IAMController.class,
        IAMAuthentication.class,
        IAMUserService.class,
        RolePermissionService.class,
        AuthenticationRestService.class,
        OperateURIs.class,
        OperateProperties.class,
        OperateProfileService.class
    },
    properties = {
        "camunda.operate.iam.issuer=http://app.iam.localhost",
        "camunda.operate.iam.issuerUrl=http://app.iam.localhost",
        "camunda.operate.iam.clientId=operate",
        "camunda.operate.iam.clientSecret=123",
        "server.servlet.session.cookie.name = " + OperateURIs.COOKIE_JSESSIONID
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles(IAM_AUTH_PROFILE)
public class AuthenticationIT implements AuthenticationTestable {

  @LocalServerPort
  private int randomServerPort;

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Autowired
  private OperateProperties operateProperties;

  @MockBean
  private IAMAuthentication iamAuthentication;

  @Test
  public void testAccessNoPermission() {
    ResponseEntity<String> response = get(NO_PERMISSION);
    assertThat(response.getBody()).contains("No permission for Operate");
  }

  @Test
  public void testLoginSuccess() {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response)).contains(
        operateProperties.getIam().getIssuerUrl(),
        URLEncoder.encode(IAM_CALLBACK_URI, Charset.defaultCharset()),
        operateProperties.getIam().getClientId()
    );
    // Step 3 assume authentication will be successful
    when(iamAuthentication.isAuthenticated()).thenReturn(true);
    // Step 4 do callback
    response = get(IAM_CALLBACK_URI, cookies);
    assertThatRequestIsRedirectedTo(response, urlFor(ROOT));
    // Step 5  check if access to url possible
    response = get(ROOT, cookies);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  public void testLoginFailedWithNoPermissions() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response)).contains(
        operateProperties.getIam().getIssuerUrl(),
        URLEncoder.encode(IAM_CALLBACK_URI, Charset.defaultCharset()),
        operateProperties.getIam().getClientId()
    );
    // Step 3 assume authentication will be fail
    doThrow(TokenExpiredException.class).when(iamAuthentication).authenticate(any(), any());
    when(iamAuthentication.isAuthenticated()).thenReturn(false);
    response = get(IAM_CALLBACK_URI, cookies);

    assertThat(redirectLocationIn(response)).contains(NO_PERMISSION);

    response = get(ROOT, cookies);
    // Check that access to url is not possible
    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
  }

  @Test
  public void testLoginFailedWithNoReadPermissions() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response)).contains(
        operateProperties.getIam().getIssuerUrl(),
        URLEncoder.encode(IAM_CALLBACK_URI, Charset.defaultCharset()),
        operateProperties.getIam().getClientId()
    );
    // Step 3 assume permissions doesnt contain READ
    doThrow(TokenExpiredException.class).when(iamAuthentication).authenticate(any(), any());
    when(iamAuthentication.isAuthenticated()).thenReturn(true);
    when(iamAuthentication.getPermissions()).thenReturn(List.of(Permission.WRITE));
    response = get(IAM_CALLBACK_URI, cookies);

    assertThat(redirectLocationIn(response)).contains(NO_PERMISSION);

    response = get(ROOT, cookies);
    // Check that access to url is not possible
    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
  }

  @Test
  public void testLoginToAPIResource() throws Exception {
    // Step 1 try to access user info
    String userInfoUrl = AuthenticationRestService.AUTHENTICATION_URL + "/user";
    ResponseEntity<String> response = get(userInfoUrl);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    // Save cookie for further requests
    HttpEntity<?> httpEntity = httpEntityWithCookie(response);

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, httpEntity);

    assertThat(redirectLocationIn(response)).contains(
        operateProperties.getIam().getIssuerUrl(),
        URLEncoder.encode(IAM_CALLBACK_URI, Charset.defaultCharset()),
        operateProperties.getIam().getClientId()
    );
    // Step 3 assume authentication will be successful
    doNothing().when(iamAuthentication).authenticate(any(), any());
    when(iamAuthentication.isAuthenticated()).thenReturn(true);
    when(iamAuthentication.getName()).thenReturn("displayName");
    when(iamAuthentication.getId()).thenReturn("userId");
    response = get(IAM_CALLBACK_URI, httpEntity);

    httpEntity = httpEntityWithCookie(response);
    response = get(userInfoUrl, httpEntity);
    assertThat(response.getBody()).contains("\"displayName\":\"displayName\"");
    assertThat(response.getBody()).contains("\"userId\":\"userId\"");
    assertThat(response.getBody()).contains("\"username\":\"displayName\"");
    assertThat(response.getBody()).contains("\"canLogout\":false");
    assertThat(response.getBody()).contains("\"permissions\":[");
  }

  //@Ignore("NOT IMPLEMENTED YET")
  @Test
  public void testLogout() {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response)).contains(
        operateProperties.getIam().getIssuerUrl(),
        URLEncoder.encode(IAM_CALLBACK_URI, Charset.defaultCharset()),
        operateProperties.getIam().getClientId()
    );
    // Step 3 assume authentication will be successful
    when(iamAuthentication.isAuthenticated()).thenReturn(true);
    // Step 4 do callback
    response = get(IAM_CALLBACK_URI, cookies);
    assertThatRequestIsRedirectedTo(response, urlFor(ROOT));
    // Step 5  check if access to url possible
    response = get(ROOT, cookies);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    // when
    ResponseEntity<?> logoutResponse = logout(response);

    // Redirect to iam logout page
    assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(redirectLocationIn(logoutResponse)).contains(
        operateProperties.getIam().getIssuerUrl(), "logout");
    // TODO: Check operate logout callback
    // response = get(IAM_LOGOUT_CALLBACK_URI, cookies);
    // assertThatRequestIsRedirectedTo(response, urlFor(ROOT));
  }

  protected void assertThatRequestIsRedirectedTo(ResponseEntity<?> response, String url) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(redirectLocationIn(response)).isEqualTo(url);
  }

  @Override
  public TestRestTemplate getTestRestTemplate() {
    return testRestTemplate;
  }

  private HttpEntity<?> httpEntityWithCookie(ResponseEntity<String> response) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Cookie", String.join(";", getCookies(response)));
    return new HttpEntity<>(new HashMap<>(), headers);
  }

  private String urlFor(String path) {
    return "http://localhost:" + randomServerPort + path;
  }

  private ResponseEntity<String> get(String path, HttpEntity<?> requestEntity) {
    return testRestTemplate.exchange(path, HttpMethod.GET, requestEntity, String.class);
  }

}
