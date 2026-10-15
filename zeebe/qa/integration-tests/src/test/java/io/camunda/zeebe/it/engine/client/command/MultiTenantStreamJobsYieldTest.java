/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.StreamJobsResponse;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.qa.util.actuator.JobStreamActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.jobstream.JobStreamActuatorAssert;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.grpc.internal.AbstractStream.TransportState;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * End-to-end regression test for https://github.com/camunda/camunda/issues/65336: a streaming
 * worker that blocks makes the gateway report BLOCKED, the broker's job stream error handler writes
 * JOB YIELD, and with multi-tenancy checks enabled that YIELD must still yield the job so a polling
 * worker can take it.
 */
@ZeebeIntegration
final class MultiTenantStreamJobsYieldTest {

  @TestZeebe(initMethod = "initTestStandaloneBroker")
  private static TestStandaloneBroker zeebe;

  @AutoClose
  private final CamundaClient client =
      zeebe
          .newClientBuilder()
          .credentialsProvider(
              new BasicAuthCredentialsProviderBuilder().username("demo").password("demo").build())
          .build();

  private final CountDownLatch latch = new CountDownLatch(1);
  private final Map<String, Object> payload =
      Map.of("foo", "bar".repeat(TransportState.DEFAULT_ONREADY_THRESHOLD));
  private CamundaFuture<StreamJobsResponse> stream;

  @SuppressWarnings("unused")
  static void initTestStandaloneBroker() {
    zeebe =
        new TestStandaloneBroker()
            .withRecordingExporter(true)
            .withBasicAuth()
            .withAuthenticatedAccess()
            .withMultiTenancyEnabled()
            .withSecondaryStorageType(SecondaryStorageType.rdbms)
            .withUnifiedConfig(
                cfg -> {
                  final var rdbms = cfg.getData().getSecondaryStorage().getRdbms();
                  rdbms.setUrl(
                      "jdbc:h2:mem:yield-"
                          + UUID.randomUUID()
                          + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
                  rdbms.setUsername("sa");
                  rdbms.setPassword("");
                });
  }

  @AfterEach
  void afterEach() {
    latch.countDown();
    if (stream != null) {
      stream.cancel(true);
    }
    RecordingExporter.setMaximumWaitTime(5000);
  }

  @ParameterizedTest
  @ValueSource(strings = {TenantOwned.DEFAULT_TENANT_IDENTIFIER, "custom"})
  void shouldYieldJobOfBlockedStreamAndLetPollingWorkerTakeIt(final String tenant) {
    // given
    final var tenantId =
        tenant.equals(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            ? tenant
            : Strings.newRandomValidTenantId();
    // basic auth resolves the user from secondary storage, which is filled asynchronously
    Awaitility.await("until demo user can authenticate")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions()
        .until(() -> client.newTopologyRequest().send().join() != null);
    if (!tenantId.equals(TenantOwned.DEFAULT_TENANT_IDENTIFIER)) {
      client.newCreateTenantCommand().tenantId(tenantId).name(tenantId).send().join();
      client.newAssignUserToTenantCommand().username("demo").tenantId(tenantId).send().join();
    }
    final var jobType = Strings.newRandomValidBpmnId();
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(jobType)
                .startEvent()
                .serviceTask("task", b -> b.zeebeJobType(jobType))
                .endEvent()
                .done(),
            "process.bpmn")
        .tenantId(tenantId)
        .send()
        .join();
    stream =
        client
            .newStreamJobsCommand()
            .jobType(jobType)
            .consumer(this::block)
            .workerName("stream")
            .tenantIds(tenantId)
            .timeout(Duration.ofMinutes(10))
            .send();
    awaitStreamRegistered(jobType);

    // when - create instances until the stream is blocked and the broker writes a YIELD command
    RecordingExporter.setMaximumWaitTime(0);
    Awaitility.await("until the broker writes a YIELD command for a failed push")
        .atMost(Duration.ofMinutes(1))
        .untilAsserted(
            () -> {
              client
                  .newCreateInstanceCommand()
                  .bpmnProcessId(jobType)
                  .latestVersion()
                  .variables(payload)
                  .tenantId(tenantId)
                  .send()
                  .join();
              assertThat(
                      RecordingExporter.jobRecords(JobIntent.YIELD)
                          .withType(jobType)
                          .withRecordType(RecordType.COMMAND)
                          .exists())
                  .isTrue();
            });
    RecordingExporter.setMaximumWaitTime(5000);
    final Record<JobRecordValue> yieldCommand =
        RecordingExporter.jobRecords(JobIntent.YIELD)
            .withType(jobType)
            .withRecordType(RecordType.COMMAND)
            .getFirst();

    // then - the YIELD command is processed into YIELDED, not rejected
    final var yieldResult =
        RecordingExporter.jobRecords()
            .withRecordKey(yieldCommand.getKey())
            .filter(r -> r.getSourceRecordPosition() == yieldCommand.getPosition())
            .getFirst();
    assertThat(yieldResult.getRecordType())
        .describedAs(
            "result of broker-written YIELD (claims %s): %s",
            yieldCommand.getAuthorizations(), yieldResult)
        .isEqualTo(RecordType.EVENT);
    assertThat(yieldResult.getIntent()).isEqualTo(JobIntent.YIELDED);

    // and a polling worker can activate the yielded job
    final var polled =
        client
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(1000)
            .tenantIds(tenantId)
            .requestTimeout(Duration.ofSeconds(5))
            .send()
            .join()
            .getJobs();
    assertThat(polled).extracting(ActivatedJob::getKey).contains(yieldCommand.getKey());
  }

  private void block(final ActivatedJob job) {
    try {
      latch.await(5, TimeUnit.MINUTES);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void awaitStreamRegistered(final String jobType) {
    final var actuator = JobStreamActuator.of(zeebe);
    Awaitility.await("until stream with type '%s' is registered".formatted(jobType))
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(actuator)
                    .remoteStreams()
                    .haveJobType(1, jobType));
  }
}
