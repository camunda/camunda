/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor.workflow.job;

import static io.zeebe.protocol.record.intent.JobIntent.ACTIVATED;
import static io.zeebe.protocol.record.intent.JobIntent.FAIL;
import static io.zeebe.protocol.record.intent.JobIntent.FAILED;
import static io.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.JobBatchIntent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.value.JobBatchRecordValue;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.test.util.Strings;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class FailJobTest {
  private static final String PROCESS_ID = "process";
  private static String jobType;

  @ClassRule public static final EngineRule ENGINE = new EngineRule();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Before
  public void setup() {
    jobType = Strings.newRandomValidBpmnId();
  }

  @Test
  public void shouldFail() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();
    final JobRecordValue job = batchRecord.getValue().getJobs().get(0);
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);
    final int retries = 23;

    // when
    final Record<JobRecordValue> failRecord =
        ENGINE
            .job()
            .withKey(jobKey)
            .ofInstance(job.getWorkflowInstanceKey())
            .withRetries(retries)
            .fail();

    // then
    Assertions.assertThat(failRecord).hasRecordType(RecordType.EVENT).hasIntent(FAILED);
    Assertions.assertThat(failRecord.getValue())
        .hasWorker(job.getWorker())
        .hasType(job.getType())
        .hasRetries(retries)
        .hasDeadline(job.getDeadline());
  }

  @Test
  public void shouldFailWithMessage() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();

    final long jobKey = batchRecord.getValue().getJobKeys().get(0);
    final JobRecordValue job = batchRecord.getValue().getJobs().get(0);
    final int retries = 23;

    // when
    final Record<JobRecordValue> failedRecord =
        ENGINE
            .job()
            .withKey(jobKey)
            .ofInstance(job.getWorkflowInstanceKey())
            .withRetries(retries)
            .withErrorMessage("failed job")
            .fail();

    // then
    Assertions.assertThat(failedRecord).hasRecordType(RecordType.EVENT).hasIntent(FAILED);
    Assertions.assertThat(failedRecord.getValue())
        .hasWorker(job.getWorker())
        .hasType(job.getType())
        .hasRetries(retries)
        .hasDeadline(job.getDeadline())
        .hasErrorMessage(failedRecord.getValue().getErrorMessage());
  }

  @Test
  public void shouldFailJobAndRetry() {
    // given
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, PROCESS_ID);

    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);
    final Record<JobRecordValue> activatedRecord =
        jobRecords(ACTIVATED).withRecordKey(jobKey).getFirst();

    // when
    final Record<JobRecordValue> failRecord =
        ENGINE
            .job()
            .withKey(jobKey)
            .ofInstance(job.getValue().getWorkflowInstanceKey())
            .withRetries(3)
            .fail();
    ENGINE.jobs().withType(jobType).activate();

    // then
    Assertions.assertThat(failRecord).hasRecordType(RecordType.EVENT).hasIntent(FAILED);

    // and the job is published again
    final Record republishedEvent =
        RecordingExporter.jobRecords()
            .skipUntil(j -> j.getIntent() == FAILED)
            .withIntent(ACTIVATED)
            .getFirst();

    assertThat(republishedEvent.getKey()).isEqualTo(activatedRecord.getKey());
    assertThat(republishedEvent.getPosition()).isNotEqualTo(activatedRecord.getPosition());

    // and the job lifecycle is correct
    final List<Record> jobEvents =
        RecordingExporter.jobRecords().limit(6).collect(Collectors.toList());
    assertThat(jobEvents)
        .extracting(Record::getRecordType, Record::getValueType, Record::getIntent)
        .containsExactly(
            tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.CREATE),
            tuple(RecordType.EVENT, ValueType.JOB, JobIntent.CREATED),
            tuple(RecordType.EVENT, ValueType.JOB, JobIntent.ACTIVATED),
            tuple(RecordType.COMMAND, ValueType.JOB, FAIL),
            tuple(RecordType.EVENT, ValueType.JOB, FAILED),
            tuple(RecordType.EVENT, ValueType.JOB, JobIntent.ACTIVATED));

    final List<Record<JobBatchRecordValue>> jobActivateCommands =
        RecordingExporter.jobBatchRecords().limit(4).collect(Collectors.toList());

    assertThat(jobActivateCommands)
        .extracting(Record::getRecordType, Record::getValueType, Record::getIntent)
        .containsExactly(
            tuple(RecordType.COMMAND, ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE),
            tuple(RecordType.EVENT, ValueType.JOB_BATCH, JobBatchIntent.ACTIVATED),
            tuple(RecordType.COMMAND, ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE),
            tuple(RecordType.EVENT, ValueType.JOB_BATCH, JobBatchIntent.ACTIVATED));
  }

  @Test
  public void shouldRejectFailIfJobNotFound() {
    // given
    final int key = 123;

    // when
    final Record<JobRecordValue> jobRecord =
        ENGINE.job().withKey(key).withRetries(3).expectRejection().fail();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectFailIfJobAlreadyFailed() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);
    ENGINE.job().withKey(jobKey).withRetries(0).fail();

    // when
    final Record<JobRecordValue> jobRecord =
        ENGINE.job().withKey(jobKey).withRetries(3).expectRejection().fail();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(jobRecord.getRejectionReason()).contains("is marked as failed");
  }

  @Test
  public void shouldRejectFailIfJobCreated() {
    // given
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, PROCESS_ID);

    // when
    final Record<JobRecordValue> jobRecord =
        ENGINE.job().withKey(job.getKey()).withRetries(3).expectRejection().fail();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(jobRecord.getRejectionReason()).contains("must be activated first");
  }

  @Test
  public void shouldRejectFailIfJobCompleted() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();
    final JobRecordValue job = batchRecord.getValue().getJobs().get(0);
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    ENGINE.job().withKey(jobKey).complete();

    // when
    final Record<JobRecordValue> jobRecord =
        ENGINE.job().withKey(jobKey).withRetries(3).expectRejection().fail();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }
}
