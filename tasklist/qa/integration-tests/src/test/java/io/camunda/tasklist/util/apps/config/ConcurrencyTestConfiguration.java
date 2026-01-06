/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util.apps.config;

import io.camunda.client.CamundaClient;
import io.camunda.tasklist.webapp.permission.TasklistPermissionServices;
import io.camunda.tasklist.webapp.service.CamundaClientBasedAdapter;
import io.camunda.tasklist.webapp.tenant.TenantService;
import io.camunda.tasklist.zeebe.TasklistServicesAdapter;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration for custom TasklistServicesAdapter to reproduce concurrency with exporter
 * issues
 */
@TestConfiguration
public class ConcurrencyTestConfiguration {

  public volatile CountDownLatch completionLatch = new CountDownLatch(1);

  @Bean
  @Primary
  public TasklistServicesAdapter concurrencyTestAdapter(
      @Qualifier("tasklistCamundaClient") final CamundaClient camundaClient,
      final TasklistPermissionServices permissionServices,
      final TenantService tenantService) {
    /** Custom adapter that waits on a latch after completing a task */
    return new CamundaClientBasedAdapter(camundaClient, permissionServices, tenantService) {
      @Override
      public void completeUserTask(final TaskEntity task, final Map<String, Object> variables) {
        // Call the parent implementation to complete the task
        super.completeUserTask(task, variables);
        // Wait for the test to signal that it's ready to proceed
        try {
          completionLatch.await(30, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted while waiting for test synchronization", e);
        }
      }
    };
  }
}
