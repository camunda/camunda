/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.qa.util.DependencyInjectionTestExecutionListener;
import io.camunda.operate.util.TestApplication;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".cloud.clusterId=wrong-scope",
      // OAuth2WebConfigurer.SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_ISSUER_URI +
      // "=https://weblogin.cloud.ultrawombat.com/"
      OAuth2WebConfigurer.SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_ISSUER_URI
          + "=http://dummy-issuer/",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestExecutionListeners(
    listeners = DependencyInjectionTestExecutionListener.class,
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@ActiveProfiles({"auth", "test"})
public class JwtAuthenticationIT {

  @MockBean JwtDecoder jwtDecoder;
  @Autowired private TestRestTemplate testRestTemplate;

  @Test
  public void testJWTUnauthorizedDueExpired() {
    // given
    when(jwtDecoder.decode("expired-token"))
        .thenThrow(new JwtValidationException("Token is expired", List.of(new OAuth2Error("23"))));
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_JSON);
    headers.setBearerAuth("expired-token");
    // when
    final ResponseEntity<String> response =
        testRestTemplate.postForEntity(
            "/v1/process-instances/search", new HttpEntity<>("{}", headers), String.class);
    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).contains("Token is expired");
  }

  @Test
  public void testJWTUnauthorizedAnyOtherAuthenticationException() {
    // given
    when(jwtDecoder.decode(any())).thenReturn(aValidJWT());
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_JSON);
    headers.setBearerAuth("token");
    // The JWT is a valid JWT but the scope in configurations is set to 'wrong-scope' which should
    // throw an
    // authentication exception
    // when
    final ResponseEntity<String> response =
        testRestTemplate.postForEntity(
            "/v1/process-instances/search", new HttpEntity<>("{}", headers), String.class);
    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).contains("{\"message\":\"JWT payload validation failed\"}");
  }

  private Jwt aValidJWT() {
    return Jwt.withTokenValue("token")
        .audience(List.of("audience"))
        .header("alg", "HS256")
        .claim("scope", "scope")
        .build();
  }
}
