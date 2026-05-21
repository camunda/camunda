/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.system;

import static io.camunda.security.api.model.config.initialization.InitializationConfiguration.DEFAULT_USER_PASSWORD;
import static io.camunda.security.api.model.config.initialization.InitializationConfiguration.DEFAULT_USER_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class SystemConfigurationIT {

  private static final String SYSTEM_CONFIGURATION_PATH = "v2/system/configuration";

  @MultiDbTestApplication
  private static final TestCamundaApplication CAMUNDA_APPLICATION =
      new TestCamundaApplication().withBasicAuth().withAuthenticatedAccess();

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @AutoClose private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void shouldReturn401WithoutAuthentication() throws IOException, InterruptedException {
    // given
    final HttpRequest request = HttpRequest.newBuilder().uri(configUri()).GET().build();

    // when
    final HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
  }

  @Test
  void shouldReturnAllConfigurationSectionsWithSelfManagedDefaults()
      throws IOException, InterruptedException {
    // given
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(configUri())
            .header("Authorization", basicAuth(DEFAULT_USER_USERNAME, DEFAULT_USER_PASSWORD))
            .GET()
            .build();

    // when
    final HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

    final JsonNode root = OBJECT_MAPPER.readTree(response.body());

    final JsonNode jobMetrics = root.get("jobMetrics");
    assertThat(jobMetrics).isNotNull();
    assertThat(jobMetrics.get("enabled").booleanValue()).isTrue();
    assertThat(jobMetrics.get("exportInterval").textValue()).isEqualTo("PT5M");
    assertThat(jobMetrics.get("maxWorkerNameLength").intValue()).isEqualTo(100);
    assertThat(jobMetrics.get("maxJobTypeLength").intValue()).isEqualTo(100);
    assertThat(jobMetrics.get("maxTenantIdLength").intValue()).isEqualTo(30);
    assertThat(jobMetrics.get("maxUniqueKeys").intValue()).isEqualTo(9500);

    final JsonNode components = root.get("components");
    assertThat(components).isNotNull();
    assertThat(components.get("active").isArray()).isTrue();

    final JsonNode deployment = root.get("deployment");
    assertThat(deployment).isNotNull();
    assertThat(deployment.get("isEnterprise").booleanValue()).isFalse();
    assertThat(deployment.get("isMultiTenancyEnabled").booleanValue()).isFalse();
    assertThat(deployment.get("contextPath").textValue()).isEqualTo("");
    assertThat(deployment.get("maxRequestSize").longValue()).isPositive();

    final JsonNode authentication = root.get("authentication");
    assertThat(authentication).isNotNull();
    assertThat(authentication.get("canLogout").booleanValue()).isTrue();
    assertThat(authentication.get("isLoginDelegated").booleanValue()).isFalse();

    final JsonNode cloud = root.get("cloud");
    assertThat(cloud).isNotNull();
    assertThat(cloud.get("organizationId").isNull()).isTrue();
    assertThat(cloud.get("clusterId").isNull()).isTrue();
    assertThat(cloud.get("stage").isNull()).isTrue();
    assertThat(cloud.get("mixpanelToken").isNull()).isTrue();
    assertThat(cloud.get("mixpanelAPIHost").isNull()).isTrue();
  }

  private URI configUri() {
    return CAMUNDA_APPLICATION.restAddress().resolve(SYSTEM_CONFIGURATION_PATH);
  }

  private static String basicAuth(final String user, final String password) {
    return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes());
  }
}
