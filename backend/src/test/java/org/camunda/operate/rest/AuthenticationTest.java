/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.rest.AuthenticationRestService.AUTHENTICATION_URL;
import static org.camunda.operate.security.WebSecurityConfig.COOKIE_JSESSIONID;
import static org.camunda.operate.security.WebSecurityConfig.LOGIN_RESOURCE;
import static org.camunda.operate.security.WebSecurityConfig.LOGOUT_RESOURCE;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.camunda.operate.entities.UserEntity;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.dto.UserDto;
import org.camunda.operate.security.WebSecurityConfig;
import org.camunda.operate.user.UserStorage;
import org.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = {OperateProperties.class,TestApplicationWithNoBeans.class,WebSecurityConfig.class, AuthenticationRestService.class},
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("auth")
public class AuthenticationTest {

  public static final String CURRENT_USER_URL = AUTHENTICATION_URL + "/user";

  public static final String USERNAME = "demo";
  public static final String PASSWORD = "demo";

  @Autowired
  OperateProperties operateProperties;
  
  @Autowired
  private TestRestTemplate testRestTemplate;
  
  @MockBean(name = "userStorage")
  UserStorage userStorage;
  
  @Before
  public void setUp() {
    given(userStorage.getUserByName("demo")).willReturn(new UserEntity().setUsername("demo").setPassword(new BCryptPasswordEncoder().encode("demo")).setRoles("USER"));
  }

  @Test
  public void shouldSetCookieAndCSRFToken() {
    // given
    HttpEntity<MultiValueMap<String, String>> request = prepareLoginRequest(USERNAME, PASSWORD);

    // when
    ResponseEntity<Void> response = login(request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(response.getHeaders()).containsKey("Set-Cookie");
    assertThat(response.getHeaders().get("Set-Cookie").get(0)).contains(COOKIE_JSESSIONID);
    if(operateProperties.isCsrfPreventionEnabled()) {
      assertThat(response.getHeaders()).containsKey("X-CSRF-TOKEN");
      assertThat(response.getHeaders().get("X-CSRF-TOKEN").get(0)).isNotBlank();
    }
  }

  @Test
  public void shouldFailWhileLogin() {
    // given
    HttpEntity<MultiValueMap<String, String>> request = prepareLoginRequest(USERNAME, String.format("%s%d", PASSWORD, 123));

    // when
    ResponseEntity<Void> response = login(request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldResetCookie() {
    // given
    HttpEntity<MultiValueMap<String, String>> loginRequest = prepareLoginRequest(USERNAME, PASSWORD);
    ResponseEntity<Void> loginResponse = login(loginRequest);

    // assume
    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(loginResponse.getHeaders()).containsKey("Set-Cookie");
    assertThat(loginResponse.getHeaders().get("Set-Cookie").get(0)).contains(COOKIE_JSESSIONID);

    HttpEntity<Map<String, String>> logoutRequest = prepareRequestWithCookies(loginResponse);

    // when
    ResponseEntity<String> logoutResponse = logout(logoutRequest);

    assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(logoutResponse.getHeaders()).containsKey("Set-Cookie");
    List<String> cookies = logoutResponse.getHeaders().get("Set-Cookie");
    if(operateProperties.isCsrfPreventionEnabled()) {
      assertThat(cookies).anyMatch((cookie) -> cookie.contains("X-CSRF-TOKEN"));
    }
    assertThat(cookies).anyMatch( (cookie) -> cookie.contains(COOKIE_JSESSIONID + "=;"));
  }

  @Test
  public void shouldReturnCurrentUser() {
    //given authenticated user
    HttpEntity<MultiValueMap<String, String>> loginRequest = prepareLoginRequest(USERNAME, PASSWORD);
    ResponseEntity<Void> loginResponse = login(loginRequest);

    //when
    final ResponseEntity<UserDto> responseEntity = testRestTemplate.exchange(CURRENT_USER_URL, HttpMethod.GET,
      prepareRequestWithCookies(loginResponse), UserDto.class);

    //then
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(responseEntity.getBody().getFirstname()).isNotEmpty();
    assertThat(responseEntity.getBody().getLastname()).isNotEmpty();
  }

  @Test
  public void testEndpointsNotAccessibleAfterLogout() {
    //when user is logged in
    HttpEntity<MultiValueMap<String, String>> loginRequest = prepareLoginRequest(USERNAME, PASSWORD);
    ResponseEntity<Void> loginResponse = login(loginRequest);
    
    //then endpoints are accessible
    ResponseEntity<Object> responseEntity = testRestTemplate.exchange(CURRENT_USER_URL, HttpMethod.GET, prepareRequestWithCookies(loginResponse), Object.class);
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(responseEntity.getBody()).isNotNull();

    //when user logged out
    HttpEntity<Map<String, String>> logoutRequest = prepareRequestWithCookies(loginResponse);
    logout(logoutRequest);

    //then endpoint is not accessible
    responseEntity = testRestTemplate.exchange(CURRENT_USER_URL, HttpMethod.GET, prepareRequestWithCookies(loginResponse), Object.class);
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
  
  protected HttpHeaders getHeaderWithCSRF(HttpHeaders responseHeaders) {
    HttpHeaders headers = new HttpHeaders();
    if(responseHeaders.containsKey("X-CSRF-HEADER")) {
      String csrfHeader = responseHeaders.get("X-CSRF-HEADER").get(0);
      String csrfToken = responseHeaders.get("X-CSRF-TOKEN").get(0);
      headers.set(csrfHeader,csrfToken);
    }
    return headers;
  }

  protected HttpEntity<MultiValueMap<String, String>> prepareLoginRequest(String username, String password) {
    HttpHeaders headers = new HttpHeaders();
    
    headers.setContentType(APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("username", username);
    body.add("password", password);

    return new HttpEntity<>(body, headers);
  }

  protected HttpEntity<Map<String, String>> prepareRequestWithCookies(ResponseEntity<?> response) {
    HttpHeaders headers = getHeaderWithCSRF(response.getHeaders());
    headers.setContentType(APPLICATION_JSON);
    headers.add("Cookie", response.getHeaders().get("Set-Cookie").get(0));

    Map<String, String> body = new HashMap<>();

    return new HttpEntity<>(body, headers);
  }

  protected ResponseEntity<Void> login(HttpEntity<MultiValueMap<String, String>> request) {
    return testRestTemplate.postForEntity(LOGIN_RESOURCE, request, Void.class);
  }

  protected ResponseEntity<String> logout(HttpEntity<Map<String, String>> request) {
    return testRestTemplate.postForEntity(LOGOUT_RESOURCE, request, String.class);
  }

}
