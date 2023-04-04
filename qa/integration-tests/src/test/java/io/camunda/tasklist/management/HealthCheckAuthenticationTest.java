/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.management;

import static io.camunda.tasklist.webapp.security.TasklistProfileService.AUTH_PROFILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;

import io.camunda.tasklist.es.ElasticsearchConnector;
import io.camunda.tasklist.es.ElasticsearchTask;
import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.management.HealthCheckTest.AddManagementPropertiesInitializer;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.tasklist.webapp.security.ElasticsearchSessionRepository;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import io.camunda.tasklist.webapp.security.WebSecurityConfig;
import io.camunda.tasklist.webapp.security.oauth.OAuth2WebConfigurer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/** Tests the health check with enabled authentication. */
@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      TasklistProperties.class,
      TestApplicationWithNoBeans.class,
      ElsIndicesHealthIndicator.class,
      WebSecurityConfig.class,
      TasklistProfileService.class,
      ElasticsearchSessionRepository.class,
      RetryElasticsearchClient.class,
      ElasticsearchTask.class,
      TasklistProperties.class,
      ElasticsearchConnector.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = AddManagementPropertiesInitializer.class)
@ActiveProfiles(AUTH_PROFILE)
public class HealthCheckAuthenticationTest {

  @Autowired private TestRestTemplate testRestTemplate;

  @MockBean private ElsIndicesHealthIndicator probes;

  @MockBean private OAuth2WebConfigurer oAuth2WebConfigurer;

  @Test
  public void testHealthStateEndpointIsNotSecured() {
    given(probes.getHealth(anyBoolean())).willReturn(Health.up().build());

    final ResponseEntity<String> response =
        testRestTemplate.getForEntity("/actuator/health/liveness", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
