/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class JobUpdateRetriesTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final int NEW_RETRIES = 20;
  private static final String PROCESS_ID = "process";
  private static String jobType;
  private static String username;
  private static String tenantId;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @BeforeClass
  public static void setUp() {
    tenantId = UUID.randomUUID().toString();
    username = UUID.randomUUID().toString();
    ENGINE.user().newUser(username).create().getValue();
    ENGINE.tenant().newTenant().withTenantId(tenantId).create().getValue().getTenantKey();
    ENGINE
        .tenant()
        .addEntity(tenantId)
        .withEntityType(EntityType.USER)
        .withEntityId(username)
        .add();

    ENGINE
        .authorization()
        .newAuthorization()
        .withPermissions(PermissionType.UPDATE_PROCESS_INSTANCE)
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId(PROCESS_ID)
        .withResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
        .withOwnerId(username)
        .withOwnerType(AuthorizationOwnerType.USER)
        .create();
  }

  @Before
  public void setup() {
    jobType = Strings.newRandomValidBpmnId();
  }

  @Test
  public void shouldUpdateRetries() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();
    final JobRecordValue job = batchRecord.getValue().getJobs().get(0);
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    final Record<JobRecordValue> updatedRecord =
        ENGINE.job().withKey(jobKey).withRetries(NEW_RETRIES).updateRetries();

    // then
    Assertions.assertThat(updatedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.RETRIES_UPDATED);
    assertThat(updatedRecord.getKey()).isEqualTo(jobKey);

    Assertions.assertThat(updatedRecord.getValue())
        .hasWorker(job.getWorker())
        .hasType(job.getType())
        .hasRetries(NEW_RETRIES)
        .hasDeadline(job.getDeadline());
  }

  @Test
  public void shouldRejectUpdateRetriesIfJobNotFound() {
    // when
    final Record<JobRecordValue> jobRecord =
        ENGINE.job().withKey(123).withRetries(NEW_RETRIES).expectRejection().updateRetries();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectUpdateRetriesIfJobCompleted() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();

    final long jobKey = batchRecord.getValue().getJobKeys().get(0);
    ENGINE.job().withKey(jobKey).withVariables("{}").complete();

    // when
    final Record<JobRecordValue> jobRecord =
        ENGINE.job().withKey(jobKey).withRetries(NEW_RETRIES).expectRejection().updateRetries();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldUpdateRetriesIfJobActivated() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    final Record<JobRecordValue> response =
        ENGINE.job().withKey(jobKey).withRetries(NEW_RETRIES).updateRetries();

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(JobIntent.RETRIES_UPDATED);
    assertThat(response.getKey()).isEqualTo(jobKey);
    assertThat(response.getValue().getRetries()).isEqualTo(NEW_RETRIES);
  }

  @Test
  public void shouldUpdateRetriesIfJobCreated() {
    // given
    final long jobKey = ENGINE.createJob(jobType, PROCESS_ID).getKey();

    // when
    final Record<JobRecordValue> response =
        ENGINE.job().withKey(jobKey).withRetries(NEW_RETRIES).updateRetries();

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(JobIntent.RETRIES_UPDATED);
    assertThat(response.getKey()).isEqualTo(jobKey);
    assertThat(response.getValue().getRetries()).isEqualTo(NEW_RETRIES);
  }

  @Test
  public void shouldRejectUpdateRetriesIfRetriesZero() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    ENGINE.job().withKey(jobKey).withRetries(0).fail();

    // when
    final Record<JobRecordValue> jobRecord =
        ENGINE.job().withKey(jobKey).withRetries(0).expectRejection().updateRetries();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectUpdateRetriesIfRetriesLessThanZero() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    ENGINE.job().withKey(jobKey).withRetries(0).fail();

    // when
    final Record<JobRecordValue> jobRecord =
        ENGINE.job().withKey(jobKey).withRetries(-1).expectRejection().updateRetries();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }
}
