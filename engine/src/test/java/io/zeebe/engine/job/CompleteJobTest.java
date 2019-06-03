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

import static io.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;

import io.zeebe.engine.processor.workflow.MsgPackConstants;
import io.zeebe.engine.util.EngineRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.RecordMetadata;
import io.zeebe.exporter.api.record.value.JobBatchRecordValue;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;

public class CompleteJobTest {
  public static final String JSON_VARIABLES = "{\"foo\":\"bar\"}";
  public static final byte[] VARIABLES_MSG_PACK = MsgPackUtil.asMsgPackReturnArray(JSON_VARIABLES);
  private static final String JOB_TYPE = "foo";
  public static final String PROCESS_ID = "process";

  @Rule public EngineRule engineRule = new EngineRule();

  @Test
  public void shouldCompleteJob() {
    // given
    createJob(JOB_TYPE);
    final Record<JobBatchRecordValue> batchRecord =
        engineRule.jobs().withType(JOB_TYPE).activateAndWait();
    final JobRecordValue job = batchRecord.getValue().getJobs().get(0);

    // when
    final Record<JobRecordValue> jobCompletedRecord =
        engineRule.completeJobAndWait(
            batchRecord.getValue().getJobKeys().get(0), MsgPackConstants.MSGPACK_VARIABLES);

    // then
    final RecordMetadata metadata = jobCompletedRecord.getMetadata();
    final JobRecordValue recordValue = jobCompletedRecord.getValue();

    assertThat(metadata.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(metadata.getIntent()).isEqualTo(JobIntent.COMPLETED);

    Assertions.assertThat(recordValue)
        .hasWorker(batchRecord.getValue().getWorker())
        .hasType(job.getType())
        .hasRetries(job.getRetries())
        .hasDeadline(job.getDeadline());
  }

  @Test
  public void shouldRejectCompletionIfJobNotFound() {
    // given
    final int key = 123;

    // when
    engineRule.completeJob(key, MsgPackConstants.MSGPACK_VARIABLES);
    final Record<JobRecordValue> rejection =
        RecordingExporter.jobRecords(JobIntent.COMPLETE)
            .withRecordType(RecordType.COMMAND_REJECTION)
            .getFirst();

    // then
    assertThat(rejection.getMetadata().getIntent()).isEqualTo(JobIntent.COMPLETE);
    assertThat(rejection.getMetadata().getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldCompleteJobWithVariables() {
    // given
    createJob(JOB_TYPE);
    final Record<JobBatchRecordValue> batchRecord =
        engineRule.jobs().withType(JOB_TYPE).activateAndWait();

    // when
    final Record<JobRecordValue> completedRecord =
        engineRule.completeJobAndWait(
            batchRecord.getValue().getJobKeys().get(0), MsgPackConstants.MSGPACK_VARIABLES);

    // then
    Assertions.assertThat(completedRecord.getMetadata())
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.COMPLETED);
    assertThat(completedRecord.getValue().getVariables())
        .isEqualTo(MsgPackConverter.convertToJson(MsgPackConstants.MSGPACK_VARIABLES));
  }

  @Test
  public void shouldCompleteJobWithNilVariables() {
    // given
    createJob(JOB_TYPE);
    final Record<JobBatchRecordValue> batchRecord =
        engineRule.jobs().withType(JOB_TYPE).activateAndWait();

    // when
    final Record<JobRecordValue> completedRecord =
        engineRule.completeJobAndWait(
            batchRecord.getValue().getJobKeys().get(0), new UnsafeBuffer(MsgPackHelper.NIL));

    // then
    Assertions.assertThat(completedRecord.getMetadata())
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.COMPLETED);
    assertThat(MsgPackConverter.convertToMsgPack(completedRecord.getValue().getVariables()))
        .isEqualTo(MsgPackHelper.EMTPY_OBJECT);
  }

  @Test
  public void shouldCompleteJobWithZeroLengthVariables() {
    // given
    createJob(JOB_TYPE);
    final Record<JobBatchRecordValue> batchRecord =
        engineRule.jobs().withType(JOB_TYPE).activateAndWait();

    // when
    final Record<JobRecordValue> completedRecord =
        engineRule.completeJobAndWait(
            batchRecord.getValue().getJobKeys().get(0), new UnsafeBuffer(new byte[0]));

    // then
    Assertions.assertThat(completedRecord.getMetadata())
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.COMPLETED);
    assertThat(MsgPackConverter.convertToMsgPack(completedRecord.getValue().getVariables()))
        .isEqualTo(MsgPackHelper.EMTPY_OBJECT);
  }

  @Test
  public void shouldCompleteJobWithNoVariables() {
    // given
    createJob(JOB_TYPE);
    final Record<JobBatchRecordValue> batchRecord =
        engineRule.jobs().withType(JOB_TYPE).activateAndWait();
    final Record<JobRecordValue> activated = jobRecords(JobIntent.ACTIVATED).getFirst();

    // when
    final DirectBuffer variables = MsgPackUtil.asMsgPack(activated.getValue().getVariables());
    final Record<JobRecordValue> completedRecord =
        engineRule.completeJobAndWait(activated.getKey(), variables);

    // then
    Assertions.assertThat(completedRecord.getMetadata())
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.COMPLETED);
    MsgPackUtil.assertEquality(
        MsgPackHelper.EMTPY_OBJECT, completedRecord.getValue().getVariables());
  }

  @Test
  public void shouldThrowExceptionOnCompletionIfVariablesAreInvalid() {
    // given
    createJob(JOB_TYPE);
    final Record<JobBatchRecordValue> batchRecord =
        engineRule.jobs().withType(JOB_TYPE).activateAndWait();

    final byte[] invalidVariables = new byte[] {1}; // positive fixnum, i.e. no object

    // when
    final Throwable throwable =
        catchThrowable(
            () ->
                engineRule.completeJob(
                    batchRecord.getValue().getJobKeys().get(0),
                    new UnsafeBuffer(invalidVariables)));

    // then
    assertThat(throwable).isInstanceOf(RuntimeException.class);
    assertThat(throwable.getMessage()).contains("Property 'variables' is invalid");
    assertThat(throwable.getMessage())
        .contains("Expected document to be a root level object, but was 'INTEGER'");
  }

  @Test
  public void shouldRejectCompletionIfJobIsCompleted() {
    // given
    createJob(JOB_TYPE);
    final Record<JobBatchRecordValue> batchRecord =
        engineRule.jobs().withType(JOB_TYPE).activateAndWait();

    final DirectBuffer variables =
        MsgPackUtil.asMsgPack(batchRecord.getValue().getJobs().get(0).getVariables());
    final Long jobKey = batchRecord.getValue().getJobKeys().get(0);
    engineRule.completeJob(jobKey, variables);

    // when
    engineRule.completeJob(jobKey, variables);
    final Record<JobRecordValue> rejection =
        RecordingExporter.jobRecords(JobIntent.COMPLETE)
            .withRecordType(RecordType.COMMAND_REJECTION)
            .getFirst();

    // then
    Assertions.assertThat(rejection.getMetadata())
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasIntent(JobIntent.COMPLETE);
  }

  @Test
  public void shouldRejectCompletionIfJobIsFailed() {
    // given
    createJob(JOB_TYPE);

    // when
    final Record<JobBatchRecordValue> batchRecord =
        engineRule.jobs().withType(JOB_TYPE).activateAndWait();
    final Long jobKey = batchRecord.getValue().getJobKeys().get(0);
    engineRule.failJob(jobKey);

    engineRule.completeJob(
        jobKey, MsgPackUtil.asMsgPack(batchRecord.getValue().getJobs().get(0).getVariables()));
    final Record<JobRecordValue> rejection =
        jobRecords(JobIntent.COMPLETE).withRecordType(RecordType.COMMAND_REJECTION).getFirst();

    // then
    Assertions.assertThat(rejection.getMetadata())
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasIntent(JobIntent.COMPLETE);
  }

  private Record<JobRecordValue> createJob(final String type) {
    engineRule.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .serviceTask("task", b -> b.zeebeTaskType(type).done())
            .endEvent("end")
            .done());

    final long instanceKey =
        engineRule
            .createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID))
            .getValue()
            .getInstanceKey();

    return jobRecords(JobIntent.CREATED)
        .withType(type)
        .filter(r -> r.getValue().getHeaders().getWorkflowInstanceKey() == instanceKey)
        .getFirst();
  }
}
