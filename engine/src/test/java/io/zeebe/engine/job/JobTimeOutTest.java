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
import static io.zeebe.protocol.intent.JobIntent.COMPLETED;
import static io.zeebe.protocol.intent.JobIntent.FAILED;
import static io.zeebe.protocol.intent.JobIntent.TIME_OUT;
import static io.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.processor.workflow.job.JobTimeoutTrigger;
import io.zeebe.engine.util.EngineRule;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.RecordMetadata;
import io.zeebe.exporter.api.record.value.JobBatchRecordValue;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.intent.JobBatchIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

public class JobTimeOutTest {
  private static final String PROCESS_ID = "process";

  @Rule public EngineRule engineRule = new EngineRule();

  @Test
  public void shouldNotTimeOutIfDeadlineNotExceeded() {
    // given
    engineRule.getClock().pinCurrentTime();
    final Duration timeout = Duration.ofSeconds(60);
    final String jobType = "jobType";

    createJob(jobType);

    engineRule.jobs().withType(jobType).withTimeout(timeout.toMillis()).activate();
    jobRecords(ACTIVATED).getFirst();

    // when
    engineRule.getClock().addTime(timeout.minus(Duration.ofSeconds(1)));

    // then
    assertNoMoreJobsReceived(ACTIVATED);
  }

  @Test
  public void shouldNotTimeOutIfJobCompleted() {
    // given
    engineRule.getClock().pinCurrentTime();
    final Duration timeout = Duration.ofSeconds(60);
    final String jobType = "jobType";

    createJob(jobType);

    final Record<JobBatchRecordValue> batchRecord =
        engineRule.jobs().withType(jobType).withTimeout(timeout.toMillis()).activateAndWait();
    engineRule.job().withVariables("{}").complete(batchRecord.getValue().getJobKeys().get(0));

    // when
    engineRule.getClock().addTime(timeout.plus(Duration.ofSeconds(1)));

    // then
    assertNoMoreJobsReceived(COMPLETED);
  }

  @Test
  public void shouldNotTimeOutIfJobFailed() {
    // given
    engineRule.getClock().pinCurrentTime();
    final Duration timeout = Duration.ofSeconds(60);
    final String jobType = "jobType";

    createJob(jobType);

    final Record<JobBatchRecordValue> batchRecord =
        engineRule.jobs().withType(jobType).withTimeout(timeout.toMillis()).activateAndWait();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    engineRule.job().withRetries(0).fail(jobKey);

    // when
    engineRule.getClock().addTime(timeout.plus(Duration.ofSeconds(1)));

    // then
    assertNoMoreJobsReceived(FAILED);
  }

  @Test
  public void shouldTimeOutJob() {
    // given
    final String jobType = "foo";
    final long jobKey1 = createJob(jobType);
    final long timeout = 10L;

    engineRule.jobs().withType(jobType).withTimeout(timeout).activateAndWait();
    engineRule.getClock().addTime(JobTimeoutTrigger.TIME_OUT_POLLING_INTERVAL);

    // when expired
    RecordingExporter.jobRecords(TIME_OUT).getFirst();
    engineRule.jobs().withType(jobType).activateAndWait();

    // then activated again
    final List<Record<JobRecordValue>> jobEvents =
        RecordingExporter.jobRecords().limit(6).collect(Collectors.toList());

    assertThat(jobEvents).extracting(Record::getKey).contains(jobKey1);
    assertThat(jobEvents)
        .extracting(Record::getMetadata)
        .extracting(RecordMetadata::getIntent)
        .containsExactly(
            JobIntent.CREATE,
            JobIntent.CREATED,
            JobIntent.ACTIVATED,
            JobIntent.TIME_OUT,
            JobIntent.TIMED_OUT,
            JobIntent.ACTIVATED);
  }

  @Test
  public void shouldSetCorrectSourcePositionAfterJobTimeOut() {
    // given
    final String jobType = "foo";
    createJob(jobType);
    final long timeout = 10L;
    engineRule.jobs().withType(jobType).withTimeout(timeout).activateAndWait();
    engineRule.getClock().addTime(JobTimeoutTrigger.TIME_OUT_POLLING_INTERVAL);

    // when expired
    RecordingExporter.jobRecords(TIME_OUT).getFirst();
    engineRule.jobs().withType(jobType).activateAndWait();

    // then activated again
    final Record jobActivated =
        RecordingExporter.jobRecords()
            .skipUntil(j -> j.getMetadata().getIntent() == TIME_OUT)
            .withIntent(ACTIVATED)
            .getFirst();

    final Record firstActivateCommand =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATE).getFirst();
    assertThat(jobActivated.getSourceRecordPosition())
        .isNotEqualTo(firstActivateCommand.getPosition());

    final Record secondActivateCommand =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATE)
            .skipUntil(s -> s.getPosition() > firstActivateCommand.getPosition())
            .findFirst()
            .get();

    assertThat(jobActivated.getSourceRecordPosition())
        .isEqualTo(secondActivateCommand.getPosition());
  }

  @Test
  public void shouldExpireMultipleActivatedJobsAtOnce() {
    // given
    final String jobType = "foo";
    final long jobKey1 = createJob(jobType);
    final long jobKey2 = createJob(jobType);
    final long timeout = 10L;

    engineRule.jobs().withType(jobType).withTimeout(timeout).activateAndWait();

    // when
    RecordingExporter.jobRecords(ACTIVATED).limit(2).count();
    engineRule.getClock().addTime(JobTimeoutTrigger.TIME_OUT_POLLING_INTERVAL);
    RecordingExporter.jobRecords(JobIntent.TIMED_OUT).getFirst();
    engineRule.jobs().withType(jobType).activateAndWait();

    // then
    final List<Record<JobRecordValue>> activatedEvents =
        RecordingExporter.jobRecords(ACTIVATED).limit(4).collect(Collectors.toList());

    assertThat(activatedEvents)
        .hasSize(4)
        .extracting(Record::getKey)
        .containsExactlyInAnyOrder(jobKey1, jobKey2, jobKey1, jobKey2);

    final List<Record<JobRecordValue>> expiredEvents =
        RecordingExporter.jobRecords(JobIntent.TIMED_OUT).limit(2).collect(Collectors.toList());

    assertThat(expiredEvents)
        .extracting(Record::getKey)
        .containsExactlyInAnyOrder(jobKey1, jobKey2);
  }

  //  private long createJob(final String type) {
  //    return apiRule.partitionClient().createJob(type);
  //  }

  private long createJob(final String type) {
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
        .getFirst()
        .getKey();
  }

  private void assertNoMoreJobsReceived(JobIntent lastIntent) {
    final long eventCount =
        jobRecords().limit(r -> r.getMetadata().getIntent() == lastIntent).count();
    assertThat(RecordingExporter.jobRecords().skip(eventCount).exists()).isFalse();
  }
}
