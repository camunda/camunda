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
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class JobWorkerDispatchTest {

  private static final String TOKEN = "ts-abc";
  private static final String CALLED_PROCESS_ID = "ship-order";

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
    final JobRecord job = job().setReservationToken(TOKEN);

    // when / then
    assertThat(JobWorkerDispatch.withheldFromWorkersReason(job, null))
        .isEqualTo(JobAction.SKIPPED_RESERVED);
  }

  @Test
  void shouldWithholdReservedJobFromAWorkerWithADifferentToken() {
    // given
    final JobRecord job = job().setReservationToken(TOKEN);

    // when / then
    assertThat(JobWorkerDispatch.withheldFromWorkersReason(job, "ts-other"))
        .isEqualTo(JobAction.SKIPPED_RESERVED);
  }

  @Test
  void shouldServeReservedJobToTheWorkerHoldingTheMatchingToken() {
    // given
    final JobRecord job = job().setReservationToken(TOKEN);

    // when / then
    assertThat(JobWorkerDispatch.withheldFromWorkersReason(job, TOKEN)).isNull();
  }

  @Test
  void shouldServeCallActivityStubToAnyActivationOfItsType() {
    // given a stub carries no reservation token; its type names the called process it stands in for
    final JobRecord stub = stub(CALLED_PROCESS_ID);

    // when / then it is withheld from no worker: isolation comes from its job type, not a filter
    assertThat(JobWorkerDispatch.withheldFromWorkersReason(stub, null))
        .describedAs("a stub without a reservation token is served, no token needed")
        .isNull();
    assertThat(JobWorkerDispatch.withheldFromWorkersReason(stub, TOKEN))
        .describedAs("presenting a token does not withhold the stub either")
        .isNull();
  }

  private static JobRecord stub(final String calledProcessId) {
    final JobRecord stub = job().setJobKind(JobKind.CALL_ACTIVITY_STUB);
    stub.setCustomHeaders(
        BufferUtil.wrapArray(
            MsgPackConverter.convertToMsgPack(
                Map.of(Protocol.CALLED_PROCESS_ID_HEADER_NAME, calledProcessId))));
    return stub;
  }
}
