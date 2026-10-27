/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.interceptor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.cluster.SecondaryStorageReadiness;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.service.exception.SecondaryStorageDegradedException;
import io.camunda.service.exception.SecondaryStorageTypeNotSupportedException;
import io.camunda.zeebe.gateway.rest.GlobalControllerExceptionHandler;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Asserts the problem-detail body shapes produced when {@link SecondaryStorageInterceptor} rejects
 * a request — 503 because its physical tenant is degraded, 403 because the tenant's secondary
 * storage is not one the endpoint declares — exercised end-to-end through {@link
 * GlobalControllerExceptionHandler} via MockMvc (no Spring context).
 */
class SecondaryStorageInterceptorProblemDetailTest {

  @Test
  void shouldReturnServiceUnavailableProblemDetailWhenPhysicalTenantDegraded() throws Exception {
    // given
    final var readiness = mock(SecondaryStorageReadiness.class);
    when(readiness.isReady(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)).thenReturn(false);
    final var interceptor =
        new SecondaryStorageInterceptor(t -> DatabaseType.ELASTICSEARCH, readiness);
    final MockMvc mockMvc = mockMvcFor(new TestController(), interceptor);

    // when/then
    mockMvc
        .perform(get("/test-secondary-storage-endpoint"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("UNAVAILABLE"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    SecondaryStorageDegradedException.SECONDARY_STORAGE_DEGRADED_MESSAGE.formatted(
                        PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)));
  }

  @Test
  void shouldReturnForbiddenProblemDetailWhenConfiguredStorageIsNotDeclared() throws Exception {
    // given
    final var interceptor =
        new SecondaryStorageInterceptor(
            t -> DatabaseType.RDBMS, SecondaryStorageReadiness.ALWAYS_READY);
    final MockMvc mockMvc = mockMvcFor(new DocumentStorageController(), interceptor);

    // when/then
    mockMvc
        .perform(get("/test-document-storage-endpoint"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("FORBIDDEN"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    SecondaryStorageTypeNotSupportedException
                        .UNSUPPORTED_SECONDARY_STORAGE_TYPE_MESSAGE
                        .formatted("elasticsearch, opensearch", "rdbms")));
  }

  private static MockMvc mockMvcFor(
      final Object controller, final SecondaryStorageInterceptor interceptor) {
    return MockMvcBuilders.standaloneSetup(controller)
        .addInterceptors(interceptor)
        .setControllerAdvice(new GlobalControllerExceptionHandler())
        .build();
  }

  @RestController
  @RequiresSecondaryStorage
  static class TestController {
    @GetMapping("/test-secondary-storage-endpoint")
    String get() {
      return "ok";
    }
  }

  @RestController
  @RequiresSecondaryStorage({DatabaseType.ELASTICSEARCH, DatabaseType.OPENSEARCH})
  static class DocumentStorageController {
    @GetMapping("/test-document-storage-endpoint")
    String get() {
      return "ok";
    }
  }
}
