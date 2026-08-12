/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultCorrections;
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
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.UUID;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class CompleteJobTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withSecurityConfig(config -> config.getAuthorizations().setEnabled(true));

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
  public void shouldCompleteJob() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).activate(username);
    final JobRecordValue job = batchRecord.getValue().getJobs().get(0);

    // when
    final Record<JobRecordValue> jobCompletedRecord =
        ENGINE.job().withKey(batchRecord.getValue().getJobKeys().get(0)).complete();

    // then
    final JobRecordValue recordValue = jobCompletedRecord.getValue();

    Assertions.assertThat(jobCompletedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.COMPLETED);

    Assertions.assertThat(recordValue)
        .hasWorker(batchRecord.getValue().getWorker())
        .hasType(job.getType())
        .hasRetries(job.getRetries())
        .hasDeadline(job.getDeadline())
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .hasResult(new JobResult().setDenied(false));
  }

  @Test
  public void shouldRejectCompletionIfJobNotFound() {
    // given
    final int key = 123;

    // when
    final Record<JobRecordValue> jobRecord = ENGINE.job().withKey(key).expectRejection().complete();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldCompleteJobWithVariables() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).activate(username);

    // when
    final Record<JobRecordValue> completedRecord =
        ENGINE
            .job()
            .withKey(batchRecord.getValue().getJobKeys().get(0))
            .withVariables("{'foo':'bar'}")
            .complete();

    // then
    Assertions.assertThat(completedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.COMPLETED);
    assertThat(completedRecord.getValue().getVariables()).containsExactly(entry("foo", "bar"));
  }

  @Test
  public void shouldCompleteJobWithNilVariables() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).activate(username);

    // when
    final Record<JobRecordValue> completedRecord =
        ENGINE
            .job()
            .withKey(batchRecord.getValue().getJobKeys().get(0))
            .withVariables(new UnsafeBuffer(MsgPackHelper.NIL))
            .complete();

    // then
    Assertions.assertThat(completedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.COMPLETED);
    assertThat(completedRecord.getValue().getVariables()).isEmpty();
  }

  @Test
  public void shouldCompleteJobWithZeroLengthVariables() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).activate(username);

    // when
    final Record<JobRecordValue> completedRecord =
        ENGINE
            .job()
            .withKey(batchRecord.getValue().getJobKeys().get(0))
            .withVariables(new UnsafeBuffer(new byte[0]))
            .complete();

    // then
    Assertions.assertThat(completedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.COMPLETED);
    assertThat(completedRecord.getValue().getVariables()).isEmpty();
  }

  @Test
  public void shouldThrowExceptionOnCompletionIfVariablesAreInvalid() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).activate(username);

    final byte[] invalidVariables = new byte[] {1}; // positive fixnum, i.e. no object

    // when
    final Throwable throwable =
        catchThrowable(
            () ->
                ENGINE
                    .job()
                    .withKey(batchRecord.getValue().getJobKeys().get(0))
                    .withVariables(new UnsafeBuffer(invalidVariables))
                    .expectRejection()
                    .complete());

    // then
    assertThat(throwable).isInstanceOf(RuntimeException.class);
    assertThat(throwable.getMessage()).contains("Property 'variables' is invalid");
    assertThat(throwable.getMessage())
        .contains("Expected document to be a root level object, but was 'INTEGER'");
  }

  @Test
  public void shouldCompleteJobWithSetResultDeniedFalse() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).activate(username);

    // when
    final Record<JobRecordValue> completedRecord =
        ENGINE
            .job()
            .withKey(batchRecord.getValue().getJobKeys().get(0))
            .withResult(new JobResult().setDenied(false))
            .complete();

    // then
    Assertions.assertThat(completedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.COMPLETED);

    Assertions.assertThat(completedRecord.getValue()).hasResult(new JobResult().setDenied(false));
  }

  @Test
  public void shouldCompleteJobWithSetResultDeniedTrueAndDeniedReason() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).activate(username);

    // when
    final Record<JobRecordValue> completedRecord =
        ENGINE
            .job()
            .withKey(batchRecord.getValue().getJobKeys().get(0))
            .withResult(
                new JobResult()
                    .setDenied(true)
                    .setDeniedReason("Reason to deny lifecycle transition"))
            .complete();

    // then
    Assertions.assertThat(completedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.COMPLETED);

    Assertions.assertThat(completedRecord.getValue())
        .hasResult(
            new JobResult().setDenied(true).setDeniedReason("Reason to deny lifecycle transition"));
  }

  @Test
  public void shouldCompleteJobWithResultWithCorrections() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).activate(username);

    final JobResultCorrections corrections = new JobResultCorrections();
    corrections.setAssignee("TestAssignee");
    corrections.setDueDate("2025-05-23T01:02:03+01:00");
    corrections.setFollowUpDate("2025-06-23T01:02:03+01:00");
    corrections.setCandidateUsersList(List.of("userA", "userB"));
    corrections.setCandidateGroupsList(List.of("groupA", "groupB"));
    corrections.setPriority(20);

    final List<String> correctedAttributes =
        List.of(
            "assignee", "dueDate", "followUpDate", "candidateUsers", "candidateGroups", "priority");

    final JobResult result =
        new JobResult().setCorrections(corrections).setCorrectedAttributes(correctedAttributes);

    // when
    final Record<JobRecordValue> completedRecord =
        ENGINE
            .job()
            .withKey(batchRecord.getValue().getJobKeys().get(0))
            .withResult(result)
            .complete();

    // then
    Assertions.assertThat(completedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.COMPLETED);

    Assertions.assertThat(completedRecord.getValue()).hasResult(result);
  }

  @Test
  public void shouldCompleteJobWithPartiallySetResultCorrections() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).activate(username);

    final JobResultCorrections corrections = new JobResultCorrections();
    corrections.setAssignee("TestAssignee");
    corrections.setDueDate("2025-05-23T01:02:03+01:00");
    corrections.setFollowUpDate("2025-06-23T01:02:03+01:00");
    corrections.setPriority(20);

    final List<String> correctedAttributes =
        List.of("assignee", "dueDate", "followUpDate", "priority");

    final JobResult result =
        new JobResult().setCorrections(corrections).setCorrectedAttributes(correctedAttributes);

    // when
    final Record<JobRecordValue> completedRecord =
        ENGINE
            .job()
            .withKey(batchRecord.getValue().getJobKeys().get(0))
            .withResult(result)
            .complete();

    // then
    Assertions.assertThat(completedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.COMPLETED);

    Assertions.assertThat(completedRecord.getValue()).hasResult(result);
  }

  @Test
  public void shouldRejectCompletionIfJobIsCompleted() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).activate(username);

    final Long jobKey = batchRecord.getValue().getJobKeys().get(0);
    ENGINE.job().withKey(jobKey).complete();

    // when
    final Record<JobRecordValue> jobRecord =
        ENGINE.job().withKey(jobKey).expectRejection().complete();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectCompletionIfJobIsFailed() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);

    // when
    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).activate(username);
    final Long jobKey = batchRecord.getValue().getJobKeys().get(0);
    ENGINE.job().withKey(jobKey).fail();

    final Record<JobRecordValue> jobRecord =
        ENGINE.job().withKey(jobKey).expectRejection().complete();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.INVALID_STATE);
  }
}
