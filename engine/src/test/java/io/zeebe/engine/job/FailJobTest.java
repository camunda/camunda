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
package io.zeebe.engine.job;

import static io.zeebe.protocol.intent.JobIntent.ACTIVATED;
import static io.zeebe.protocol.intent.JobIntent.FAIL;
import static io.zeebe.protocol.intent.JobIntent.FAILED;
import static io.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.RecordMetadata;
import io.zeebe.exporter.api.record.value.JobBatchRecordValue;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.RecordType;
import io.zeebe.protocol.RejectionType;
import io.zeebe.protocol.ValueType;
import io.zeebe.protocol.intent.JobBatchIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

public class FailJobTest {
  private static final String JSON_VARIABLES = "{\"foo\":\"bar\"}";
  private static final byte[] VARIABLES_MSG_PACK = MsgPackUtil.asMsgPackReturnArray(JSON_VARIABLES);
  private static final String JOB_TYPE = "foo";
  private static final String PROCESS_ID = "process";

  @Rule public EngineRule engineRule = new EngineRule();

  @Test
  public void shouldFail() {
    // given
    createJob(JOB_TYPE);
    final Record<JobBatchRecordValue> batchRecord =
        engineRule.jobs().withType(JOB_TYPE).activateAndWait();
    final JobRecordValue job = batchRecord.getValue().getJobs().get(0);
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);
    final int retries = 23;

    // when
    final Record<JobRecordValue> failRecord =
        engineRule
            .job()
            .ofInstance(job.getHeaders().getWorkflowInstanceKey())
            .withRetries(retries)
            .failAndWait(jobKey);

    // then
    Assertions.assertThat(failRecord.getMetadata())
        .hasRecordType(RecordType.EVENT)
        .hasIntent(FAILED);
    Assertions.assertThat(failRecord.getValue())
        .hasWorker(job.getWorker())
        .hasType(job.getType())
        .hasRetries(retries)
        .hasDeadline(job.getDeadline());
  }

  @Test
  public void shouldFailWithMessage() {
    // given
    createJob(JOB_TYPE);
    final Record<JobBatchRecordValue> batchRecord =
        engineRule.jobs().withType(JOB_TYPE).activateAndWait();

    final long jobKey = batchRecord.getValue().getJobKeys().get(0);
    final JobRecordValue job = batchRecord.getValue().getJobs().get(0);
    final int retries = 23;

    // when
    final Record<JobRecordValue> failedRecord =
        engineRule
            .job()
            .ofInstance(job.getHeaders().getWorkflowInstanceKey())
            .withRetries(retries)
            .withErrorMessage("failed job")
            .failAndWait(jobKey);

    // then
    Assertions.assertThat(failedRecord.getMetadata())
        .hasRecordType(RecordType.EVENT)
        .hasIntent(FAILED);
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
    final Record<JobRecordValue> job = createJob(JOB_TYPE);

    final Record<JobBatchRecordValue> batchRecord =
        engineRule.jobs().withType(JOB_TYPE).activateAndWait();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);
    final Record<JobRecordValue> activatedRecord =
        jobRecords(ACTIVATED).withRecordKey(jobKey).getFirst();

    // when
    final Record<JobRecordValue> failRecord =
        engineRule
            .job()
            .ofInstance(job.getValue().getHeaders().getWorkflowInstanceKey())
            .withRetries(3)
            .failAndWait(jobKey);
    engineRule.jobs().withType(JOB_TYPE).activateAndWait();

    // then
    Assertions.assertThat(failRecord.getMetadata())
        .hasRecordType(RecordType.EVENT)
        .hasIntent(FAILED);

    // and the job is published again
    final Record republishedEvent =
        RecordingExporter.jobRecords()
            .skipUntil(j -> j.getMetadata().getIntent() == FAILED)
            .withIntent(ACTIVATED)
            .getFirst();

    assertThat(republishedEvent.getKey()).isEqualTo(activatedRecord.getKey());
    assertThat(republishedEvent.getPosition()).isNotEqualTo(activatedRecord.getPosition());

    // and the job lifecycle is correct
    final List<Record> jobEvents =
        RecordingExporter.jobRecords().limit(6).collect(Collectors.toList());
    assertThat(jobEvents)
        .extracting(Record::getMetadata)
        .extracting(
            RecordMetadata::getRecordType, RecordMetadata::getValueType, RecordMetadata::getIntent)
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
        .extracting(Record::getMetadata)
        .extracting(
            RecordMetadata::getRecordType, RecordMetadata::getValueType, RecordMetadata::getIntent)
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
    final long position = engineRule.job().withRetries(3).fail(key);
    final Record<JobRecordValue> rejection =
        jobRecords(FAIL).filter(r -> r.getPosition() > position).getFirst();

    // then
    Assertions.assertThat(rejection.getMetadata())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasIntent(FAIL);
  }

  @Test
  public void shouldRejectFailIfJobAlreadyFailed() {
    // given
    createJob(JOB_TYPE);
    final Record<JobBatchRecordValue> batchRecord =
        engineRule.jobs().withType(JOB_TYPE).activateAndWait();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);
    engineRule.job().withRetries(0).fail(jobKey);

    // when

    final long position = engineRule.job().withRetries(3).fail(jobKey);

    // then
    final Record<JobRecordValue> rejection =
        jobRecords(FAIL).withSourceRecordPosition(position).getFirst();
    Assertions.assertThat(rejection.getMetadata())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasIntent(FAIL);
    assertThat(rejection.getMetadata().getRejectionReason()).contains("is marked as failed");
  }

  @Test
  public void shouldRejectFailIfJobCreated() {
    // given
    final Record<JobRecordValue> job = createJob(JOB_TYPE);

    // when
    final long position = engineRule.job().withRetries(3).fail(job.getKey());
    final Record<JobRecordValue> rejection =
        jobRecords(FAIL).withSourceRecordPosition(position).getFirst();

    // then
    Assertions.assertThat(rejection.getMetadata())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasIntent(FAIL);
    assertThat(rejection.getMetadata().getRejectionReason()).contains("must be activated first");
  }

  @Test
  public void shouldRejectFailIfJobCompleted() {
    // given
    createJob(JOB_TYPE);
    final Record<JobBatchRecordValue> batchRecord =
        engineRule.jobs().withType(JOB_TYPE).activateAndWait();
    final JobRecordValue job = batchRecord.getValue().getJobs().get(0);
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    engineRule.completeJob(jobKey, MsgPackUtil.asMsgPack(job.getVariables()));

    // when
    final long position = engineRule.job().withRetries(3).fail(jobKey);

    final Record<JobRecordValue> rejection =
        jobRecords(FAIL).withSourceRecordPosition(position).getFirst();

    // then
    Assertions.assertThat(rejection.getMetadata())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasIntent(FAIL);
  }

  private Record<JobRecordValue> createJob(final String type) {
    engineRule.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .serviceTask("task", b -> b.zeebeTaskType(type).done())
            .endEvent("end")
            .done());

    final long instanceKey = engineRule.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    return jobRecords(JobIntent.CREATED)
        .withType(type)
        .filter(r -> r.getValue().getHeaders().getWorkflowInstanceKey() == instanceKey)
        .getFirst();
  }
}
