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
import static io.zeebe.protocol.record.intent.JobIntent.TIME_OUT;
import static io.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobBatchIntent;
import io.zeebe.protocol.record.intent.JobIntent;
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

public class JobTimeOutTest {
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
  public void shouldTimeOutJob() {
    // given
    final long jobKey = ENGINE.createJob(jobType, PROCESS_ID).getKey();
    final long timeout = 10L;

    ENGINE.jobs().withType(jobType).withTimeout(timeout).activate();
    ENGINE.increaseTime(JobTimeoutTrigger.TIME_OUT_POLLING_INTERVAL);

    // when expired
    RecordingExporter.jobRecords(TIME_OUT).getFirst();
    ENGINE.jobs().withType(jobType).activate();

    // then activated again
    final List<Record<JobRecordValue>> jobEvents =
        RecordingExporter.jobRecords().limit(6).collect(Collectors.toList());

    assertThat(jobEvents).extracting(Record::getKey).contains(jobKey);
    assertThat(jobEvents)
        .extracting(Record::getIntent)
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
    ENGINE.createJob(jobType, PROCESS_ID);
    final long timeout = 10L;
    ENGINE.jobs().withType(jobType).withTimeout(timeout).activate();
    ENGINE.increaseTime(JobTimeoutTrigger.TIME_OUT_POLLING_INTERVAL);

    // when expired
    RecordingExporter.jobRecords(TIME_OUT).getFirst();
    ENGINE.jobs().withType(jobType).activate();

    // then activated again
    final Record jobActivated =
        RecordingExporter.jobRecords()
            .skipUntil(j -> j.getIntent() == TIME_OUT)
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
    final long instanceKey1 = createInstance();
    final long instanceKey2 = createInstance();

    final long jobKey1 =
        jobRecords(JobIntent.CREATED)
            .withType(jobType)
            .filter(r -> r.getValue().getWorkflowInstanceKey() == instanceKey1)
            .getFirst()
            .getKey();
    final long jobKey2 =
        jobRecords(JobIntent.CREATED)
            .withType(jobType)
            .filter(r -> r.getValue().getWorkflowInstanceKey() == instanceKey2)
            .getFirst()
            .getKey();
    final long timeout = 10L;

    ENGINE.jobs().withType(jobType).withTimeout(timeout).activate();

    // when
    RecordingExporter.jobRecords(ACTIVATED)
        .withWorkflowInstanceKey(instanceKey1)
        .limit(2)
        .getFirst();

    ENGINE.increaseTime(JobTimeoutTrigger.TIME_OUT_POLLING_INTERVAL);
    RecordingExporter.jobRecords(JobIntent.TIMED_OUT)
        .withWorkflowInstanceKey(instanceKey1)
        .getFirst();
    ENGINE.jobs().withType(jobType).activate();

    // then
    final List<Record<JobRecordValue>> activatedEvents =
        RecordingExporter.jobRecords(ACTIVATED)
            .filter(
                r -> {
                  final long wfInstanceKey = r.getValue().getWorkflowInstanceKey();
                  return wfInstanceKey == instanceKey1 || wfInstanceKey == instanceKey2;
                })
            .limit(4)
            .collect(Collectors.toList());

    assertThat(activatedEvents)
        .hasSize(4)
        .extracting(Record::getKey)
        .containsExactlyInAnyOrder(jobKey1, jobKey2, jobKey1, jobKey2);

    final List<Record<JobRecordValue>> expiredEvents =
        RecordingExporter.jobRecords(JobIntent.TIMED_OUT)
            .filter(
                r -> {
                  final long wfInstanceKey = r.getValue().getWorkflowInstanceKey();
                  return wfInstanceKey == instanceKey1 || wfInstanceKey == instanceKey2;
                })
            .limit(2)
            .collect(Collectors.toList());

    assertThat(expiredEvents)
        .extracting(Record::getKey)
        .containsExactlyInAnyOrder(jobKey1, jobKey2);
  }

  private long createInstance() {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent("start")
                .serviceTask("task", b -> b.zeebeTaskType(jobType).done())
                .endEvent("end")
                .done())
        .deploy();
    return ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();
  }
}
