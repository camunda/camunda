/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security.ldap;

import static org.assertj.core.api.Assertions.assertThat;
import static io.camunda.operate.webapp.rest.AuthenticationRestService.AUTHENTICATION_URL;
import static io.camunda.operate.webapp.rest.AuthenticationRestService.USER_ENDPOINT;
import static io.camunda.operate.webapp.security.OperateURIs.*;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.HashMap;
import java.util.Map;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.rest.AuthenticationRestService;
import io.camunda.operate.webapp.rest.dto.UserDto;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
        OperateProperties.class,
        TestApplicationWithNoBeans.class, AuthenticationRestService.class,
        LDAPWebSecurityConfig.class, LDAPUserService.class
    },
    properties = {
        "spring.ldap.embedded.base-dn=dc=springframework,dc=org",
        "spring.ldap.embedded.credential.username=uid=admin",
        "spring.ldap.embedded.credential.password=secret",
        "spring.ldap.embedded.ldif=classpath:config/ldap-test-server.ldif",
        "spring.ldap.embedded.port=8389",
        "camunda.operate.ldap.url=ldap://localhost:8389/",
        "camunda.operate.ldap.baseDn=dc=springframework,dc=org",
        "camunda.operate.ldap.managerDn=uid=admin",
        "camunda.operate.ldap.managerPassword=secret",
        "camunda.operate.ldap.userSearchFilter=uid={0}",
        //WRONG ATTR NAMES
        "camunda.operate.ldap.firstnameAttrName=wrongValue",
        "camunda.operate.ldap.lastnameAttrName="
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles({"ldap-auth", "test"})
public class AuthenticationWrongParametersTest {

  private static final String SET_COOKIE_HEADER = "Set-Cookie";
  private static final String CURRENT_USER_URL = AUTHENTICATION_URL + USER_ENDPOINT;

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Test
  public void shouldReturnCurrentUser() {
    //given authenticated user
    ResponseEntity<?> response = loginAs("bob", "bobspassword");
    // when
    UserDto userInfo = getCurrentUser(response);
    //then
    assertThat(userInfo.getUsername()).isEqualTo("bob");
    assertThat(userInfo.getFirstname()).isNull();
    assertThat(userInfo.getLastname()).isNull();
  }

  @Test
  @Ignore("User with encoded password")
  public void shouldReturnCurrentUser2() {
    //given authenticated user
    ResponseEntity<?> response = loginAs("ben", "benspassword");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    // when
    UserDto userInfo = getCurrentUser(response);
    //then
    assertThat(userInfo.getFirstname()).isNull();
    assertThat(userInfo.getLastname()).isNull();
  }

  protected ResponseEntity<?> loginAs(String user, String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("username", user);
    body.add("password", password);

    return testRestTemplate.postForEntity(LOGIN_RESOURCE, new HttpEntity<>(body, headers), Void.class);
  }

  protected UserDto getCurrentUser(ResponseEntity<?> previousResponse) {
    final ResponseEntity<UserDto> responseEntity = testRestTemplate.exchange(CURRENT_USER_URL, HttpMethod.GET,
        prepareRequestWithCookies(previousResponse), UserDto.class);
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    return responseEntity.getBody();
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

}
