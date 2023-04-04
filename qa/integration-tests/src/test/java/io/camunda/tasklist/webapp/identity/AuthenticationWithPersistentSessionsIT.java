/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.identity;

import static io.camunda.tasklist.webapp.security.TasklistURIs.COOKIE_JSESSIONID;
import static io.camunda.tasklist.webapp.security.TasklistURIs.IDENTITY_CALLBACK_URI;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.NO_PERMISSION;
import static io.camunda.tasklist.webapp.security.TasklistURIs.ROOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.apps.identity.AuthIdentityApplication;
import io.camunda.tasklist.webapp.security.AuthenticationTestable;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthentication;
import io.camunda.tasklist.webapp.security.identity.IdentityService;
import java.util.HashMap;
import org.assertj.core.util.DateUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {AuthIdentityApplication.class},
    properties = {
      "camunda.tasklist.identity.issuerBackendUrl=http://localhost:18080/auth/realms/camunda-platform",
      "camunda.tasklist.identity.issuerUrl=http://localhost:18080/auth/realms/camunda-platform",
      "camunda.tasklist.identity.clientId=tasklist",
      "camunda.tasklist.identity.clientSecret=the-cake-is-alive",
      "camunda.tasklist.identity.audience=tasklist-api",
      "server.servlet.session.cookie.name = " + COOKIE_JSESSIONID,
      "camunda.tasklist.persistentSessionsEnabled = true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({TasklistProfileService.IDENTITY_AUTH_PROFILE, "test"})
public class AuthenticationWithPersistentSessionsIT implements AuthenticationTestable {

  @LocalServerPort private int randomServerPort;

  @Autowired private TestRestTemplate testRestTemplate;

  @MockBean private IdentityService identityService;

  @Autowired private TasklistProperties tasklistProperties;

  @Test
  public void testAccessNoPermission() {
    final ResponseEntity<String> response = get(NO_PERMISSION);
    assertThat(response.getBody()).contains("No permission for Tasklist");
  }

  @Test
  public void testLoginSuccess() {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    assertThatCookiesAreSet(response);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
    when(identityService.getRedirectUrl(any())).thenReturn("/redirected-to-identity");

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response)).contains("/redirected-to-identity");

    // Step 3 assume authentication will be successful
    final IdentityAuthentication identityAuthentication =
        new IdentityAuthentication().setExpires(DateUtil.tomorrow());
    identityAuthentication.setAuthenticated(true);

    when(identityService.getAuthenticationFor(any(), any())).thenReturn(identityAuthentication);

    // Step 4 do callback
    response = get(IDENTITY_CALLBACK_URI, cookies);

    assertThatRequestIsRedirectedTo(response, urlFor(ROOT));
    // Step 5  check if access to url possible
    response = get(ROOT, cookies);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  public void testLoginFailedWithNoPermissions() {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    assertThatCookiesAreSet(response);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
    when(identityService.getRedirectUrl(any())).thenReturn("/redirected-to-identity");

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response)).contains("/redirected-to-identity");

    // Step 3 assume authentication will be fail
    when(identityService.getAuthenticationFor(any(), any()))
        .thenThrow(new RuntimeException("Something is going wrong"));

    response = get(IDENTITY_CALLBACK_URI, cookies);

    assertThat(redirectLocationIn(response)).contains(NO_PERMISSION);

    response = get(ROOT, cookies);
    // Check that access to url is not possible
    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
  }

  @Test
  public void testLoginFailedWithNoReadPermissions() {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    assertThatCookiesAreSet(response);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
    when(identityService.getRedirectUrl(any())).thenReturn("/redirected-to-identity");

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response)).contains("/redirected-to-identity");
    // Step 3 assume authentication succeed but return no READ permission
    when(identityService.getAuthenticationFor(any(), any()))
        .thenThrow(new InsufficientAuthenticationException("No read permissions"));

    response = get(IDENTITY_CALLBACK_URI, cookies);

    assertThat(redirectLocationIn(response)).contains(NO_PERMISSION);

    response = get(ROOT, cookies);
    // Check that access to url is not possible
    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
  }

  @Override
  public TestRestTemplate getTestRestTemplate() {
    return testRestTemplate;
  }

  private HttpEntity<?> httpEntityWithCookie(ResponseEntity<String> response) {
    final HttpHeaders headers = new HttpHeaders();
    headers.add("Cookie", getCookiesAsString(response.getHeaders()));
    return new HttpEntity<>(new HashMap<>(), headers);
  }

  protected void assertThatRequestIsRedirectedTo(ResponseEntity<?> response, String url) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(redirectLocationIn(response)).isEqualTo(url);
  }

  private String urlFor(String path) {
    return "http://localhost:" + randomServerPort + path;
  }

  private ResponseEntity<String> get(String path, HttpEntity<?> requestEntity) {
    return testRestTemplate.exchange(path, HttpMethod.GET, requestEntity, String.class);
  }
}
