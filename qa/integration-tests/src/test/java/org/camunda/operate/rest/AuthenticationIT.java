/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.webapp.rest.AuthenticationRestService.AUTHENTICATION_URL;
import static org.camunda.operate.webapp.security.WebSecurityConfig.COOKIE_JSESSIONID;
import static org.camunda.operate.webapp.security.WebSecurityConfig.LOGIN_RESOURCE;
import static org.camunda.operate.webapp.security.WebSecurityConfig.LOGOUT_RESOURCE;
import static org.camunda.operate.webapp.security.WebSecurityConfig.X_CSRF_HEADER;
import static org.camunda.operate.webapp.security.WebSecurityConfig.X_CSRF_TOKEN;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.camunda.operate.TestApplication;
import org.camunda.operate.entities.UserEntity;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.webapp.rest.AuthenticationRestService;
import org.camunda.operate.webapp.rest.dto.UserDto;
import org.camunda.operate.webapp.security.WebSecurityConfig;
import org.camunda.operate.webapp.user.ElasticSearchUserDetailsService;
import org.camunda.operate.webapp.user.UserStorage;
import org.camunda.operate.util.MetricAssert;
import org.junit.Before;
import org.junit.Ignore;
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
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = {
      OperateProperties.class,TestApplication.class,WebSecurityConfig.class, AuthenticationRestService.class,ElasticSearchUserDetailsService.class
  },
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("auth")
public class AuthenticationIT {

  private static final String SET_COOKIE_HEADER = "Set-Cookie";

  public static final String CURRENT_USER_URL = AUTHENTICATION_URL + "/user";

  public static final String USERNAME = "demo";
  public static final String PASSWORD = "demo";

  private static final String USER_ROLE = "USER";
  private static final String METRICS_ROLE = "ACTRADMIN";
  private static final String METRICS_USER = "act";
  private static final String METRICS_PASSWORD = "act";

  @Autowired
  OperateProperties operateProperties;
  
  @Autowired
  private TestRestTemplate testRestTemplate;
  
  @MockBean(name = "userStorage")
  UserStorage userStorage;
  
  @Before
  public void setUp() {
    addWebUser(USERNAME, PASSWORD, USER_ROLE);
    addWebUser(METRICS_USER, METRICS_PASSWORD, METRICS_ROLE);
  }
  
  protected void addWebUser(String name,String password, String role) {
    given(userStorage.getByName(name))
    .willReturn(new UserEntity()
    .setUsername(name)
    .setPassword(
        new BCryptPasswordEncoder().encode(password)
     ).setRole(role));
  }

  @Test
  public void shouldSetCookieAndCSRFToken() {
    // given
    // when
    ResponseEntity<Void> response = login(USERNAME, PASSWORD);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThatCookiesAreSet(response.getHeaders());
  }

  @Test
  public void shouldFailWhileLogin() {
    // when
    ResponseEntity<Void> response = login(USERNAME, String.format("%s%d", PASSWORD, 123));

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getHeaders().containsKey(SET_COOKIE_HEADER)).isFalse();
    assertThat(response.getHeaders().containsKey(X_CSRF_TOKEN)).isFalse();
  }

  @Test
  public void shouldResetCookie() {
    // given
    ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);

    // assume
    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(loginResponse.getHeaders()).containsKey(SET_COOKIE_HEADER);
    assertThat(loginResponse.getHeaders().get(SET_COOKIE_HEADER).get(0)).contains(COOKIE_JSESSIONID);
    // when
    ResponseEntity<String> logoutResponse = logout(loginResponse);

    assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(logoutResponse.getHeaders().containsKey(X_CSRF_TOKEN)).isFalse();
    assertThatCookiesAreDeleted(logoutResponse.getHeaders());
  }

  
  @Test
  public void shouldReturnCurrentUser() {
    //given authenticated user
    ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);

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
    ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);
    
    //then endpoints are accessible
    ResponseEntity<Object> responseEntity = testRestTemplate.exchange(CURRENT_USER_URL, HttpMethod.GET, prepareRequestWithCookies(loginResponse), Object.class);
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(responseEntity.getBody()).isNotNull();

    //when user logged out
    logout(loginResponse);

    //then endpoint is not accessible
    responseEntity = testRestTemplate.exchange(CURRENT_USER_URL, HttpMethod.GET, prepareRequestWithCookies(loginResponse), Object.class);
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(responseEntity.getHeaders().containsKey(SET_COOKIE_HEADER)).isFalse();
    assertThat(responseEntity.getHeaders().containsKey(X_CSRF_TOKEN)).isFalse();
  }
  
  @Ignore @Test
  public void testMetricsUserCanAccessMetricsEndpoint() {
    testRestTemplate.getRestTemplate().getInterceptors().add(new BasicAuthenticationInterceptor(METRICS_USER, METRICS_USER));
    ResponseEntity<String> response = testRestTemplate.getForEntity("/actuator",String.class);
    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody()).contains("actuator/info");
    
    ResponseEntity<String> prometheusResponse = testRestTemplate.getForEntity(MetricAssert.ENDPOINT,String.class);
    assertThat(prometheusResponse.getStatusCodeValue()).isEqualTo(200);
    assertThat(prometheusResponse.getBody()).contains("# TYPE system_cpu_usage gauge");
  }
  
  protected HttpHeaders getHeaderWithCSRF(HttpHeaders responseHeaders) {
    HttpHeaders headers = new HttpHeaders();
    if(responseHeaders.containsKey(X_CSRF_HEADER)) {
      String csrfHeader = responseHeaders.get(X_CSRF_HEADER).get(0);
      String csrfToken = responseHeaders.get(X_CSRF_TOKEN).get(0);
      headers.set(csrfHeader,csrfToken);
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

  protected ResponseEntity<Void> login(String username,String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_FORM_URLENCODED);
    
    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("username", username);
    body.add("password", password);
    
    return testRestTemplate.postForEntity(LOGIN_RESOURCE, new HttpEntity<>(body, headers), Void.class);
  }
  
  protected ResponseEntity<String> logout(ResponseEntity<Void> response) {
    HttpEntity<Map<String, String>> request = prepareRequestWithCookies(response);
    return testRestTemplate.postForEntity(LOGOUT_RESOURCE, request, String.class);
  }


  protected void assertThatCookiesAreSet(HttpHeaders headers) {
    assertThat(headers).containsKey(SET_COOKIE_HEADER);
    assertThat(headers.get(SET_COOKIE_HEADER).get(0)).contains(COOKIE_JSESSIONID);
    if(operateProperties.isCsrfPreventionEnabled()) {
      assertThat(headers).containsKey(X_CSRF_TOKEN);
      assertThat(headers.get(X_CSRF_TOKEN).get(0)).isNotBlank();
    }
  }

  protected void assertThatCookiesAreDeleted(HttpHeaders headers) {
    assertThat(headers).containsKey(SET_COOKIE_HEADER);
    List<String> cookies = headers.get(SET_COOKIE_HEADER);
    final String emptyValue = "=;";
    if(operateProperties.isCsrfPreventionEnabled()) {
      assertThat(cookies).anyMatch( (cookie) -> cookie.contains(X_CSRF_TOKEN + emptyValue));
    }
    assertThat(cookies).anyMatch( (cookie) -> cookie.contains(COOKIE_JSESSIONID + emptyValue));
  }
}
