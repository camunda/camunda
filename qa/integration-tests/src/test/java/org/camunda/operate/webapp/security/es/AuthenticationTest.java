/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.security.es;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.camunda.operate.webapp.security.OperateURIs.*;

import org.camunda.operate.entities.UserEntity;
import org.camunda.operate.es.RetryElasticsearchClient;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.schema.indices.OperateWebSessionIndex;
import org.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import org.camunda.operate.webapp.rest.AuthenticationRestService;
import org.camunda.operate.webapp.rest.dto.UserDto;
import org.camunda.operate.webapp.security.AuthenticationTestable;
import org.camunda.operate.webapp.security.ElasticsearchSessionRepository;
import org.camunda.operate.webapp.security.WebSecurityConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * This test tests:
 * * authentication and security of REST API
 * * /api/authentications/user endpoint to get current user
 * * {@link UserStorage} is mocked (integration with ELS is not tested)
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = {
      TestApplicationWithNoBeans.class,
      OperateProperties.class,
      WebSecurityConfig.class,
      DefaultUserService.class,
      AuthenticationRestService.class,
      ElasticSearchUserDetailsService.class,
      RetryElasticsearchClient.class,ElasticsearchSessionRepository.class, OperateWebSessionIndex.class
  },
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles({ AUTH_PROFILE, "test"})
public class AuthenticationTest implements AuthenticationTestable {

  private static final String USERNAME = "demo";
  private static final String PASSWORD = "demo";
  private static final String FIRSTNAME = "Firstname";
  private static final String LASTNAME = "Lastname";

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Autowired
  private PasswordEncoder encoder;

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
    assertThatCookiesAreSet(response, operateProperties.isCsrfPreventionEnabled());
  }

  @Test
  public void shouldFailWhileLogin() {
    // when
    ResponseEntity<Void> response = login(USERNAME, String.format("%s%d", PASSWORD, 123));

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThatCookiesAreDeleted(response, operateProperties.isCsrfPreventionEnabled());
  }

  @Test
  public void shouldResetCookie() {
    // given
    ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);

    // assume
    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThatCookiesAreSet(loginResponse, operateProperties.isCsrfPreventionEnabled());
    // when
    ResponseEntity<?> logoutResponse = logout(loginResponse);

    assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThatCookiesAreDeleted(logoutResponse, operateProperties.isCsrfPreventionEnabled());
  }


  @Test
  public void shouldReturnCurrentUser() {
    //given authenticated user
    ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);

    UserDto userDto = getCurrentUser(loginResponse);
    assertThat(userDto.getUsername()).isEqualTo(USERNAME);
    assertThat(userDto.getFirstname()).isEqualTo(FIRSTNAME);
    assertThat(userDto.getLastname()).isEqualTo(LASTNAME);
    assertThat(userDto.isCanLogout()).isTrue();
  }

  @Test
  public void testEndpointsNotAccessibleAfterLogout() {
    //when user is logged in
    ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);

    //then endpoint are accessible
    ResponseEntity<Object> responseEntity = testRestTemplate.exchange(CURRENT_USER_URL, HttpMethod.GET, prepareRequestWithCookies(loginResponse), Object.class);
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(responseEntity.getBody()).isNotNull();

    //when user logged out
    logout(loginResponse);

    //then endpoint is not accessible
    responseEntity = testRestTemplate.exchange(CURRENT_USER_URL, HttpMethod.GET, prepareRequestWithCookies(loginResponse), Object.class);
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThatCookiesAreDeleted(responseEntity, operateProperties.isCsrfPreventionEnabled());
  }

  @Test
  public void testCanAccessMetricsEndpoint() {
    ResponseEntity<String> response = testRestTemplate.getForEntity("/actuator",String.class);
    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody()).contains("actuator/info");

    ResponseEntity<String> prometheusResponse = testRestTemplate.getForEntity("/actuator/prometheus",String.class);
    assertThat(prometheusResponse.getStatusCodeValue()).isEqualTo(200);
    assertThat(prometheusResponse.getBody()).contains("# TYPE system_cpu_usage gauge");
  }

  @Override
  public TestRestTemplate getTestRestTemplate() {
    return testRestTemplate;
  }
}
