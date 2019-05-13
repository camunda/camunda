/*
 * Zeebe Broker Core
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
package io.zeebe.broker.engine.job;

import static io.zeebe.exporter.api.record.Assertions.assertThat;
import static io.zeebe.protocol.Protocol.DEPLOYMENT_PARTITION;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.test.util.record.RecordingExporter.jobBatchRecords;
import static io.zeebe.test.util.record.RecordingExporter.jobRecords;
import static io.zeebe.test.util.record.RecordingExporter.workflowInstanceRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.JobBatchRecordValue;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.exporter.api.record.value.job.Headers;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.clientapi.VarDataEncodingEncoder;
import io.zeebe.protocol.impl.record.value.deployment.ResourceType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.JobBatchIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.Strings;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.util.sched.clock.ControlledActorClock;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import one.util.streamex.StreamEx;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.internal.bytebuddy.utility.RandomString;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ActivateJobsTest {

  public static final String JSON_VARIABLES = "{\"foo\": \"bar\"}";
  public static final byte[] VARIABLES_MSG_PACK = MsgPackUtil.asMsgPackReturnArray(JSON_VARIABLES);
  public static final String LONG_CUSTOM_HEADER_VALUE = RandomString.make(128);

  public static EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public static ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);

  @ClassRule public static RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private String processId;
  private String jobType;

  @Before
  public void setUp() {
    processId = Strings.newRandomValidBpmnId();
    jobType = Strings.newRandomValidBpmnId();
  }

  @Test
  public void shouldRejectInvalidAmount() {
    // when
    final ExecuteCommandResponse response =
        apiRule
            .createCmdRequest()
            .type(ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE)
            .command()
            .put("type", jobType)
            .put("worker", "testWorker")
            .put("timeout", Duration.ofSeconds(10).toMillis())
            .put("retries", 3)
            .put("maxJobsToActivate", 0)
            .put("jobs", Collections.emptyList())
            .done()
            .sendAndAwait();

    // then
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(response.getRejectionReason())
        .isEqualTo(
            "Expected to activate job batch with max jobs to activate to be greater than zero, but it was '0'");
  }

  @Test
  public void shouldRejectInvalidTimeout() {
    // when
    final ExecuteCommandResponse response =
        apiRule
            .createCmdRequest()
            .type(ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE)
            .command()
            .put("type", jobType)
            .put("worker", "testWorker")
            .put("timeout", Duration.ofSeconds(0).toMillis())
            .put("retries", 3)
            .put("maxJobsToActivate", 3)
            .put("jobs", Collections.emptyList())
            .done()
            .sendAndAwait();

    // then
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(response.getRejectionReason())
        .isEqualTo(
            "Expected to activate job batch with timeout to be greater than zero, but it was '0'");
  }

  @Test
  public void shouldRejectInvalidType() {
    // when
    final ExecuteCommandResponse response =
        apiRule
            .createCmdRequest()
            .type(ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE)
            .command()
            .put("type", "")
            .put("worker", "testWorker")
            .put("timeout", Duration.ofSeconds(10).toMillis())
            .put("retries", 3)
            .put("maxJobsToActivate", 3)
            .put("jobs", Collections.emptyList())
            .done()
            .sendAndAwait();

    // then
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(response.getRejectionReason())
        .isEqualTo("Expected to activate job batch with type to be present, but it was blank");
  }

  @Test
  public void shouldRejectInvalidWorker() {
    // when
    final ExecuteCommandResponse response =
        apiRule
            .createCmdRequest()
            .type(ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE)
            .command()
            .put("type", jobType)
            .put("worker", "")
            .put("timeout", Duration.ofSeconds(10).toMillis())
            .put("retries", 3)
            .put("maxJobsToActivate", 3)
            .put("jobs", Collections.emptyList())
            .done()
            .sendAndAwait();

    // then
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(response.getRejectionReason())
        .isEqualTo("Expected to activate job batch with worker to be present, but it was blank");
  }

  @Test
  public void shouldActivateSingleJob() {
    // given
    final ControlledActorClock clock = brokerRule.getClock();
    clock.pinCurrentTime();

    final Long expectedJobKey = createJobs(jobType, 3).get(0);
    final String worker = "myTestWorker";
    final Duration timeout = Duration.ofMinutes(12);
    final Instant deadline = clock.getCurrentTime().plusMillis(timeout.toMillis());

    // when
    final ExecuteCommandResponse response =
        apiRule
            .createCmdRequest()
            .type(ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE)
            .command()
            .put("type", jobType)
            .put("worker", worker)
            .put("timeout", timeout.toMillis())
            .put("maxJobsToActivate", 1)
            .put("jobs", Collections.emptyList())
            .done()
            .sendAndAwait();
    final List<Long> jobKeys = (List<Long>) response.getValue().get("jobKeys");
    final List<Map<String, Object>> jobs =
        (List<Map<String, Object>>) response.getValue().get("jobs");

    // then
    assertThat(response.getIntent()).isEqualTo(JobBatchIntent.ACTIVATED);

    assertThat(jobKeys).hasSize(1);
    assertThat(jobs).hasSize(1);
    assertThat(jobKeys.get(0)).isEqualTo(expectedJobKey);
    assertThat(jobs.get(0))
        .contains(
            entry("retries", 3L),
            entry("worker", worker),
            entry("deadline", deadline.toEpochMilli()),
            entry("type", jobType));

    MsgPackUtil.assertEquality((byte[]) jobs.get(0).get("variables"), JSON_VARIABLES);

    final Record<JobRecordValue> jobRecord =
        jobRecords(JobIntent.ACTIVATED).withType(jobType).getFirst();
    assertThat(jobRecord).hasKey(expectedJobKey);
    assertThat(jobRecord.getValue()).hasRetries(3).hasWorker(worker).hasDeadline(deadline);

    final Record<JobBatchRecordValue> jobBatchActivateRecord =
        jobBatchRecords(JobBatchIntent.ACTIVATE).withType(jobType).getFirst();
    final Record<JobBatchRecordValue> jobBatchActivatedRecord =
        jobBatchRecords(JobBatchIntent.ACTIVATED).withType(jobType).getFirst();
    assertThat(jobBatchActivatedRecord)
        .hasKey(response.getKey())
        .hasSourceRecordPosition(jobBatchActivateRecord.getPosition())
        .hasTimestamp(clock.getCurrentTime());
  }

  @Test
  public void shouldActivateJobBatch() {
    // given
    final List<Long> expectedJobKeys = createJobs(5).subList(0, 3);

    // when
    final List<Job> jobs = activateJobs(3);

    // then
    assertThat(jobs).extracting(Job::getKey).containsOnlyElementsOf(expectedJobKeys);

    final List<Record<JobRecordValue>> record =
        jobRecords(JobIntent.ACTIVATED).withType(jobType).limit(3).collect(Collectors.toList());
    assertThat(record).extracting(Record::getKey).containsOnlyElementsOf(expectedJobKeys);
  }

  @Test
  public void shouldActivateJobBatches() {
    // given
    final List<Long> jobKeys = createJobs(12);
    final List<Long> expectedFirstJobKeys = jobKeys.subList(0, 3);
    final List<Long> expectedSecondJobKeys = jobKeys.subList(3, 7);
    final List<Long> expectedThirdJobKeys = jobKeys.subList(7, 10);

    // when
    final List<Job> firstJobs = activateJobs(3);
    final List<Job> secondJobs = activateJobs(4);
    final List<Job> thirdJobs = activateJobs(3);

    // then
    assertThat(firstJobs).extracting(Job::getKey).containsOnlyElementsOf(expectedFirstJobKeys);
    assertThat(secondJobs).extracting(Job::getKey).containsOnlyElementsOf(expectedSecondJobKeys);
    assertThat(thirdJobs).extracting(Job::getKey).containsOnlyElementsOf(expectedThirdJobKeys);
  }

  @Test
  public void shouldReturnEmptyBatchIfNotJobsAvailable() {
    // when
    final List<Job> jobEvents = activateJobs(3);

    // then
    assertThat(jobEvents).isEmpty();
  }

  @Test
  public void shouldCompleteActivatedJobs() {
    // given
    final int jobAmount = 5;
    final List<Long> jobKeys = createJobs(jobAmount);
    final List<Job> jobs = activateJobs(jobAmount);

    // when
    jobs.forEach(this::completeJob);

    // then
    final List<Record<JobRecordValue>> records =
        jobRecords(JobIntent.COMPLETED).limit(jobAmount).collect(Collectors.toList());

    assertThat(records).extracting(Record::getKey).containsOnlyElementsOf(jobKeys);
  }

  @Test
  public void shouldOnlyReturnJobsOfCorrectType() {
    // given
    final List<Long> jobKeys = createJobs(jobType, 3);
    createJobs("different" + jobType, 5);
    jobKeys.addAll(createJobs(jobType, 4));

    // when
    final List<Job> jobs = activateJobs(jobType, 7);

    // then
    assertThat(jobs).extracting(Job::getKey).containsOnlyElementsOf(jobKeys);

    final List<Record<JobRecordValue>> records =
        jobRecords(JobIntent.ACTIVATED)
            .withType(jobType)
            .limit(jobKeys.size())
            .collect(Collectors.toList());

    assertThat(records).extracting(Record::getKey).containsOnlyElementsOf(jobKeys);
    assertThat(records).extracting("value.type").containsOnly(jobType);
  }

  @Test
  public void shouldActivateJobsFromWorkflow() {
    // given
    final int jobAmount = 10;
    final String jobType2 = Strings.newRandomValidBpmnId();
    final String jobType3 = Strings.newRandomValidBpmnId();
    deployWorkflow(jobType, jobType2, jobType3);
    final List<Long> workflowInstanceKeys = createWorkflowInstances(jobAmount);

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
            .withBpmnProcessId(processId)
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
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(
                "task",
                b -> {
                  b.zeebeTaskType(jobType).zeebeTaskHeader("foo", LONG_CUSTOM_HEADER_VALUE);
                })
            .endEvent()
            .done();

    apiRule.partitionClient().deployWithResponse(Bpmn.convertToString(modelInstance).getBytes());
    final long workflowInstanceKey =
        apiRule
            .partitionClient()
            .createWorkflowInstance(r -> r.setBpmnProcessId(processId))
            .getWorkflowInstanceKey();

    // when
    apiRule.partitionClient().completeJobOfType(jobType);

    // then
    final JobRecordValue jobRecord =
        RecordingExporter.jobRecords(JobIntent.ACTIVATED)
            .withType(jobType)
            .limit(1)
            .getFirst()
            .getValue();
    assertThat(jobRecord.getCustomHeaders().get("foo")).isEqualTo(LONG_CUSTOM_HEADER_VALUE);
  }

  @Test
  public void shouldFetchFullJobRecordFromWorkflow() {
    // given
    final ControlledActorClock clock = brokerRule.getClock();
    clock.pinCurrentTime();

    final String worker = "testWorker";
    final Duration timeout = Duration.ofMinutes(4);

    final Instant deadline = clock.getCurrentTime().plusMillis(timeout.toMillis());

    deployWorkflow(jobType);
    createWorkflowInstance(processId);
    final Record<JobRecordValue> jobRecord =
        jobRecords(JobIntent.CREATED).withType(jobType).getFirst();

    // when
    final Job job = activateJobs(jobType, worker, timeout, 1).get(0);

    // then
    final Map<String, Object> value = job.getValue();
    assertThat(value)
        .contains(
            entry("type", jobType),
            entry("worker", worker),
            entry("retries", 3L),
            entry("deadline", deadline.toEpochMilli()));

    MsgPackUtil.assertEquality((byte[]) value.get("variables"), "{'foo': 'bar'}");

    final Map<String, Object> headers = (Map<String, Object>) value.get("headers");
    final Headers jobRecordHeaders = jobRecord.getValue().getHeaders();
    assertThat(headers)
        .contains(
            entry("bpmnProcessId", jobRecordHeaders.getBpmnProcessId()),
            entry(
                "workflowDefinitionVersion",
                (long) jobRecordHeaders.getWorkflowDefinitionVersion()),
            entry("workflowKey", jobRecordHeaders.getWorkflowKey()),
            entry("workflowInstanceKey", jobRecordHeaders.getWorkflowInstanceKey()),
            entry("elementId", jobRecordHeaders.getElementId()),
            entry("elementInstanceKey", jobRecordHeaders.getElementInstanceKey()));

    final Map<String, Object> customHeaders = (Map<String, Object>) value.get("customHeaders");
    assertThat(customHeaders).isEqualTo(jobRecord.getValue().getCustomHeaders());
  }

  @Test
  public void shouldLimitJobsInBatch() {
    // given
    final int variablesSize = VarDataEncodingEncoder.lengthMaxValue() / 3;
    final String variables = "{\"key\": \"" + RandomString.make(variablesSize) + "\"}";

    // when
    createJobs(jobType, 3, variables);
    final List<Job> jobs = activateJobs(jobType, 3);

    // then
    assertThat(jobs).hasSize(2);
    final List<Job> remainingJobs = activateJobs(jobType, 1);
    assertThat(remainingJobs).hasSize(1);
  }

  private List<Long> createJobs(int amount) {
    return createJobs(jobType, amount);
  }

  private List<Long> createJobs(String jobType, int amount) {
    return createJobs(jobType, amount, JSON_VARIABLES);
  }

  private List<Long> createJobs(String jobType, int amount, String variables) {
    return IntStream.range(0, amount)
        .boxed()
        .map(i -> createJob(jobType, variables))
        .collect(Collectors.toList());
  }

  private long createJob(String jobType, String variables) {
    return apiRule.partitionClient().createJob(jobType, b -> b.zeebeTaskRetries(3), variables);
  }

  private List<Job> activateJobs(int amount) {
    return activateJobs(jobType, amount);
  }

  private List<Job> activateJobs(String type, int amount) {
    return activateJobs(type, "testWorker", Duration.ofMinutes(5), amount);
  }

  private List<Job> activateJobs(String jobType, String worker, Duration timeout, int amount) {
    final Map<String, Object> response =
        apiRule
            .createCmdRequest()
            .type(ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE)
            .command()
            .put("type", jobType)
            .put("worker", worker)
            .put("timeout", timeout.toMillis())
            .put("maxJobsToActivate", amount)
            .put("jobs", Collections.emptyList())
            .done()
            .sendAndAwait()
            .getValue();

    return StreamEx.zip(
            ((List<Long>) response.get("jobKeys")),
            ((List<Map<String, Object>>) response.get("jobs")),
            Job::new)
        .collect(Collectors.toList());
  }

  private long completeJob(Job job) {
    return apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.COMPLETE)
        .key(job.key)
        .command()
        .putAll(job.value)
        .done()
        .sendAndAwait()
        .getKey();
  }

  private long deployWorkflow(String... taskTypes) {
    AbstractFlowNodeBuilder<?, ?> builder = Bpmn.createExecutableProcess(processId).startEvent();

    for (String taskType : taskTypes) {
      builder =
          builder.serviceTask(
              taskType,
              b -> b.zeebeTaskType(taskType).zeebeTaskRetries(3).zeebeTaskHeader("model", "true"));
    }

    final BpmnModelInstance model = builder.endEvent().done();
    return apiRule
        .createCmdRequest()
        .partitionId(DEPLOYMENT_PARTITION)
        .type(ValueType.DEPLOYMENT, DeploymentIntent.CREATE)
        .command()
        .put(
            "resources",
            Collections.singletonList(deploymentResource(bpmnXml(model), "process.bpmn")))
        .done()
        .sendAndAwait()
        .getKey();
  }

  private Map<String, Object> deploymentResource(final byte[] resource, final String name) {
    final Map<String, Object> deploymentResource = new HashMap<>();
    deploymentResource.put("resource", resource);
    deploymentResource.put("resourceType", ResourceType.BPMN_XML);
    deploymentResource.put("resourceName", name);

    return deploymentResource;
  }

  private byte[] bpmnXml(final BpmnModelInstance definition) {
    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(outStream, definition);
    return outStream.toByteArray();
  }

  private List<Long> createWorkflowInstances(int amount) {
    return Stream.generate(() -> processId)
        .limit(amount)
        .map(this::createWorkflowInstance)
        .collect(Collectors.toList());
  }

  private long createWorkflowInstance(String processId) {
    return apiRule
        .partitionClient()
        .createWorkflowInstance(
            r -> r.setBpmnProcessId(processId).setVariables(new UnsafeBuffer(VARIABLES_MSG_PACK)))
        .getInstanceKey();
  }

  static class Job {
    final long key;
    final Map<String, Object> value;

    Job(long key, Map<String, Object> value) {
      this.key = key;
      this.value = value;
    }

    public long getKey() {
      return key;
    }

    public Map<String, Object> getValue() {
      return value;
    }
  }
}
