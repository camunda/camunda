/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import org.camunda.operate.webapp.es.reader.Probes;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.webapp.rest.HealthCheckRestService;
import org.camunda.operate.webapp.security.WebSecurityConfig;
import org.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Tests the health check with enabled authentication.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = {OperateProperties.class,TestApplicationWithNoBeans.class, HealthCheckRestService.class, WebSecurityConfig.class},
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("auth")
public class HealthCheckRestServiceAuthenticationTest {

  @Autowired
  private TestRestTemplate testRestTemplate;

  @MockBean
  private Probes probes;
  
  @Test
  public void testHealthStateEndpointIsNotSecured() {
    given(probes.isLive(any(Long.class))).willReturn(true);
    final ResponseEntity<String> response = testRestTemplate.getForEntity(HealthCheckRestService.HEALTH_CHECK_URL, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

}
