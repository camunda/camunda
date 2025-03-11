/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.ElasticsearchConnector;
import io.camunda.operate.connect.OpensearchConnector;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.management.IndicesHealthIndicator;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.rest.HealthCheckIT.AddManagementPropertiesInitializer;
import io.camunda.operate.store.TaskStore;
import io.camunda.operate.store.elasticsearch.ElasticsearchTaskStore;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import io.camunda.operate.store.opensearch.OpensearchTaskStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.security.WebSecurityConfig;
import io.camunda.operate.webapp.security.oauth2.CCSaaSJwtAuthenticationTokenValidator;
import io.camunda.operate.webapp.security.oauth2.Jwt2AuthenticationTokenConverter;
import io.camunda.operate.webapp.security.oauth2.OAuth2WebConfigurer;
import java.io.IOException;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/** Tests the health check with enabled authentication. */
@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      OperateProperties.class,
      TestApplicationWithNoBeans.class,
      IndicesHealthIndicator.class,
      OAuth2WebConfigurer.class,
      Jwt2AuthenticationTokenConverter.class,
      CCSaaSJwtAuthenticationTokenValidator.class,
      WebSecurityConfig.class,
      ElasticsearchTaskStore.class,
      RetryElasticsearchClient.class,
      OperateProfileService.class,
      ElasticsearchConnector.class,
      OpensearchTaskStore.class,
      RichOpenSearchClient.class,
      OpensearchConnector.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class,
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = AddManagementPropertiesInitializer.class)
@ActiveProfiles(OperateProfileService.AUTH_PROFILE)
public class HealthCheckAuthenticationIT {

  @MockBean private UserDetailsService userDetailsService;

  @Autowired private TestRestTemplate testRestTemplate;

  @MockBean private IndicesHealthIndicator probes;

  @Autowired private TaskStore taskStore;

  @LocalManagementPort private int managementPort;

  @Test
  public void testHealthStateEndpointIsNotSecured() {
    given(probes.getHealth(anyBoolean())).willReturn(Health.up().build());

    final ResponseEntity<String> response =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/health/liveness", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Ignore // unless you have a reindex task in ELS for mentioned indices
  @Test
  public void testAccessElasticsearchTaskStatusFields() throws IOException {
    assertThat(
            taskStore.getRunningReindexTasksIdsFor(
                "operate-flownode-instances-1.3.0_*", "operate-flownode-instance-8.2.0_"))
        .isEmpty();
    assertThat(
            taskStore.getRunningReindexTasksIdsFor(
                "operate-flownode-instance-1.3.0_*", "operate-flownode-instance-8.2.0_"))
        .hasSize(1);
  }
}
