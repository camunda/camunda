/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapp.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapptest.TestWebappApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test for {@link ClusterConfigurationController}.
 *
 * <p>Boots the full {@code TestWebappApplication} with the {@code tmp-webapp} profile active and
 * exercises the endpoint over HTTP. Asserts structural invariants that must hold before any PR
 * merge and that would be broken by accidental field removal or schema drift.
 */
@SpringBootTest(classes = TestWebappApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("tmp-webapp")
class ClusterConfigurationControllerIT {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void shouldReturn200WithJsonContentType() {
    // when
    final ResponseEntity<String> response =
        restTemplate.getForEntity(ClusterConfigurationController.PATH, String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType())
        .as("response must be JSON")
        .isNotNull()
        .satisfies(ct -> assertThat(ct.toString()).startsWith("application/json"));
  }

  @Test
  void shouldReturnNoCacheHeader() {
    // when
    final ResponseEntity<String> response =
        restTemplate.getForEntity(ClusterConfigurationController.PATH, String.class);

    // then
    assertThat(response.getHeaders().getCacheControl())
        .as("cluster config must never be cached")
        .isEqualTo("no-store");
  }

  @Test
  void shouldIncludeAllM1TopLevelFields() {
    // when
    final ResponseEntity<String> response =
        restTemplate.getForEntity(ClusterConfigurationController.PATH, String.class);

    // then — assert all M1 fields are present (catches accidental field removal)
    final String body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body).contains("\"activeComponents\"");
    assertThat(body).contains("\"isEnterprise\"");
    assertThat(body).contains("\"isMultiTenancyEnabled\"");
    assertThat(body).contains("\"canLogout\"");
    assertThat(body).contains("\"isLoginDelegated\"");
    assertThat(body).contains("\"contextPath\"");
    assertThat(body).contains("\"maxRequestSize\"");
    assertThat(body).contains("\"cloud\"");
  }

  @Test
  void shouldIncludeCloudSubObject() {
    // when
    final ResponseEntity<String> response =
        restTemplate.getForEntity(ClusterConfigurationController.PATH, String.class);

    // then — assert cloud sub-object fields are present
    final String body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body).contains("\"organizationId\"");
    assertThat(body).contains("\"clusterId\"");
    assertThat(body).contains("\"stage\"");
    assertThat(body).contains("\"mixpanelToken\"");
    assertThat(body).contains("\"mixpanelAPIHost\"");
  }

  @Test
  void shouldReturnActiveComponentsAsList() {
    // when
    final ResponseEntity<String> response =
        restTemplate.getForEntity(ClusterConfigurationController.PATH, String.class);

    // then — activeComponents must be a JSON array (not null, not a scalar)
    final String body = response.getBody();
    assertThat(body).isNotNull();
    // value is either [] or ["...",...] — never a primitive
    assertThat(body).containsPattern("\"activeComponents\"\\s*:\\s*\\[");
  }
}
