/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.engine.state.immutable.SuspensionState.State;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.JobClient;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Job commands are processed while the process instance is resuming. */
@RunWith(Parameterized.class)
public final class JobCommandWhileResumingTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Parameter(0)
  public String name;

  @Parameter(1)
  public Consumer<JobClient> command;

  @Parameter(2)
  public JobIntent expectedIntent;

  @Parameters(name = "{0}")
  public static Object[][] parameters() {
    return new Object[][] {
      {"complete", (Consumer<JobClient>) JobClient::complete, JobIntent.COMPLETED},
      {"fail", (Consumer<JobClient>) c -> c.withRetries(0).fail(), JobIntent.FAILED},
      {
        "throw error",
        (Consumer<JobClient>) c -> c.withErrorCode("ERR").throwError(),
        JobIntent.ERROR_THROWN
      },
      {
        "update",
        (Consumer<JobClient>) c -> c.withRetries(5).withChangeset(Set.of("retries")).update(),
        JobIntent.UPDATED
      },
      {
        "update retries",
        (Consumer<JobClient>) c -> c.withRetries(5).updateRetries(),
        JobIntent.RETRIES_UPDATED
      },
      {
        "update timeout",
        (Consumer<JobClient>) c -> c.withTimeout(60_000L).updateTimeout(),
        JobIntent.TIMEOUT_UPDATED
      },
    };
  }

  @Test
  public void shouldProcessJobCommandWhileResuming() {
    // given - an activated job, so it has a deadline for the timeout update
    final String jobType = Strings.newRandomValidBpmnId();
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, Strings.newRandomValidBpmnId());
    ENGINE.jobs().withType(jobType).activate();
    seedResuming(job.getValue().getProcessInstanceKey());

    // when
    command.accept(ENGINE.job().withKey(job.getKey()));

    // then
    assertThat(RecordingExporter.jobRecords(expectedIntent).withRecordKey(job.getKey()).exists())
        .isTrue();
  }

  private static void seedResuming(final long processInstanceKey) {
    await().until(ENGINE::hasReachedEnd);
    ((MutableProcessingState) ENGINE.getProcessingState())
        .getSuspensionState()
        .setSuspensionState(processInstanceKey, State.RESUMING);
  }
}
