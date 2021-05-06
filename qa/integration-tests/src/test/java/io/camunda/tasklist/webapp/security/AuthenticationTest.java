/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security;

import static io.camunda.tasklist.webapp.security.TasklistURIs.COOKIE_JSESSIONID;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGOUT_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.X_CSRF_HEADER;
import static io.camunda.tasklist.webapp.security.TasklistURIs.X_CSRF_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.MetricAssert;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.webapp.security.es.UserStorage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.util.Lists;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** This tests: authentication and security over GraphQL API /currentUser to get current user */
@ActiveProfiles({TasklistURIs.AUTH_PROFILE, "test"})
public class AuthenticationTest extends TasklistIntegrationTest {

  private static final String SET_COOKIE_HEADER = "Set-Cookie";

  private static final String GRAPHQL_URL = "/graphql";
  private static final String CURRENT_USER_QUERY =
      "{currentUser{ username \n lastname \n firstname }}";

  private static final String USERNAME = "demo";
  private static final String PASSWORD = "demo";
  private static final String FIRSTNAME = "Firstname";
  private static final String LASTNAME = "Lastname";

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private TestRestTemplate testRestTemplate;

  @Autowired private PasswordEncoder encoder;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private UserStorage userStorage;

  @Before
  public void setUp() {
    final UserEntity user =
        new UserEntity()
            .setUsername(USERNAME)
            .setPassword(encoder.encode(PASSWORD))
            .setRole("USER")
            .setFirstname(FIRSTNAME)
            .setLastname(LASTNAME);
    given(userStorage.getByName(USERNAME)).willReturn(user);
  }

  @Test
  public void shouldSetCookieAndCSRFToken() {
    // given
    // when
    final ResponseEntity<Void> response = login(USERNAME, PASSWORD);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThatCookiesAreSet(response.getHeaders());
  }

  @Test
  public void shouldFailWhileLogin() {
    // when
    final ResponseEntity<Void> response = login(USERNAME, String.format("%s%d", PASSWORD, 123));

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getHeaders().containsKey(SET_COOKIE_HEADER)).isFalse();
    assertThat(response.getHeaders().containsKey(X_CSRF_TOKEN)).isFalse();
  }

  @Test
  public void shouldResetCookie() {
    // given
    final ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);

    // assume
    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(loginResponse.getHeaders()).containsKey(SET_COOKIE_HEADER);
    assertThat(getSessionCookie(loginResponse.getHeaders()).orElse("")).contains(COOKIE_JSESSIONID);
    // when
    final ResponseEntity<String> logoutResponse = logout(loginResponse);

    assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(logoutResponse.getHeaders().containsKey(X_CSRF_TOKEN)).isFalse();
    assertThatCookiesAreDeleted(logoutResponse.getHeaders());
  }

  @Test
  public void shouldReturnIndexPageForUnknownURI() {
    // given
    final ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);

    // when
    final ResponseEntity<String> responseEntity =
        testRestTemplate.exchange(
            "/does-not-exist",
            HttpMethod.GET,
            prepareRequestWithCookies(loginResponse.getHeaders()),
            String.class);

    // then
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    // TODO: How can we check that this is the index page?
    assertThat(responseEntity.getBody()).contains("<!doctype html><html lang=\"en\">");
  }

  @Test
  public void shouldReturnCurrentUser() {
    // given authenticated user
    final ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);

    // when
    final ResponseEntity<String> responseEntity =
        testRestTemplate.exchange(
            GRAPHQL_URL,
            HttpMethod.POST,
            prepareRequestWithCookies(loginResponse.getHeaders(), CURRENT_USER_QUERY),
            String.class);

    // then
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    final GraphQLResponse response = new GraphQLResponse(responseEntity, objectMapper);
    assertThat(response.get("$.data.currentUser.username")).isEqualTo(USERNAME);
    assertThat(response.get("$.data.currentUser.firstname")).isEqualTo(FIRSTNAME);
    assertThat(response.get("$.data.currentUser.lastname")).isEqualTo(LASTNAME);
  }

  @Test
  public void testEndpointsNotAccessibleAfterLogout() {
    // when user is logged in
    final ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);

    // then endpoint are accessible
    ResponseEntity<String> responseEntity =
        testRestTemplate.exchange(
            GRAPHQL_URL,
            HttpMethod.POST,
            prepareRequestWithCookies(loginResponse.getHeaders(), CURRENT_USER_QUERY),
            String.class);
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(responseEntity.getBody()).isNotNull();

    // when user logged out
    final ResponseEntity<String> logoutResponse = logout(loginResponse);

    // then endpoint is not accessible
    responseEntity =
        testRestTemplate.exchange(
            GRAPHQL_URL,
            HttpMethod.POST,
            prepareRequestWithCookies(logoutResponse.getHeaders(), CURRENT_USER_QUERY),
            String.class);
    assertThat(responseEntity.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED);
    assertThat(responseEntity.getHeaders().containsKey(X_CSRF_TOKEN)).isFalse();
  }

  @Test
  public void testCanAccessMetricsEndpoint() {
    final ResponseEntity<String> response =
        testRestTemplate.getForEntity("/actuator", String.class);
    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody()).contains("actuator/info");

    final ResponseEntity<String> prometheusResponse =
        testRestTemplate.getForEntity(MetricAssert.ENDPOINT, String.class);
    assertThat(prometheusResponse.getStatusCodeValue()).isEqualTo(200);
    assertThat(prometheusResponse.getBody()).contains("# TYPE system_cpu_usage gauge");
  }

  @Test
  public void testCanReadAndWriteLoggersActuatorEndpoint() throws JSONException {
    ResponseEntity<String> response =
        testRestTemplate.getForEntity("/actuator/loggers/io.camunda.tasklist", String.class);
    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody()).contains("\"configuredLevel\" : \"DEBUG\"");

    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    final HttpEntity<String> request =
        new HttpEntity<String>(
            new JSONObject().put("configuredLevel", "TRACE").toString(), headers);
    response =
        testRestTemplate.postForEntity(
            "/actuator/loggers/io.camunda.tasklist", request, String.class);
    assertThat(response.getStatusCodeValue()).isEqualTo(204);

    response = testRestTemplate.getForEntity("/actuator/loggers/io.camunda.tasklist", String.class);
    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody()).contains("\"configuredLevel\" : \"TRACE\"");
  }

  private HttpHeaders getHeaderWithCSRF(HttpHeaders responseHeaders) {
    final HttpHeaders headers = new HttpHeaders();
    if (responseHeaders.containsKey(X_CSRF_HEADER)) {
      final String csrfHeader = responseHeaders.get(X_CSRF_HEADER).get(0);
      final String csrfToken = responseHeaders.get(X_CSRF_TOKEN).get(0);
      headers.set(csrfHeader, csrfToken);
    }
    return headers;
  }

  private HttpEntity<Map> prepareRequestWithCookies(HttpHeaders httpHeaders) {
    return prepareRequestWithCookies(httpHeaders, null);
  }

  private HttpEntity<Map> prepareRequestWithCookies(HttpHeaders httpHeaders, String graphQlQuery) {

    final HttpHeaders headers = getHeaderWithCSRF(httpHeaders);
    headers.setContentType(APPLICATION_JSON);
    headers.add("Cookie", getCookiesAsString(httpHeaders));

    final HashMap<String, String> body = new HashMap<>();
    if (graphQlQuery != null) {
      body.put("query", graphQlQuery);
    }

    return new HttpEntity<>(body, headers);
  }

  private ResponseEntity<Void> login(String username, String password) {
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_FORM_URLENCODED);

    final MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("username", username);
    body.add("password", password);

    return testRestTemplate.postForEntity(
        LOGIN_RESOURCE, new HttpEntity<>(body, headers), Void.class);
  }

  private ResponseEntity<String> logout(ResponseEntity<Void> response) {
    final HttpEntity<Map> request = prepareRequestWithCookies(response.getHeaders());
    return testRestTemplate.postForEntity(LOGOUT_RESOURCE, request, String.class);
  }

  private void assertThatCookiesAreSet(HttpHeaders headers) {
    assertThat(headers).containsKey(SET_COOKIE_HEADER);
    assertThat(getSessionCookie(headers).orElse("")).contains(COOKIE_JSESSIONID);
    if (tasklistProperties.isCsrfPreventionEnabled()) {
      assertThat(headers).containsKey(X_CSRF_TOKEN);
      assertThat(headers.get(X_CSRF_TOKEN).get(0)).isNotBlank();
    }
  }

  private void assertThatCookiesAreDeleted(HttpHeaders headers) {
    assertThat(headers).containsKey(SET_COOKIE_HEADER);
    final List<String> cookies = headers.get(SET_COOKIE_HEADER);
    final String emptyValue = "=;";
    if (tasklistProperties.isCsrfPreventionEnabled()) {
      assertThat(cookies).anyMatch((cookie) -> cookie.contains(X_CSRF_TOKEN + emptyValue));
    }
    assertThat(cookies).anyMatch((cookie) -> cookie.contains(COOKIE_JSESSIONID + emptyValue));
  }

  private String getCookiesAsString(HttpHeaders headers) {
    return String.format(
        "%s; %s", getSessionCookie(headers).orElse(""), getCSRFCookie(headers).orElse(""));
  }

  private Optional<String> getSessionCookie(HttpHeaders headers) {
    return getCookies(headers).stream().filter(key -> key.contains(COOKIE_JSESSIONID)).findFirst();
  }

  private Optional<String> getCSRFCookie(HttpHeaders headers) {
    return getCookies(headers).stream().filter(key -> key.contains(X_CSRF_TOKEN)).findFirst();
  }

  private List<String> getCookies(HttpHeaders headers) {
    return Optional.ofNullable(headers.get(SET_COOKIE_HEADER)).orElse(Lists.emptyList());
  }
}
