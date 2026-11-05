/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client;

import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.it.util.RecordingJobHandler;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class MultipleClientTest {

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true);

  private CamundaClient client1;
  private CamundaClient client2;
  private ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  void createClients() {
    client1 = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(10)).build();
    client2 = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(10)).build();
    resourcesHelper = new ZeebeResourcesHelper(client1);
  }

  @AfterEach
  void closeClients() {
    client1.close();
    client2.close();
  }

  @Test
  void shouldOpenJobWorkerForDifferentTypes() {
    // given
    final RecordingJobHandler handler1 = new RecordingJobHandler();
    final RecordingJobHandler handler2 = new RecordingJobHandler();
    client1.newWorker().jobType("foo").handler(handler1).open();
    client2.newWorker().jobType("bar").handler(handler2).open();

    // when
    final long job1Key = resourcesHelper.createSingleJob("foo");
    final long job2Key = resourcesHelper.createSingleJob("bar");

    // then
    waitUntil(() -> handler1.getHandledJobs().size() + handler2.getHandledJobs().size() >= 2);

    assertThat(handler1.getHandledJobs()).hasSize(1);
    assertThat(handler1.getHandledJobs().get(0).getKey()).isEqualTo(job1Key);

    assertThat(handler2.getHandledJobs()).hasSize(1);
    assertThat(handler2.getHandledJobs().get(0).getKey()).isEqualTo(job2Key);
  }
}
