/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest;

import org.camunda.operate.webapp.rest.dto.HealthStateDto;
import org.camunda.operate.util.WebSecurityDisabledConfig;
import org.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import org.camunda.operate.webapp.rest.HealthCheckRestService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the health check with disabled authentication.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = {TestApplicationWithNoBeans.class, HealthCheckRestService.class, WebSecurityDisabledConfig.class},
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class HealthCheckRestServiceTest {

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Test
  public void testHealthState() {
    final ResponseEntity<HealthStateDto> response = testRestTemplate.getForEntity(HealthCheckRestService.HEALTH_CHECK_URL, HealthStateDto.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getState()).isEqualTo("OK");
  }

}
