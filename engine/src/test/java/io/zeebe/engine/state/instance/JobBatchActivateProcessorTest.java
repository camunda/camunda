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
package io.zeebe.engine.state.instance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.job.JobBatchActivateProcessor;
import io.zeebe.engine.util.MockTypedRecord;
import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.protocol.clientapi.VarDataEncodingEncoder;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import java.time.Duration;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class JobBatchActivateProcessorTest {
  @ClassRule public static ZeebeStateRule zeebeState = new ZeebeStateRule();

  @Mock TypedStreamWriter streamWriter;
  @Mock TypedResponseWriter responseWriter;
  @Spy JobState jobState = zeebeState.getZeebeState().getJobState();

  private JobBatchActivateProcessor processor;
  private JobRecord jobRecord;

  @Before
  public void setUp() {
    jobRecord = new JobRecord();
    processor =
        new JobBatchActivateProcessor(
            jobState,
            zeebeState
                .getZeebeState()
                .getWorkflowState()
                .getElementInstanceState()
                .getVariablesState(),
            zeebeState.getKeyGenerator());
  }

  @Test
  public void stopIteratingAfterAmount() {
    // given
    final String type = "testTask";
    final int expectedIterations = 10;
    final TypedRecord<JobBatchRecord> record =
        newRecord(expectedIterations, type, VarDataEncodingEncoder.lengthMaxValue());
    createJobs(expectedIterations, type);
    createJobs(1, "other-" + type);

    // when
    processor.processRecord(record, responseWriter, streamWriter);

    // then
    verify(jobState, times(expectedIterations)).visitJob(anyLong(), any());
  }

  @Test
  public void stopIteratingOnTruncation() {
    // given
    final String type = "testTask";
    final int expectedIterations = 3;
    final int maxValueLength = expectedIterations * jobRecord.getLength() - 1;
    final TypedRecord<JobBatchRecord> record = newRecord(10, type, maxValueLength);
    createJobs(expectedIterations + 1, type);
    createJobs(1, "other-" + type);

    // when
    processor.processRecord(record, responseWriter, streamWriter);

    // then
    verify(jobState, times(expectedIterations)).visitJob(anyLong(), any());
  }

  private void createJobs(int amount, String type) {
    IntStream.range(0, amount).forEach(i -> createJob(type));
  }

  private void createJob(String type) {
    final JobRecord job = new JobRecord().setType(type);
    final long key = zeebeState.getZeebeState().getKeyGenerator().nextKey();
    jobState.create(key, job);
  }

  private TypedRecord<JobBatchRecord> newRecord(int amount, String type, int maxValueLength) {
    final JobBatchRecord record =
        new JobBatchRecord()
            .setMaxJobsToActivate(amount)
            .setType(type)
            .setWorker("testWorker")
            .setTimeout(Duration.ofSeconds(1).toMillis());
    return new MockTypedRecord<JobBatchRecord>(-1, new RecordMetadata(), record) {
      @Override
      public int getMaxValueLength() {
        return maxValueLength;
      }
    };
  }
}
