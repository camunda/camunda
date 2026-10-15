/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.es;

import static io.camunda.operate.util.CollectionUtil.map;
import static io.camunda.operate.webapp.security.OperateURIs.COOKIE_JSESSIONID;
import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.X_CSRF_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.ElasticsearchConnector;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.entities.UserEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.UserStore;
import io.camunda.operate.store.elasticsearch.ElasticsearchTaskStore;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.api.v1.dao.ProcessDefinitionDao;
import io.camunda.operate.webapp.api.v1.rest.ProcessDefinitionController;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.ProcessReader;
import io.camunda.operate.webapp.rest.AuthenticationRestService;
import io.camunda.operate.webapp.rest.ProcessRestService;
import io.camunda.operate.webapp.security.OperateURIs;
import io.camunda.operate.webapp.security.SameSiteCookieTomcatContextCustomizer;
import io.camunda.operate.webapp.security.WebSecurityConfig;
import io.camunda.operate.webapp.security.auth.AuthUserService;
import io.camunda.operate.webapp.security.auth.OperateUserDetailsService;
import io.camunda.operate.webapp.security.auth.Role;
import io.camunda.operate.webapp.security.auth.RolePermissionService;
import io.camunda.operate.webapp.security.oauth2.CCSaaSJwtAuthenticationTokenValidator;
import io.camunda.operate.webapp.security.oauth2.Jwt2AuthenticationTokenConverter;
import io.camunda.operate.webapp.security.oauth2.OAuth2WebConfigurer;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      SameSiteCookieTomcatContextCustomizer.class,
      TestApplicationWithNoBeans.class,
      OperateProperties.class,
      WebSecurityConfig.class,
      OAuth2WebConfigurer.class,
      Jwt2AuthenticationTokenConverter.class,
      CCSaaSJwtAuthenticationTokenValidator.class,
      AuthUserService.class,
      RolePermissionService.class,
      AuthenticationRestService.class,
      OperateUserDetailsService.class,
      ElasticsearchTaskStore.class,
      RetryElasticsearchClient.class,
      OperateProfileService.class,
      ElasticsearchConnector.class,
      ProcessRestService.class,
      ProcessDefinitionController.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "camunda.operate.csrf-prevention-enabled=true",
      "server.servlet.session.cookie.name = " + OperateURIs.COOKIE_JSESSIONID
    })
@ActiveProfiles({OperateProfileService.AUTH_PROFILE, "test"})
public class CsrfTokenIT {

  private static final String USER_ID = "demo";
  private static final String PASSWORD = "demo";
  private static final String FIRSTNAME = "Firstname";
  private static final String LASTNAME = "Lastname";
  private static final String SET_COOKIE_HEADER = "Set-Cookie";

  @Autowired private TestRestTemplate testRestTemplate;

  @Autowired private PasswordEncoder encoder;

  @Autowired private OperateProperties operateProperties;

  @MockBean private UserStore userStore;

  @MockBean private ProcessReader processReader;
  @MockBean private ProcessInstanceReader processInstanceReader;
  @MockBean private BatchOperationWriter batchOperationWriter;
  @MockBean private ProcessDefinitionDao processDefinitionDao;

  @Before
  public void setUp() {
    final UserEntity user =
        new UserEntity()
            .setUserId(USER_ID)
            .setPassword(encoder.encode(PASSWORD))
            .setRoles(map(List.of(Role.OPERATOR), Role::name))
            .setDisplayName(FIRSTNAME + " " + LASTNAME)
            .setRoles(List.of(Role.OPERATOR.name()));
    given(userStore.getById(USER_ID)).willReturn(user);
  }

  @Test
  public void loginShouldFailWithoutCSRF() {
    // given a login that does not send a CSRF token, as a cross-site form POST would not
    final ResponseEntity<Void> loginResponse = postLogin(USER_ID, PASSWORD, new HttpHeaders());

    // then
    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(getSessionCookie(loginResponse)).isEmpty();
  }

  @Test
  public void loginPageShouldHandOutCSRFTokenWithoutSession() {
    // when a client without a session gets the login endpoint
    final ResponseEntity<String> response =
        testRestTemplate.getForEntity(LOGIN_RESOURCE, String.class);

    // then it receives a token as both header and cookie, so it can log in next
    assertThat(response.getHeaders().get(X_CSRF_TOKEN)).isNotEmpty();
    assertThat(getCsrfCookie(response)).isPresent();
  }

  @Test
  public void loginShouldSucceedWithCSRF() {
    // when
    final ResponseEntity<Void> loginResponse = login(USER_ID, PASSWORD);

    // then
    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(getSessionCookie(loginResponse)).isPresent();
  }

  @Test
  public void requestToInternalAPIShouldFailWithoutCSRF() {
    final ResponseEntity<Void> loginResponse = login(USER_ID, PASSWORD);
    final var headers = new HttpHeaders();
    getSessionCookie(loginResponse)
        .ifPresent(sessionCookie -> headers.add("Cookie", sessionCookie));
    headers.setContentType(MediaType.APPLICATION_JSON);
    final var request = new HttpEntity<>("{}", headers);
    final var response =
        testRestTemplate.postForEntity("/api/processes/grouped", request, Object.class);
    assertThat(response.getStatusCode())
        .isEqualTo(
            operateProperties.isCsrfPreventionEnabled() ? HttpStatus.FORBIDDEN : HttpStatus.OK);
  }

  @Test
  public void requestToInternalAPIShouldSucceedWithCSRF() {
    final ResponseEntity<Void> loginResponse = login(USER_ID, PASSWORD);
    final var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    // Set session cookie - otherwise you get an 401 UNAUTHORIZED
    getSessionCookie(loginResponse)
        .ifPresent(sessionCookie -> headers.add("Cookie", sessionCookie));
    // Add CSRF token as cookie - otherwise you get an 403 FORBIDDEN
    getCsrfCookie(loginResponse).ifPresent(csrfCookie -> headers.add("Cookie", csrfCookie));
    // Add CSRF token also as header - otherwise you get an 403 FORBIDDEN
    headers.set(X_CSRF_TOKEN, loginResponse.getHeaders().get(X_CSRF_TOKEN).getFirst());
    final var request = new HttpEntity<>("{}", headers);
    final var response =
        testRestTemplate.postForEntity("/api/processes/grouped", request, Object.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  public void requestToPublicAPIShouldSucceedWithoutCSRF() {
    final ResponseEntity<Void> loginResponse = login(USER_ID, PASSWORD);
    final var headers = new HttpHeaders();
    getSessionCookie(loginResponse)
        .ifPresent(sessionCookie -> headers.add("Cookie", sessionCookie));
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth("bearerToken");
    final var request = new HttpEntity<>("{}", headers);
    final var response =
        testRestTemplate.postForEntity("/v1/process-definitions/search", request, Object.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  /** Logs in the way the frontend does: get a CSRF token first, then send it back. */
  private ResponseEntity<Void> login(final String username, final String password) {
    final ResponseEntity<String> tokenResponse =
        testRestTemplate.getForEntity(LOGIN_RESOURCE, String.class);
    final HttpHeaders headers = new HttpHeaders();
    headers.set(X_CSRF_TOKEN, tokenResponse.getHeaders().getFirst(X_CSRF_TOKEN));
    getCsrfCookie(tokenResponse)
        .ifPresent(csrfCookie -> headers.add("Cookie", csrfCookie.split(";")[0]));
    return postLogin(username, password, headers);
  }

  private ResponseEntity<Void> postLogin(
      final String username, final String password, final HttpHeaders headers) {
    headers.setContentType(APPLICATION_FORM_URLENCODED);

    final MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("username", username);
    body.add("password", password);

    return testRestTemplate.postForEntity(
        LOGIN_RESOURCE, new HttpEntity<>(body, headers), Void.class);
  }

  /**
   * The last CSRF cookie the response sets, as a browser would keep it. A login that sent a token
   * first clears that token and then sets the new one, so the response sets the cookie twice.
   */
  private Optional<String> getCsrfCookie(final ResponseEntity<?> response) {
    return getCookies(response).stream()
        .filter(key -> key.startsWith(X_CSRF_TOKEN))
        .reduce((first, second) -> second);
  }

  private Optional<String> getSessionCookie(final ResponseEntity<?> response) {
    return getCookies(response).stream()
        .filter(key -> key.startsWith(COOKIE_JSESSIONID))
        .findFirst();
  }

  private List<String> getCookies(final ResponseEntity<?> response) {
    return Optional.ofNullable(response.getHeaders().get(SET_COOKIE_HEADER)).orElse(List.of());
  }
}
