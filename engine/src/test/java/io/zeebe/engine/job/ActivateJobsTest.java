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

import static io.zeebe.exporter.api.record.Assertions.assertThat;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.test.util.record.RecordingExporter.jobBatchRecords;
import static io.zeebe.test.util.record.RecordingExporter.jobRecords;
import static io.zeebe.test.util.record.RecordingExporter.workflowInstanceRecords;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.processor.workflow.message.command.VarDataEncodingEncoder;
import io.zeebe.engine.util.EngineRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.JobBatchRecordValue;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.exporter.api.record.value.job.Headers;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.protocol.RecordType;
import io.zeebe.protocol.RejectionType;
import io.zeebe.protocol.intent.JobBatchIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.Strings;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.util.sched.clock.ControlledActorClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.internal.bytebuddy.utility.RandomString;
import org.junit.Rule;
import org.junit.Test;

public class ActivateJobsTest {
  private static final String JSON_VARIABLES = "{\"foo\":\"bar\"}";
  private static final byte[] VARIABLES_MSG_PACK = MsgPackUtil.asMsgPackReturnArray(JSON_VARIABLES);
  private static final String LONG_CUSTOM_HEADER_VALUE = RandomString.make(128);

  private static final String PROCESS_ID = "process";
  private static final String TASK_TYPE = "type";

  private static final BpmnModelInstance MODEL =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .serviceTask("task", b -> b.zeebeTaskType(TASK_TYPE).done())
          .endEvent("end")
          .done();

  @Rule public EngineRule engineRule = new EngineRule();

  @Test
  public void shouldRejectInvalidAmount() {
    // when
    engineRule.jobs().withType(TASK_TYPE).withMaxJobsToActivate(0).activate();
    final Record<JobBatchRecordValue> rejection =
        jobBatchRecords().withRecordType(RecordType.COMMAND_REJECTION).getFirst();

    // then
    assertThat(rejection.getMetadata().getRejectionType())
        .isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getMetadata().getRejectionReason())
        .isEqualTo(
            "Expected to activate job batch with max jobs to activate to be greater than zero, but it was '0'");
  }

  @Test
  public void shouldRejectInvalidTimeout() {
    // when
    engineRule.jobs().withType(TASK_TYPE).withTimeout(Duration.ofSeconds(0).toMillis()).activate();
    final Record<JobBatchRecordValue> rejection =
        jobBatchRecords().withRecordType(RecordType.COMMAND_REJECTION).getFirst();

    // then
    assertThat(rejection.getMetadata().getRejectionType())
        .isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getMetadata().getRejectionReason())
        .isEqualTo(
            "Expected to activate job batch with timeout to be greater than zero, but it was '0'");
  }

  @Test
  public void shouldRejectInvalidType() {
    // when
    engineRule.jobs().withType("").activate();
    final Record<JobBatchRecordValue> rejection =
        jobBatchRecords().withRecordType(RecordType.COMMAND_REJECTION).getFirst();

    // then
    assertThat(rejection.getMetadata().getRejectionType())
        .isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getMetadata().getRejectionReason())
        .isEqualTo("Expected to activate job batch with type to be present, but it was blank");
  }

  @Test
  public void shouldRejectInvalidWorker() {
    // when
    engineRule.jobs().withType(TASK_TYPE).byWorker("").activate();
    final Record<JobBatchRecordValue> rejection =
        jobBatchRecords().withRecordType(RecordType.COMMAND_REJECTION).getFirst();

    // then
    assertThat(rejection.getMetadata().getRejectionType())
        .isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getMetadata().getRejectionReason())
        .isEqualTo("Expected to activate job batch with worker to be present, but it was blank");
  }

  @Test
  public void shouldActivateSingleJob() {
    // given
    final Long expectedJobKey = deployAndCreateJobs(TASK_TYPE, 3).get(0);
    final String worker = "myTestWorker";
    final Duration timeout = Duration.ofMinutes(12);

    // when
    final Record<JobBatchRecordValue> batchRecord =
        engineRule
            .jobs()
            .withType(TASK_TYPE)
            .byWorker(worker)
            .withTimeout(timeout.toMillis())
            .withMaxJobsToActivate(1)
            .activateAndWait();

    final List<JobRecordValue> jobs = batchRecord.getValue().getJobs();
    final List<Long> jobKeys = batchRecord.getValue().getJobKeys();

    // then
    assertThat(batchRecord.getMetadata().getIntent()).isEqualTo(JobBatchIntent.ACTIVATED);

    assertThat(jobKeys).hasSize(1);
    assertThat(jobs).hasSize(1);
    assertThat(jobKeys.get(0)).isEqualTo(expectedJobKey);
    assertThat(jobs.get(0)).hasRetries(3).hasWorker(worker).hasType(TASK_TYPE);

    assertThat(jobs.get(0).getVariables()).isEqualTo(JSON_VARIABLES);

    final Record<JobRecordValue> jobRecord =
        jobRecords(JobIntent.ACTIVATED).withType(TASK_TYPE).getFirst();
    assertThat(jobRecord).hasKey(expectedJobKey);
    assertThat(jobRecord.getValue()).hasRetries(3).hasWorker(worker);

    final Record<JobBatchRecordValue> jobBatchActivateRecord =
        jobBatchRecords(JobBatchIntent.ACTIVATE).withType(TASK_TYPE).getFirst();
    final Record<JobBatchRecordValue> jobBatchActivatedRecord =
        jobBatchRecords(JobBatchIntent.ACTIVATED).withType(TASK_TYPE).getFirst();
    Assertions.assertThat(jobBatchActivatedRecord)
        .hasKey(batchRecord.getKey())
        .hasSourceRecordPosition(jobBatchActivateRecord.getPosition());
  }

  @Test
  public void shouldActivateJobBatch() {
    // given
    final List<Long> expectedJobKeys = deployAndCreateJobs(5).subList(0, 3);

    // when
    final List<Long> jobKeys = activateJobs(3);

    // then
    assertThat(jobKeys).containsExactlyInAnyOrderElementsOf(expectedJobKeys);
  }

  @Test
  public void shouldActivateJobBatches() {
    // given
    final List<Long> jobKeys = deployAndCreateJobs(12);
    final List<Long> expectedFirstJobKeys = jobKeys.subList(0, 3);
    final List<Long> expectedSecondJobKeys = jobKeys.subList(3, 7);
    final List<Long> expectedThirdJobKeys = jobKeys.subList(7, 10);

    // when
    final List<Long> firstJobs = activateJobs(3);
    final List<Long> secondJobs = activateJobs(4);
    final List<Long> thirdJobs = activateJobs(3);

    // then
    assertThat(firstJobs).containsOnlyElementsOf(expectedFirstJobKeys);
    assertThat(secondJobs).containsOnlyElementsOf(expectedSecondJobKeys);
    assertThat(thirdJobs).containsOnlyElementsOf(expectedThirdJobKeys);
  }

  @Test
  public void shouldReturnEmptyBatchIfNoJobsAvailable() {
    // when
    final List<Long> jobEvents = activateJobs(3);

    // then
    assertThat(jobEvents).isEmpty();
  }

  @Test
  public void shouldCompleteActivatedJobs() {
    // given
    final int jobAmount = 5;
    final List<Long> jobKeys = deployAndCreateJobs(jobAmount);
    final List<Long> activateJobKeys = activateJobs(jobAmount);

    // when
    activateJobKeys.forEach(k -> this.completeJob(k));

    // then
    final List<Record<JobRecordValue>> records =
        jobRecords(JobIntent.COMPLETED).limit(jobAmount).collect(Collectors.toList());

    assertThat(records).extracting(Record::getKey).containsOnlyElementsOf(jobKeys);
  }

  @Test
  public void shouldOnlyReturnJobsOfCorrectType() {
    // given
    final String type = "someType";
    final List<Long> jobKeys = deployAndCreateJobs(type, 3);
    deployAndCreateJobs("different" + type, 5);
    jobKeys.addAll(deployAndCreateJobs(type, 4));

    // when
    final List<Long> jobs = activateJobs(type, 7);

    // then
    assertThat(jobs).containsOnlyElementsOf(jobKeys);

    final List<Record<JobRecordValue>> records =
        jobRecords(JobIntent.ACTIVATED)
            .withType(type)
            .limit(jobKeys.size())
            .collect(Collectors.toList());

    assertThat(records).extracting(Record::getKey).containsOnlyElementsOf(jobKeys);
    assertThat(records).extracting("value.type").containsOnly(type);
  }

  @Test
  public void shouldActivateJobsFromWorkflow() {
    // given
    final int jobAmount = 10;
    final String jobType = TASK_TYPE;
    final String jobType2 = Strings.newRandomValidBpmnId();
    final String jobType3 = Strings.newRandomValidBpmnId();

    deployWorkflow(jobType, jobType2, jobType3);
    final List<Long> workflowInstanceKeys = createWorkflowInstances(jobAmount, VARIABLES_MSG_PACK);

    // when activating and completing all jobs
    waitUntil(
        () ->
            jobRecords(JobIntent.CREATED).withType(jobType).limit(jobAmount).count() == jobAmount);
    activateJobs(jobType, jobAmount).forEach(this::completeJob);

    waitUntil(
        () ->
            jobRecords(JobIntent.CREATED).withType(jobType2).limit(jobAmount).count() == jobAmount);
    activateJobs(jobType2, jobAmount).forEach(this::completeJob);

    waitUntil(
        () ->
            jobRecords(JobIntent.CREATED).withType(jobType3).limit(jobAmount).count() == jobAmount);
    activateJobs(jobType3, jobAmount).forEach(this::completeJob);

    // then all workflow instances are completed
    final List<Record<WorkflowInstanceRecordValue>> records =
        workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withBpmnProcessId(PROCESS_ID)
            .filter(r -> r.getKey() == r.getValue().getWorkflowInstanceKey())
            .limit(jobAmount)
            .collect(Collectors.toList());

    assertThat(records)
        .extracting(r -> r.getValue().getWorkflowInstanceKey())
        .containsOnlyElementsOf(workflowInstanceKeys);
  }

  @Test
  public void shouldActivateJobsWithLongCustomHeaders() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                "task",
                b -> b.zeebeTaskType(TASK_TYPE).zeebeTaskHeader("foo", LONG_CUSTOM_HEADER_VALUE))
            .endEvent()
            .done();

    engineRule.deploy(modelInstance);
    final long workflowInstanceKey = createWorkflowInstance();
    RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();

    // when
    activateJob(TASK_TYPE);
    engineRule.job().ofInstance(workflowInstanceKey).withType(TASK_TYPE).complete();

    // then
    final JobRecordValue jobRecord =
        RecordingExporter.jobRecords(JobIntent.ACTIVATED).withType(TASK_TYPE).getFirst().getValue();
    assertThat(jobRecord.getCustomHeaders().get("foo")).isEqualTo(LONG_CUSTOM_HEADER_VALUE);
  }

  @Test
  public void shouldFetchFullJobRecordFromWorkflow() {
    // given
    final ControlledActorClock clock = engineRule.getClock();
    clock.pinCurrentTime();

    final String worker = "testWorker";
    final Duration timeout = Duration.ofMinutes(4);

    final Instant deadline = clock.getCurrentTime().plusMillis(timeout.toMillis());

    deployWorkflow(TASK_TYPE);
    createWorkflowInstances(1, VARIABLES_MSG_PACK);
    final Record<JobRecordValue> jobRecord =
        jobRecords(JobIntent.CREATED).withType(TASK_TYPE).getFirst();

    // when
    final long jobKey =
        engineRule
            .jobs()
            .withType(TASK_TYPE)
            .byWorker(worker)
            .withTimeout(timeout.toMillis())
            .withMaxJobsToActivate(1)
            .activateAndWait()
            .getKey();

    final JobRecordValue job =
        jobBatchRecords(JobBatchIntent.ACTIVATED)
            .withRecordKey(jobKey)
            .getFirst()
            .getValue()
            .getJobs()
            .get(0);

    // then
    Assertions.assertThat(job)
        .hasType(TASK_TYPE)
        .hasWorker(worker)
        .hasRetries(3)
        .hasDeadline(deadline);

    assertThat(job.getVariables()).isEqualTo(JSON_VARIABLES);

    final Headers jobRecordHeaders = jobRecord.getValue().getHeaders();
    Assertions.assertThat(job.getHeaders())
        .hasBpmnProcessId(jobRecordHeaders.getBpmnProcessId())
        .hasWorkflowDefinitionVersion(jobRecordHeaders.getWorkflowDefinitionVersion())
        .hasWorkflowKey(jobRecordHeaders.getWorkflowKey())
        .hasWorkflowInstanceKey(jobRecordHeaders.getWorkflowInstanceKey())
        .hasElementId(jobRecordHeaders.getElementId())
        .hasElementInstanceKey(jobRecordHeaders.getElementInstanceKey());

    assertThat(job.getCustomHeaders()).isEqualTo(jobRecord.getValue().getCustomHeaders());
  }

  @Test
  public void shouldLimitJobsInBatch() {
    // given
    final int variablesSize = VarDataEncodingEncoder.lengthMaxValue() / 3;
    final byte[] variables =
        MsgPackUtil.asMsgPackReturnArray("{\"key\": \"" + RandomString.make(variablesSize) + "\"}");

    // when
    deployAndCreateJobs(TASK_TYPE, 3, variables);
    final List<Long> jobKeys = activateJobs(TASK_TYPE, 3);

    // then
    assertThat(jobKeys).hasSize(2);
    final List<Long> remainingJobKeys = activateJobs(1);
    assertThat(remainingJobKeys).hasSize(1);
  }

  private Record<JobRecordValue> completeJob(long jobKey) {
    return engineRule.completeJobAndWait(jobKey, new UnsafeBuffer(VARIABLES_MSG_PACK));
  }

  private Long activateJob(String type) {
    return activateJobs(type, 1).get(0);
  }

  private List<Long> activateJobs(String type, int amount) {
    return engineRule
        .jobs()
        .withType(type)
        .withMaxJobsToActivate(amount)
        .activateAndWait()
        .getValue()
        .getJobKeys();
  }

  private List<Long> activateJobs(int amount) {
    return activateJobs(TASK_TYPE, amount);
  }

  private long createWorkflowInstance() {
    return createWorkflowInstances(1, VARIABLES_MSG_PACK).get(0);
  }

  private List<Long> createWorkflowInstances(int amount) {
    return createWorkflowInstances(amount, VARIABLES_MSG_PACK);
  }

  private List<Long> createWorkflowInstances(int amount, byte[] variables) {
    return IntStream.range(0, amount)
        .boxed()
        .map(
            i ->
                engineRule.createWorkflowInstance(
                    r -> r.setBpmnProcessId(PROCESS_ID).setVariables(new UnsafeBuffer(variables))))
        .collect(Collectors.toList());
  }

  private List<Long> deployAndCreateJobs(
      final String type, final int amount, final byte[] variables) {
    deployWorkflow(type);
    final List<Long> instanceKeys = createWorkflowInstances(amount, variables);

    return jobRecords(JobIntent.CREATED)
        .withType(type)
        .filter(r -> instanceKeys.contains(r.getValue().getHeaders().getWorkflowInstanceKey()))
        .limit(amount)
        .map(Record::getKey)
        .collect(Collectors.toList());
  }

  private List<Long> deployAndCreateJobs(int amount) {
    return deployAndCreateJobs(TASK_TYPE, amount);
  }

  private List<Long> deployAndCreateJobs(String type, int amount) {
    return deployAndCreateJobs(type, amount, VARIABLES_MSG_PACK);
  }

  private void deployWorkflow(String type) {
    engineRule.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .serviceTask("task", b -> b.zeebeTaskType(type).done())
            .endEvent("end")
            .done());
  }

  private void deployWorkflow(String... types) {
    AbstractFlowNodeBuilder<?, ?> builder =
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent("start");

    for (final String type : types) {
      builder = builder.serviceTask(type, b -> b.zeebeTaskType(type));
    }

    engineRule.deploy(builder.done());
  }
}
