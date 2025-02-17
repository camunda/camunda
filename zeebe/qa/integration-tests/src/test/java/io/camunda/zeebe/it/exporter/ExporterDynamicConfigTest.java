/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.exporter;

import static org.hamcrest.Matchers.greaterThan;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class ExporterDynamicConfigTest {

  @TestZeebe
  private final TestStandaloneBroker zeebe = new TestStandaloneBroker().withUnauthenticatedAccess();

  @AutoClose private CamundaClient client;

  @BeforeEach
  void beforeEach() {
    client = zeebe.newClientBuilder().build();
  }

  @Test
  void shouldEnableExporterByDefaultOnStartup() {
    // given -- when start with no exporter
    zeebe.stop();

    // when -- restart with new exporter
    zeebe.withRecordingExporter(true).start().awaitCompleteTopology();
    client.newPublishMessageCommand().messageName("test").correlationKey("test").send().join();

    // then
    Awaitility.await("Exporter is enabled")
        .until(() -> RecordingExporter.getRecords().size(), greaterThan(0));
  }
}
