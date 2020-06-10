/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest;

import org.camunda.operate.webapp.rest.dto.HealthStateDto;
import org.camunda.operate.webapp.es.reader.Probes;
import org.camunda.operate.util.WebSecurityDisabledConfig;
import org.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import org.camunda.operate.webapp.rest.HealthCheckRestService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.webapp.rest.dto.HealthStateDto.HEALTH_STATUS_OK;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;

/**
 * Tests the probes check with disabled authentication.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = {TestApplicationWithNoBeans.class, HealthCheckRestService.class, WebSecurityDisabledConfig.class},
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class HealthCheckRestServiceTest {

  @Autowired
  private TestRestTemplate testRestTemplate;

  @MockBean
  private Probes probes;
  @Test
  public void testHealthStateIsOK() {
    given(probes.isLive(any(Long.class))).willReturn(true);
    final ResponseEntity<HealthStateDto> response = testRestTemplate.getForEntity(HealthCheckRestService.HEALTH_CHECK_URL, HealthStateDto.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getState()).isEqualTo(HEALTH_STATUS_OK);
  }
  
  public void testHealthStateIsNotOK() {
    given(probes.isLive(any(Long.class))).willReturn(false);
    final ResponseEntity<HealthStateDto> response = testRestTemplate.getForEntity(HealthCheckRestService.HEALTH_CHECK_URL, HealthStateDto.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

}
