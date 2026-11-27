/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.api.ExportingStateController;
import io.camunda.zeebe.dynamic.config.api.ExportingStateController.ByTenant;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootTest(
    classes = ExportingEndpointWebTest.TestConfig.class,
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "management.server.port=0",
      "management.endpoints.web.exposure.include=exporting"
    })
final class ExportingEndpointWebTest {

  @LocalManagementPort private int managementPort;
  @Autowired private ExportingStateController exportingStateController;

  private ByTenant byTenant;

  @BeforeEach
  void setUp() {
    reset(exportingStateController);
    byTenant = mock(ByTenant.class);
    when(exportingStateController.getByTenant(DEFAULT_PHYSICAL_TENANT_ID)).thenReturn(byTenant);
  }

  @ParameterizedTest
  @ValueSource(strings = {ExportingEndpoint.PAUSE, ExportingEndpoint.RESUME})
  void shouldReturnHttpNoContentOnSuccess(final String operation) throws Exception {
    // given
    when(byTenant.pauseExporting()).thenReturn(CompletableFuture.completedFuture(null));
    when(byTenant.resumeExporting()).thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var response = sendPost("/actuator/exporting/" + operation);

    // then
    assertThat(response.statusCode()).isEqualTo(204);
    assertThat(response.body()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {ExportingEndpoint.PAUSE, ExportingEndpoint.RESUME})
  void shouldReturnHttpInternalServerErrorOnFailure(final String operation) throws Exception {
    // given
    when(byTenant.pauseExporting())
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("partition unavailable")));
    when(byTenant.resumeExporting())
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("partition unavailable")));

    // when
    final var response = sendPost("/actuator/exporting/" + operation);

    // then
    assertThat(response.statusCode()).isEqualTo(500);
    assertThat(response.body()).isEqualTo("partition unavailable");
  }

  @Test
  void shouldReturnHttpBadRequestForUnknownPhysicalTenant() throws Exception {
    // when
    final var response = sendPost("/actuator/exporting/pause?physicalTenant=tenantz");

    // then
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body())
        .isEqualTo(
            "Unknown physical tenant 'tenantz'. Configured physical tenants: ["
                + DEFAULT_PHYSICAL_TENANT_ID
                + "].");
  }

  private HttpResponse<String> sendPost(final String path) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      final var request =
          HttpRequest.newBuilder()
              .uri(URI.create("http://localhost:" + managementPort + path))
              .POST(HttpRequest.BodyPublishers.noBody())
              .build();
      return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import(ExportingEndpoint.class)
  static class TestConfig {
    @Bean
    ExportingStateController exportingStateController() {
      return mock(ExportingStateController.class);
    }

    @Bean
    PhysicalTenantIds physicalTenantIds() {
      return () -> Set.of(DEFAULT_PHYSICAL_TENANT_ID);
    }
  }
}
