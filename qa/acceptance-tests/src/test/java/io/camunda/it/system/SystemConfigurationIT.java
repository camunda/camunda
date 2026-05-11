/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.system;

import static io.camunda.security.configuration.InitializationConfiguration.DEFAULT_USER_PASSWORD;
import static io.camunda.security.configuration.InitializationConfiguration.DEFAULT_USER_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;

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

    final String body = response.body();
    assertThat(body).contains("\"jobMetrics\"");
    assertThat(body).contains("\"components\"");
    assertThat(body).contains("\"active\"");
    assertThat(body).contains("\"deployment\"");
    assertThat(body).contains("\"isEnterprise\"");
    assertThat(body).contains("\"isMultiTenancyEnabled\"");
    assertThat(body).contains("\"contextPath\"");
    assertThat(body).contains("\"maxRequestSize\"");
    assertThat(body).contains("\"authentication\"");
    assertThat(body).contains("\"canLogout\"");
    assertThat(body).contains("\"isLoginDelegated\"");
    assertThat(body).contains("\"cloud\"");
    assertThat(body).contains("\"organizationId\"");
    assertThat(body).contains("\"clusterId\"");
    assertThat(body).contains("\"stage\"");
    assertThat(body).contains("\"mixpanelToken\"");
    assertThat(body).contains("\"mixpanelAPIHost\"");
  }

  @Test
  void shouldReturnSelfManagedDefaultsForCloudFields() throws IOException, InterruptedException {
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

    // then: on self-managed, cloud fields are null and canLogout is true
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(response.body())
        .contains("\"canLogout\":true")
        .contains("\"isEnterprise\":false")
        .contains("\"isMultiTenancyEnabled\":false");
  }

  private URI configUri() {
    return CAMUNDA_APPLICATION.restAddress().resolve(SYSTEM_CONFIGURATION_PATH);
  }

  private static String basicAuth(final String user, final String password) {
    return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes());
  }
}
