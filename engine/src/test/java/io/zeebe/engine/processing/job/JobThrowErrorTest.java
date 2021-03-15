/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.job;

import static io.zeebe.protocol.record.intent.JobIntent.ERROR_THROWN;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.test.util.BrokerClassRuleHelper;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class JobThrowErrorTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static String jobType;

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Before
  public void setup() {
    jobType = helper.getJobType();
  }

  @Test
  public void shouldThrowError() {
    // given
    final var job = ENGINE.createJob(jobType, PROCESS_ID);

    // when
    final Record<JobRecordValue> result =
        ENGINE
            .job()
            .withKey(job.getKey())
            .withErrorCode("error")
            .withErrorMessage("error-message")
            .throwError();

    // then
    Assertions.assertThat(result).hasRecordType(RecordType.EVENT).hasIntent(ERROR_THROWN);
    Assertions.assertThat(result.getValue()).hasErrorCode("error").hasErrorMessage("error-message");
  }

  @Test
  public void shouldRejectIfJobNotFound() {
    // given
    final int key = 123;

    // when
    final Record<JobRecordValue> result =
        ENGINE.job().withKey(key).withErrorCode("error").throwError();

    // then
    Assertions.assertThat(result).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectIfJobIsFailed() {
    // given
    final var job = ENGINE.createJob(jobType, PROCESS_ID);

    ENGINE.jobs().withType(jobType).activate();
    ENGINE.job().withKey(job.getKey()).withRetries(0).fail();

    // when
    final Record<JobRecordValue> result =
        ENGINE.job().withKey(job.getKey()).withErrorCode("error").throwError();

    // then
    Assertions.assertThat(result).hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(result.getRejectionReason()).contains("it is in state 'FAILED'");
  }

  @Test
  public void shouldRejectIfErrorIsThrown() {
    // given
    final var job = ENGINE.createJob(jobType, PROCESS_ID);

    ENGINE.job().withKey(job.getKey()).withErrorCode("error").throwError();

    // when
    final Record<JobRecordValue> result =
        ENGINE.job().withKey(job.getKey()).withErrorCode("error").throwError();

    // then
    Assertions.assertThat(result).hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(result.getRejectionReason()).contains("it is in state 'ERROR_THROWN'");
  }
}
