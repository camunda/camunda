/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.es;

import static io.camunda.operate.util.CollectionUtil.map;
import static io.camunda.operate.webapp.security.Permission.READ;
import static io.camunda.operate.webapp.security.Permission.WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.ElasticsearchConnector;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.entities.UserEntity;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.store.UserStore;
import io.camunda.operate.store.elasticsearch.ElasticsearchTaskStore;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.rest.AuthenticationRestService;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.AuthenticationTestable;
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
import jakarta.json.Json;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * This test tests: * authentication and security of REST API * /api/authentications/user endpoint
 * to get current user * {@link UserStore} is mocked (integration with ELS is not tested)
 */
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
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "management.endpoints.web.exposure.include = info,prometheus,loggers,usage-metrics",
      "server.servlet.session.cookie.name = " + OperateURIs.COOKIE_JSESSIONID
    })
@ActiveProfiles({OperateProfileService.AUTH_PROFILE, "test"})
public class AuthenticationIT implements AuthenticationTestable {

  private static final String USER_ID = "demo";
  private static final String PASSWORD = "demo";
  private static final String FIRSTNAME = "Firstname";
  private static final String LASTNAME = "Lastname";

  @Autowired private TestRestTemplate testRestTemplate;

  @Autowired private PasswordEncoder encoder;

  @MockBean private UserStore userStore;

  @LocalManagementPort private int managementPort;

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
  public void shouldSetCookie() {
    // given
    // when
    final ResponseEntity<Void> response = login(USER_ID, PASSWORD);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThatCookiesAndSecurityHeadersAreSet(response);
  }

  @Test
  public void shouldFailWhileLogin() {
    // when
    final ResponseEntity<Void> response = login(USER_ID, String.format("%s%d", PASSWORD, 123));

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThatCookiesAreDeleted(response);
  }

  @Test
  public void shouldResetCookie() {
    // given
    final ResponseEntity<Void> loginResponse = login(USER_ID, PASSWORD);

    // assume
    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThatCookiesAndSecurityHeadersAreSet(loginResponse);
    // when
    final ResponseEntity<?> logoutResponse = logout(loginResponse);

    assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThatCookiesAreDeleted(logoutResponse);
  }

  @Test
  public void shouldReturnCurrentUser() {
    // given authenticated user
    final ResponseEntity<Void> loginResponse = login(USER_ID, PASSWORD);

    final UserDto userDto = getCurrentUser(loginResponse);
    assertThat(userDto.getUserId()).isEqualTo(USER_ID);
    assertThat(userDto.getDisplayName()).isEqualTo(FIRSTNAME + " " + LASTNAME);
    assertThat(userDto.isCanLogout()).isTrue();
    assertThat(userDto.getPermissions()).isEqualTo(List.of(READ, WRITE));
  }

  @Test
  public void testEndpointsNotAccessibleAfterLogout() {
    // when user is logged in
    final ResponseEntity<Void> loginResponse = login(USER_ID, PASSWORD);

    // then endpoint are accessible
    ResponseEntity<Object> responseEntity =
        testRestTemplate.exchange(
            CURRENT_USER_URL,
            HttpMethod.GET,
            prepareRequestWithCookies(loginResponse),
            Object.class);
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(responseEntity.getBody()).isNotNull();

    // when user logged out
    logout(loginResponse);

    // then endpoint is not accessible
    responseEntity =
        testRestTemplate.exchange(
            CURRENT_USER_URL,
            HttpMethod.GET,
            prepareRequestWithCookies(loginResponse),
            Object.class);
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThatCookiesAreDeleted(responseEntity);
  }

  @Test
  public void testCanAccessMetricsEndpoint() {
    final ResponseEntity<String> response =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator", String.class);
    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody()).contains("actuator/info");

    final ResponseEntity<String> prometheusResponse =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/prometheus", String.class);
    assertThat(prometheusResponse.getStatusCodeValue()).isEqualTo(200);
    assertThat(prometheusResponse.getBody()).contains("# TYPE system_cpu_usage gauge");
  }

  @Test
  public void testCanReadAndWriteLoggersActuatorEndpoint() {
    ResponseEntity<String> response =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/loggers/io.camunda.operate",
            String.class);
    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody()).contains("\"configuredLevel\":\"DEBUG\"");

    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    final HttpEntity<String> request =
        new HttpEntity<>(
            Json.createObjectBuilder().add("configuredLevel", "TRACE").build().toString(), headers);
    response =
        testRestTemplate.postForEntity(
            "http://localhost:" + managementPort + "/actuator/loggers/io.camunda.operate",
            request,
            String.class);
    assertThat(response.getStatusCodeValue()).isEqualTo(204);

    response =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/loggers/io.camunda.operate",
            String.class);
    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody()).contains("\"configuredLevel\":\"TRACE\"");
  }

  @Override
  public TestRestTemplate getTestRestTemplate() {
    return testRestTemplate;
  }
}
