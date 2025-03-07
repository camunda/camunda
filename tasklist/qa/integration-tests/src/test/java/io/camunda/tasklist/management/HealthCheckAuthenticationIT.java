/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.management;

import static io.camunda.tasklist.webapp.security.TasklistProfileService.AUTH_PROFILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;

import io.camunda.tasklist.JacksonConfig;
import io.camunda.tasklist.connect.ElasticsearchConnector;
import io.camunda.tasklist.es.ElasticsearchInternalTask;
import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.tasklist.webapp.security.ElasticsearchSessionRepository;
import io.camunda.tasklist.webapp.security.TasklistProfileServiceImpl;
import io.camunda.tasklist.webapp.security.WebSecurityConfig;
import io.camunda.tasklist.webapp.security.oauth.OAuth2WebConfigurer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/** Tests the health check with enabled authentication. */
@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
      TasklistProperties.class,
      TestApplicationWithNoBeans.class,
      SearchEngineHealthIndicator.class,
      WebSecurityConfig.class,
      TasklistProfileServiceImpl.class,
      ElasticsearchSessionRepository.class,
      RetryElasticsearchClient.class,
      ElasticsearchInternalTask.class,
      TasklistProperties.class,
      ElasticsearchConnector.class,
      JacksonConfig.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({AUTH_PROFILE, "tasklist", "test", "standalone"})
public class HealthCheckAuthenticationIT {

  @Autowired private TestRestTemplate testRestTemplate;

  @MockBean private UserDetailsService userDetailsService;
  @MockBean private SearchEngineHealthIndicator probes;

  @MockBean private OAuth2WebConfigurer oAuth2WebConfigurer;

  @LocalManagementPort private int managementPort;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isElasticSearch());
  }

  @Test
  public void testHealthStateEndpointIsNotSecured() {
    given(probes.getHealth(anyBoolean())).willReturn(Health.up().build());

    final ResponseEntity<String> response =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/health/liveness", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
