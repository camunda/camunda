/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapp.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.webapptest.TestWebappApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/** Integration test for {@link ClusterConfigurationController}. */
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
    assertThat(response.getHeaders().getContentType()).as("response must be JSON").isNotNull();
    final var contentType = response.getHeaders().getContentType();
    assertThat(contentType.getType())
        .as("media type must be application/json")
        .isEqualTo("application");
    assertThat(contentType.getSubtype()).isEqualTo("json");
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
  void shouldIncludeAllM1TopLevelFields() throws Exception {
    // when
    final ResponseEntity<String> response =
        restTemplate.getForEntity(ClusterConfigurationController.PATH, String.class);

    // then
    final var tree = new ObjectMapper().readTree(response.getBody());
    assertThat(tree.has("activeComponents")).as("activeComponents field must be present").isTrue();
    assertThat(tree.has("isEnterprise")).as("isEnterprise field must be present").isTrue();
    assertThat(tree.has("isMultiTenancyEnabled"))
        .as("isMultiTenancyEnabled field must be present")
        .isTrue();
    assertThat(tree.has("canLogout")).as("canLogout field must be present").isTrue();
    assertThat(tree.has("isLoginDelegated")).as("isLoginDelegated field must be present").isTrue();
    assertThat(tree.has("contextPath")).as("contextPath field must be present").isTrue();
    assertThat(tree.has("maxRequestSize")).as("maxRequestSize field must be present").isTrue();
    assertThat(tree.has("cloud")).as("cloud field must be present").isTrue();
  }

  @Test
  void shouldIncludeCloudSubObject() throws Exception {
    // when
    final ResponseEntity<String> response =
        restTemplate.getForEntity(ClusterConfigurationController.PATH, String.class);

    // then — assert cloud sub-object fields are present
    final var tree = new ObjectMapper().readTree(response.getBody());
    assertThat(tree.has("cloud")).as("cloud field must be present").isTrue();
    final var cloud = tree.get("cloud");
    assertThat(cloud.has("organizationId")).as("cloud.organizationId must be present").isTrue();
    assertThat(cloud.has("clusterId")).as("cloud.clusterId must be present").isTrue();
    assertThat(cloud.has("stage")).as("cloud.stage must be present").isTrue();
    assertThat(cloud.has("mixpanelToken")).as("cloud.mixpanelToken must be present").isTrue();
    assertThat(cloud.has("mixpanelAPIHost")).as("cloud.mixpanelAPIHost must be present").isTrue();
  }

  @Test
  void shouldReturnActiveComponentsAsList() throws Exception {
    // when
    final ResponseEntity<String> response =
        restTemplate.getForEntity(ClusterConfigurationController.PATH, String.class);

    // then — activeComponents must be a JSON array (not null, not a scalar)
    final String body = response.getBody();
    assertThat(body).isNotNull();

    final var tree = new ObjectMapper().readTree(body);
    assertThat(tree.has("activeComponents")).as("activeComponents field must be present").isTrue();
    assertThat(tree.get("activeComponents").isArray())
        .as("activeComponents must be a JSON array, not a primitive or object")
        .isTrue();
  }
}
