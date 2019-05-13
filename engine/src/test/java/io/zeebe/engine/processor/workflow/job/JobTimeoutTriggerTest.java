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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.zeebe.engine.processor.TypedStreamEnvironment;
import io.zeebe.engine.processor.TypedStreamProcessor;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.instance.JobState;
import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.util.sched.ActorControl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;

public class JobTimeoutTriggerTest {

  @Rule public final ZeebeStateRule stateRule = new ZeebeStateRule();

  @Mock private ActorControl someActor;

  @Mock private TypedStreamWriter typedStreamWriter;
  private JobTimeoutTrigger jobTimeoutTrigger;

  @Before
  public void setUp() {
    initMocks(this);

    final JobState jobState = stateRule.getZeebeState().getJobState();
    jobTimeoutTrigger = new JobTimeoutTrigger(jobState);

    final TypedStreamProcessor streamProcessor = mock(TypedStreamProcessor.class);
    when(streamProcessor.getActor()).thenReturn(someActor);
    final TypedStreamEnvironment environment = mock(TypedStreamEnvironment.class);
    when(environment.buildCommandWriter()).thenReturn(typedStreamWriter);
    when(streamProcessor.getEnvironment()).thenReturn(environment);

    jobTimeoutTrigger.onRecovered(streamProcessor);

    jobState.activate(0, newJobRecord());
    jobState.activate(1, newJobRecord());
    jobState.activate(2, newJobRecord());
  }

  private JobRecord newJobRecord() {
    final JobRecord jobRecord = new JobRecord();

    jobRecord.setRetries(2);
    jobRecord.setDeadline(256L);
    jobRecord.setType("test");

    return jobRecord;
  }

  @Test
  public void shouldNotWriteAgainAfterFlushFailed() {
    // given
    when(typedStreamWriter.flush()).thenReturn(1L, -1L);

    // when
    jobTimeoutTrigger.deactivateTimedOutJobs();

    // then
    final InOrder inOrder = Mockito.inOrder(typedStreamWriter);

    inOrder
        .verify(typedStreamWriter)
        .appendFollowUpCommand(eq(0L), eq(JobIntent.TIME_OUT), any(JobRecord.class), any());
    inOrder.verify(typedStreamWriter).flush();
    inOrder
        .verify(typedStreamWriter)
        .appendFollowUpCommand(eq(1L), eq(JobIntent.TIME_OUT), any(JobRecord.class), any());
    inOrder.verify(typedStreamWriter).flush();
    inOrder.verify(typedStreamWriter).reset();
    inOrder.verifyNoMoreInteractions();
  }
}
