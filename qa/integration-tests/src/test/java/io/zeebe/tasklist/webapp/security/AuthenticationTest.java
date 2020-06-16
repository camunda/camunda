/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.security;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphql.spring.boot.test.GraphQLResponse;
import io.zeebe.tasklist.entities.UserEntity;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.util.MetricAssert;
import io.zeebe.tasklist.util.TasklistIntegrationTest;
import io.zeebe.tasklist.webapp.security.es.UserStorage;
import static io.zeebe.tasklist.webapp.security.WebSecurityConfig.COOKIE_JSESSIONID;
import static io.zeebe.tasklist.webapp.security.WebSecurityConfig.LOGIN_RESOURCE;
import static io.zeebe.tasklist.webapp.security.WebSecurityConfig.LOGOUT_RESOURCE;
import static io.zeebe.tasklist.webapp.security.WebSecurityConfig.X_CSRF_HEADER;
import static io.zeebe.tasklist.webapp.security.WebSecurityConfig.X_CSRF_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * This test tests:
 * * authentication and security of REST API
 * * /api/authentications/user endpoint to get current user
 * * {@link UserStorage} is mocked (integration with ELS is not tested)
 */
@ActiveProfiles({"auth", "test"})
public class AuthenticationTest extends TasklistIntegrationTest {

  private static final String SET_COOKIE_HEADER = "Set-Cookie";

  private static final String GRAPHQL_URL = "/graphql";
  private static final String CURRENT_USER_QUERY = "{currentUser{ username \n lastname \n firstname }}";

  private static final String USERNAME = "demo";
  private static final String PASSWORD = "demo";
  private static final String FIRSTNAME = "Firstname";
  private static final String LASTNAME = "Lastname";

  @Autowired
  private TasklistProperties tasklistProperties;
  
  @Autowired
  private TestRestTemplate testRestTemplate;

  @Autowired
  private PasswordEncoder encoder;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private UserStorage userStorage;

  @Before
  public void setUp() {
    UserEntity user = new UserEntity()
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
  public void shouldReturnIndexPageForUnknownURI() {
    // given
    ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);

    //when
    final ResponseEntity<String> responseEntity = testRestTemplate
        .exchange("/does-not-exist", HttpMethod.GET, prepareRequestWithCookies(loginResponse.getHeaders()), String.class);

    //then
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
  //TODO: How can we check that this is the index page?
    assertThat(responseEntity.getBody()).contains("<!doctype html><html lang=\"en\">"); 
  }
  
  @Test
  public void shouldReturnCurrentUser() {
    //given authenticated user
    ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);

    //when
    final ResponseEntity<String> responseEntity = testRestTemplate
        .exchange(GRAPHQL_URL, HttpMethod.POST, prepareRequestWithCookies(loginResponse.getHeaders(), CURRENT_USER_QUERY), String.class);

    //then
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    GraphQLResponse response = new GraphQLResponse(responseEntity, objectMapper);
    assertThat(response.get("$.data.currentUser.username")).isEqualTo(USERNAME);
    //TODO issue #45
//    assertThat(response.get("$.data.currentUser.firstname")).isEqualTo(FIRSTNAME);
//    assertThat(response.get("$.data.currentUser.lastname")).isEqualTo(LASTNAME);
  }

  @Test
  public void testEndpointsNotAccessibleAfterLogout() {
    //when user is logged in
    ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);

    //then endpoint are accessible
    ResponseEntity<String> responseEntity = testRestTemplate
        .exchange(GRAPHQL_URL, HttpMethod.POST, prepareRequestWithCookies(loginResponse.getHeaders(), CURRENT_USER_QUERY), String.class);
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(responseEntity.getBody()).isNotNull();

    //when user logged out
    ResponseEntity<String> logoutResponse = logout(loginResponse);

    //then endpoint is not accessible
    responseEntity = testRestTemplate.exchange(GRAPHQL_URL, HttpMethod.POST, prepareRequestWithCookies(logoutResponse.getHeaders(), CURRENT_USER_QUERY), String.class);
    assertThat(responseEntity.getStatusCode()).isIn(HttpStatus.FORBIDDEN,HttpStatus.UNAUTHORIZED);
    assertThat(responseEntity.getHeaders().containsKey(X_CSRF_TOKEN)).isFalse();
  }

  @Test
  public void testCanAccessMetricsEndpoint() {
    ResponseEntity<String> response = testRestTemplate.getForEntity("/actuator",String.class);
    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody()).contains("actuator/info");
    
    ResponseEntity<String> prometheusResponse = testRestTemplate.getForEntity(MetricAssert.ENDPOINT,String.class);
    assertThat(prometheusResponse.getStatusCodeValue()).isEqualTo(200);
    assertThat(prometheusResponse.getBody()).contains("# TYPE system_cpu_usage gauge");
  }
  
  private HttpHeaders getHeaderWithCSRF(HttpHeaders responseHeaders) {
    HttpHeaders headers = new HttpHeaders();
    if(responseHeaders.containsKey(X_CSRF_HEADER)) {
      String csrfHeader = responseHeaders.get(X_CSRF_HEADER).get(0);
      String csrfToken = responseHeaders.get(X_CSRF_TOKEN).get(0);
      headers.set(csrfHeader,csrfToken);
    }
    return headers;
  }

  private HttpEntity<Map> prepareRequestWithCookies(HttpHeaders httpHeaders) {
    return prepareRequestWithCookies(httpHeaders, null);
  }

  private HttpEntity<Map> prepareRequestWithCookies(HttpHeaders httpHeaders, String graphQlQuery) {

    HttpHeaders headers = getHeaderWithCSRF(httpHeaders);
    headers.setContentType(APPLICATION_JSON);
    headers.add("Cookie", httpHeaders.get(SET_COOKIE_HEADER).get(0));

    HashMap<String, String> body = new HashMap<>();
    if (graphQlQuery != null) {
      body.put("query", graphQlQuery);
    }

    return new HttpEntity<>(body, headers);
  }

  private ResponseEntity<Void> login(String username,String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_FORM_URLENCODED);
    
    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("username", username);
    body.add("password", password);
    
    return testRestTemplate.postForEntity(LOGIN_RESOURCE, new HttpEntity<>(body, headers), Void.class);
  }
  
  private ResponseEntity<String> logout(ResponseEntity<Void> response) {
    HttpEntity<Map> request = prepareRequestWithCookies(response.getHeaders());
    return testRestTemplate.postForEntity(LOGOUT_RESOURCE, request, String.class);
  }

  private void assertThatCookiesAreSet(HttpHeaders headers) {
    assertThat(headers).containsKey(SET_COOKIE_HEADER);
    assertThat(headers.get(SET_COOKIE_HEADER).get(0)).contains(COOKIE_JSESSIONID);
    if(tasklistProperties.isCsrfPreventionEnabled()) {
      assertThat(headers).containsKey(X_CSRF_TOKEN);
      assertThat(headers.get(X_CSRF_TOKEN).get(0)).isNotBlank();
    }
  }

  private void assertThatCookiesAreDeleted(HttpHeaders headers) {
    assertThat(headers).containsKey(SET_COOKIE_HEADER);
    List<String> cookies = headers.get(SET_COOKIE_HEADER);
    final String emptyValue = "=;";
    if(tasklistProperties.isCsrfPreventionEnabled()) {
      assertThat(cookies).anyMatch( (cookie) -> cookie.contains(X_CSRF_TOKEN + emptyValue));
    }
    assertThat(cookies).anyMatch( (cookie) -> cookie.contains(COOKIE_JSESSIONID + emptyValue));
  }
}
