/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import io.camunda.authentication.service.CamundaUserService;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.TestApplication;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      // OAuth2WebConfigurer.SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_ISSUER_URI +
      // "=https://weblogin.cloud.ultrawombat.com/"
      OAuth2WebConfigurer.SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_ISSUER_URI
          + "=http://dummy-issuer/"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"auth", "test"})
public class JwtAuthenticationIT {

  @MockBean JwtDecoder jwtDecoder;
  @MockBean CamundaUserService camundaUserService;
  @Autowired private TestRestTemplate testRestTemplate;

  @Test
  public void testJWTUnauthorized() {
    // given
    when(jwtDecoder.decode("expired-token"))
        .thenThrow(new JwtValidationException("Token is expired", List.of(new OAuth2Error("23"))));
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_JSON);
    headers.setBearerAuth("expired-token");
    // when
    final ResponseEntity<String> response =
        testRestTemplate.exchange(
            TasklistURIs.USERS_URL_V1.concat("/current"),
            HttpMethod.GET,
            new HttpEntity<>(headers), // Only headers; no body for GET requests
            String.class);
    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).contains("Token is expired");
  }
}
