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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.cluster.SecondaryStorageReadiness;
import io.camunda.service.exception.SecondaryStorageDegradedException;
import io.camunda.zeebe.gateway.rest.GlobalControllerExceptionHandler;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Asserts the HTTP 503 problem-detail body shape produced when {@link SecondaryStorageInterceptor}
 * rejects a request because its physical tenant is degraded, exercised end-to-end through {@link
 * GlobalControllerExceptionHandler} via MockMvc (no Spring context).
 */
class SecondaryStorageInterceptorProblemDetailTest {

  @Test
  void shouldReturnServiceUnavailableProblemDetailWhenPhysicalTenantDegraded() throws Exception {
    // given
    final var readiness = mock(SecondaryStorageReadiness.class);
    when(readiness.isReady(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)).thenReturn(false);
    final var interceptor = new SecondaryStorageInterceptor("elasticsearch", readiness);
    final MockMvc mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController())
            .addInterceptors(interceptor)
            .setControllerAdvice(new GlobalControllerExceptionHandler())
            .build();

    // when/then
    mockMvc
        .perform(get("/test-secondary-storage-endpoint"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("UNAVAILABLE"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    SecondaryStorageDegradedException.SECONDARY_STORAGE_DEGRADED_MESSAGE.formatted(
                        PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)));
  }

  @RestController
  @RequiresSecondaryStorage
  static class TestController {
    @GetMapping("/test-secondary-storage-endpoint")
    String get() {
      return "ok";
    }
  }
}
