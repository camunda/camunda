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
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Collections;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * When a job push fails, the broker's job stream error handler writes YIELD without authorization
 * claims and without request metadata. With multi-tenancy checks enabled, such a YIELD must still
 * yield the job of any tenant; otherwise the job stays ACTIVATED until it times out.
 */
public final class MultiTenantYieldJobTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().withMultiTenancyChecksEnabled(true);

  private static final String PROCESS_ID = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldYieldJobOfDefaultTenantOnBrokerYield() {
    shouldYieldJobOnBrokerYield(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldYieldJobOfCustomTenantOnBrokerYield() {
    shouldYieldJobOnBrokerYield(Strings.newRandomValidTenantId());
  }

  @Test
  public void shouldRejectBrokerYieldOfJobThatIsNotActivated() {
    // given
    final var jobType = Strings.newRandomValidBpmnId();
    final var tenantId = Strings.newRandomValidTenantId();
    ENGINE.tenant().newTenant().withTenantId(tenantId).create();
    final long jobKey =
        ENGINE.createJob(jobType, PROCESS_ID, Collections.emptyMap(), tenantId).getKey();

    // when
    final var result =
        writeYield(
            RecordToWrite.commandWithoutClaims()
                .job(JobIntent.YIELD, new JobRecord().setType(jobType))
                .key(jobKey));

    // then
    assertThat(result.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(result.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldRejectClientYieldForTenantTheUserIsNotAssignedTo() {
    // given
    final var jobType = Strings.newRandomValidBpmnId();
    final var tenantId = Strings.newRandomValidTenantId();
    ENGINE.tenant().newTenant().withTenantId(tenantId).create();
    final var assignedUser = createUserAssignedToTenant(tenantId);
    final var otherUser = Strings.newRandomValidIdentityId();
    ENGINE.user().newUser(otherUser).create();
    ENGINE.createJob(jobType, PROCESS_ID, Collections.emptyMap(), tenantId);
    final var batch = activateJob(jobType, tenantId, assignedUser);

    // when
    final var result =
        writeYield(
            RecordToWrite.commandFromRequest(otherUser)
                .job(JobIntent.YIELD, (JobRecord) batch.getJobs().get(0))
                .key(batch.getJobKeys().get(0)));

    // then
    assertThat(result.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(result.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  private void shouldYieldJobOnBrokerYield(final String tenantId) {
    // given
    final var jobType = Strings.newRandomValidBpmnId();
    ENGINE.tenant().newTenant().withTenantId(tenantId).create();
    final var username = createUserAssignedToTenant(tenantId);
    ENGINE.createJob(jobType, PROCESS_ID, Collections.emptyMap(), tenantId);
    final var batch = activateJob(jobType, tenantId, username);
    final long jobKey = batch.getJobKeys().get(0);

    // when
    final var result =
        writeYield(
            RecordToWrite.commandWithoutClaims()
                .job(JobIntent.YIELD, (JobRecord) batch.getJobs().get(0))
                .key(jobKey));

    // then
    assertThat(result.getRecordType()).describedAs("%s", result).isEqualTo(RecordType.EVENT);
    assertThat(result.getIntent()).isEqualTo(JobIntent.YIELDED);
    assertThat(activateJob(jobType, tenantId, username).getJobKeys())
        .describedAs("the yielded job is activatable again")
        .containsExactly(jobKey);
  }

  private static String createUserAssignedToTenant(final String tenantId) {
    final var username = Strings.newRandomValidIdentityId();
    ENGINE.user().newUser(username).create();
    ENGINE
        .tenant()
        .addEntity(tenantId)
        .withEntityType(EntityType.USER)
        .withEntityId(username)
        .add();
    return username;
  }

  private static JobBatchRecordValue activateJob(
      final String jobType, final String tenantId, final String username) {
    return ENGINE.jobs().withType(jobType).withTenantId(tenantId).activate(username).getValue();
  }

  private static Record<JobRecordValue> writeYield(final RecordToWrite yield) {
    final long position = ENGINE.writeRecords(yield);
    return RecordingExporter.jobRecords()
        .filter(r -> r.getSourceRecordPosition() == position)
        .getFirst();
  }
}
