/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.security.ldap;

import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import org.camunda.operate.webapp.rest.AuthenticationRestService;
import org.camunda.operate.webapp.rest.dto.UserDto;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.webapp.rest.AuthenticationRestService.AUTHENTICATION_URL;
import static org.camunda.operate.webapp.rest.AuthenticationRestService.USER_ENDPOINT;
import static org.camunda.operate.webapp.security.OperateURIs.*;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
        OperateProperties.class,
        TestApplicationWithNoBeans.class, AuthenticationRestService.class,
        LDAPWebSecurityConfig.class, LDAPUserService.class
    },
    properties = {
        "camunda.operate.ldap.baseDn=dc=planetexpress,dc=com",
        "camunda.operate.ldap.managerDn=cn=admin,dc=planetexpress,dc=com",
        "camunda.operate.ldap.managerPassword=GoodNewsEveryone",
        "camunda.operate.ldap.userSearchFilter=uid={0}"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles({"ldap-auth", "test"})
@ContextConfiguration(initializers = {AuthenticationTest.Initializer.class})
@Ignore("https://github.com/rroemhild/docker-test-openldap/issues/23")
public class AuthenticationTest {

  private static final String SET_COOKIE_HEADER = "Set-Cookie";
  private static final String CURRENT_USER_URL = AUTHENTICATION_URL + USER_ENDPOINT;

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Autowired
  private OperateProperties operateProperties;

  @ClassRule
  public static GenericContainer<?> ldapServer =
      // https://github.com/rroemhild/docker-test-openldap
      new GenericContainer<>("rroemhild/test-openldap")
          .withExposedPorts(389);

  static class Initializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
      TestPropertyValues.of(
          String.format("camunda.operate.ldap.url=ldap://%s:%d/", ldapServer.getHost(), ldapServer.getFirstMappedPort())
      ).applyTo(configurableApplicationContext.getEnvironment());
    }
  }

  @Test
  public void testLoginSuccess() {
    ResponseEntity<?> response = loginAs("fry", "fry");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThatCookiesExists(response);
  }

  @Test
  public void testLoginFailed() {
    ResponseEntity<?> response = loginAs("amy", "amy");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void testLogout() {
    // Given
    ResponseEntity<?> response = loginAs("fry", "fry");
    // When
    ResponseEntity<?> logoutResponse = logout(response);
    // Then
    assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThatCookiesAreDeleted(logoutResponse);
  }

  @Test
  public void shouldReturnCurrentUser() {
    //given authenticated user
    ResponseEntity<?> response = loginAs("bender", "bender");
    // when
    UserDto userInfo = getCurrentUser(response);
    //then
    assertThat(userInfo.getUsername()).isEqualTo("bender");
    assertThat(userInfo.getFirstname()).isEqualTo("Bender");
    assertThat(userInfo.getLastname()).isEqualTo("Rodríguez");
    assertThat(userInfo.isCanLogout()).isTrue();
  }

  protected ResponseEntity<?> loginAs(String user, String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("username", user);
    body.add("password", password);

    return testRestTemplate.postForEntity(LOGIN_RESOURCE, new HttpEntity<>(body, headers), Void.class);
  }

  protected ResponseEntity<?> logout(ResponseEntity<?> previousResponse) {
    HttpEntity<Map<String, String>> request = prepareRequestWithCookies(previousResponse);
    return testRestTemplate.postForEntity(LOGOUT_RESOURCE, request, String.class);
  }

  protected UserDto getCurrentUser(ResponseEntity<?> previousResponse) {
    final ResponseEntity<UserDto> responseEntity = testRestTemplate.exchange(CURRENT_USER_URL, HttpMethod.GET,
        prepareRequestWithCookies(previousResponse), UserDto.class);
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    return responseEntity.getBody();
  }

  protected void assertThatCookiesExists(ResponseEntity<?> response) {
    assertThat(response.getHeaders()).containsKey(SET_COOKIE_HEADER);
    assertThat(response.getHeaders().get(SET_COOKIE_HEADER).get(0)).contains(COOKIE_JSESSIONID);
  }

  protected HttpHeaders getHeaderWithCSRF(HttpHeaders responseHeaders) {
    HttpHeaders headers = new HttpHeaders();
    if (responseHeaders.containsKey(X_CSRF_HEADER)) {
      String csrfHeader = responseHeaders.get(X_CSRF_HEADER).get(0);
      String csrfToken = responseHeaders.get(X_CSRF_TOKEN).get(0);
      headers.set(csrfHeader, csrfToken);
    }
    return headers;
  }

  protected HttpEntity<Map<String, String>> prepareRequestWithCookies(ResponseEntity<?> response) {
    HttpHeaders headers = getHeaderWithCSRF(response.getHeaders());
    headers.setContentType(APPLICATION_JSON);
    headers.add("Cookie", response.getHeaders().get(SET_COOKIE_HEADER).get(0));

    Map<String, String> body = new HashMap<>();

    return new HttpEntity<>(body, headers);
  }

  protected void assertThatCookiesAreDeleted(ResponseEntity<?> response) {
    HttpHeaders headers = response.getHeaders();
    assertThat(headers).containsKey(SET_COOKIE_HEADER);
    assertThat(headers).doesNotContainKey(X_CSRF_TOKEN);
    List<String> cookies = headers.get(SET_COOKIE_HEADER);
    final String emptyValue = "=;";
    if (operateProperties.isCsrfPreventionEnabled()) {
      assertThat(cookies).anyMatch((cookie) -> cookie.contains(X_CSRF_TOKEN + emptyValue));
    }
    assertThat(cookies).anyMatch((cookie) -> cookie.contains(COOKIE_JSESSIONID + emptyValue));
  }

}
