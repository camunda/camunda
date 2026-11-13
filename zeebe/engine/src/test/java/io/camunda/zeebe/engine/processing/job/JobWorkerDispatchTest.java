/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.JobAction;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.JobKind;
import org.junit.jupiter.api.Test;

final class JobWorkerDispatchTest {

  private static final String TOKEN = "ts-abc";

  private static JobRecord job() {
    return new JobRecord().setType("charge-card");
  }

  @Test
  void shouldServeUnreservedJobToAnyWorker() {
    // given
    final JobRecord job = job();

    // when / then
    assertThat(JobWorkerDispatch.withheldFromWorkersReason(job, null)).isNull();
    assertThat(JobWorkerDispatch.withheldFromWorkersReason(job, TOKEN)).isNull();
  }

  @Test
  void shouldWithholdReservedJobFromAWorkerWithoutTheToken() {
    // given
    final JobRecord job = job().setJobReservationToken(TOKEN);

    // when / then
    assertThat(JobWorkerDispatch.withheldFromWorkersReason(job, null))
        .isEqualTo(JobAction.SKIPPED_RESERVED);
  }

  @Test
  void shouldWithholdReservedJobFromAWorkerWithADifferentToken() {
    // given
    final JobRecord job = job().setJobReservationToken(TOKEN);

    // when / then
    assertThat(JobWorkerDispatch.withheldFromWorkersReason(job, "ts-other"))
        .isEqualTo(JobAction.SKIPPED_RESERVED);
  }

  @Test
  void shouldServeReservedJobToTheWorkerHoldingTheMatchingToken() {
    // given
    final JobRecord job = job().setJobReservationToken(TOKEN);

    // when / then
    assertThat(JobWorkerDispatch.withheldFromWorkersReason(job, TOKEN)).isNull();
  }

  @Test
  void shouldWithholdCallActivityStubEvenFromATokenHolder() {
    // given
    final JobRecord stub =
        job().setJobKind(JobKind.CALL_ACTIVITY_STUB).setJobReservationToken(TOKEN);

    // when / then
    assertThat(JobWorkerDispatch.withheldFromWorkersReason(stub, TOKEN))
        .isEqualTo(JobAction.SKIPPED_CALL_ACTIVITY_STUB);
  }
}
