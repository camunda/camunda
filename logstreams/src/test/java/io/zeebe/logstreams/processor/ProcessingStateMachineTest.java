/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.processor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbTransaction;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.concurrent.CountDownLatch;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;

public class ProcessingStateMachineTest {

  @Rule public ControlledActorSchedulerRule actorSchedulerRule = new ControlledActorSchedulerRule();

  private ProcessingStateMachine processingStateMachine;

  @Mock private StreamProcessor streamProcessor;
  @Mock private LogStreamReader logStreamReader;
  @Mock private LogStreamRecordWriter logStreamWriter;
  @Mock private ZeebeDb zeebeDb;

  private ZeebeDbTransaction zeebeDbTransaction;
  private ActorControl actor;
  private EventProcessor eventProcessor;

  @Before
  public void setup() {
    initMocks(this);

    final ControllableActor controllableActor = new ControllableActor();
    actor = controllableActor.getActor();

    when(logStreamReader.hasNext()).thenReturn(true, false);
    when(logStreamReader.next()).thenReturn(mock(LoggedEvent.class));

    when(logStreamWriter.producerId(anyInt())).thenReturn(logStreamWriter);

    zeebeDbTransaction = mock(ZeebeDbTransaction.class);
    when(zeebeDb.transaction()).thenReturn(zeebeDbTransaction);

    eventProcessor = mock(EventProcessor.class);

    when(eventProcessor.executeSideEffects()).thenReturn(true);
    when(eventProcessor.writeEvent(any())).thenReturn(1L);

    when(streamProcessor.onEvent(any())).thenReturn(eventProcessor);

    final StreamProcessorContext streamProcessorContext = new StreamProcessorContext();
    streamProcessorContext.setActorControl(actor);
    streamProcessorContext.setLogStreamReader(logStreamReader);
    streamProcessorContext.setLogStreamWriter(logStreamWriter);
    streamProcessorContext.setName("testProcessor");

    processingStateMachine =
        ProcessingStateMachine.builder()
            .setStreamProcessorContext(streamProcessorContext)
            .setMetrics(mock(StreamProcessorMetrics.class))
            .setStreamProcessor(streamProcessor)
            .setZeebeDb(zeebeDb)
            .setShouldProcessNext(() -> true)
            .setAbortCondition(() -> false)
            .build();

    actorSchedulerRule.submitActor(controllableActor);
  }

  @Test
  public void shouldRunLifecycle() throws Exception {
    // given
    final CountDownLatch latch = new CountDownLatch(1);
    when(eventProcessor.executeSideEffects())
        .then(
            (invocationOnMock -> {
              latch.countDown();
              return true;
            }));

    // when
    actor.call(() -> processingStateMachine.readNextEvent());
    actorSchedulerRule.workUntilDone();

    // then
    latch.await();
    final InOrder inOrder =
        Mockito.inOrder(streamProcessor, eventProcessor, zeebeDb, zeebeDbTransaction);

    // process
    inOrder.verify(streamProcessor, times(1)).onEvent(any());
    inOrder.verify(zeebeDb, times(1)).transaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());

    // write event
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());

    // update state
    inOrder.verify(zeebeDbTransaction, times(1)).commit();

    // execute side effects
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldRunLifecycleOnErrorInProcess() throws Exception {
    // given
    doThrow(new RuntimeException("expected")).doNothing().when(zeebeDbTransaction).run(any());
    final CountDownLatch latch = new CountDownLatch(1);
    when(eventProcessor.executeSideEffects())
        .then(
            (invocationOnMock -> {
              latch.countDown();
              return true;
            }));

    // when
    actor.call(() -> processingStateMachine.readNextEvent());
    actorSchedulerRule.workUntilDone();

    // then
    latch.await();
    final InOrder inOrder =
        Mockito.inOrder(streamProcessor, eventProcessor, zeebeDb, zeebeDbTransaction);

    // process
    inOrder.verify(streamProcessor, times(1)).onEvent(any());
    inOrder.verify(zeebeDb, times(1)).transaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());

    // on error
    inOrder.verify(zeebeDbTransaction, times(1)).rollback();
    inOrder.verify(zeebeDb, times(1)).transaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());

    // write event
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());

    // update state
    inOrder.verify(zeebeDbTransaction, times(1)).commit();

    // execute side effects
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldRunLifecycleOnErrorInWriteEvent() throws Exception {
    // given
    doThrow(new RuntimeException("expected")).doReturn(1L).when(eventProcessor).writeEvent(any());
    final CountDownLatch latch = new CountDownLatch(1);
    when(eventProcessor.executeSideEffects())
        .then(
            (invocationOnMock -> {
              latch.countDown();
              return true;
            }));

    // when
    actor.call(() -> processingStateMachine.readNextEvent());
    actorSchedulerRule.workUntilDone();

    // then
    latch.await();
    final InOrder inOrder =
        Mockito.inOrder(streamProcessor, eventProcessor, zeebeDb, zeebeDbTransaction);

    // process
    inOrder.verify(streamProcessor, times(1)).onEvent(any());
    inOrder.verify(zeebeDb, times(1)).transaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());

    // write event
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());

    // on error
    inOrder.verify(zeebeDbTransaction, times(1)).rollback();
    inOrder.verify(zeebeDb, times(1)).transaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());

    // write event
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());

    // update state
    inOrder.verify(zeebeDbTransaction, times(1)).commit();

    // execute side effects
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldRunLifecycleOnErrorInUpdateState() throws Exception {
    // given
    doThrow(new RuntimeException("expected")).doNothing().when(zeebeDbTransaction).commit();
    final CountDownLatch latch = new CountDownLatch(1);
    when(eventProcessor.executeSideEffects())
        .then(
            (invocationOnMock -> {
              latch.countDown();
              return true;
            }));

    // when
    actor.call(() -> processingStateMachine.readNextEvent());
    actorSchedulerRule.workUntilDone();

    // then
    latch.await();
    final InOrder inOrder =
        Mockito.inOrder(streamProcessor, eventProcessor, zeebeDb, zeebeDbTransaction);

    // process
    inOrder.verify(streamProcessor, times(1)).onEvent(any());
    inOrder.verify(zeebeDb, times(1)).transaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());

    // write event
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());

    // update state
    inOrder.verify(zeebeDbTransaction, times(1)).commit();

    // on error
    inOrder.verify(zeebeDbTransaction, times(1)).rollback();
    inOrder.verify(zeebeDb, times(1)).transaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());

    // update state
    inOrder.verify(zeebeDbTransaction, times(1)).commit();

    // execute side effects
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldRunLifecycleOnErrorInExecuteSideEffects() throws Exception {
    // given
    final CountDownLatch latch = new CountDownLatch(1);
    when(eventProcessor.executeSideEffects())
        .then(
            (invocationOnMock -> {
              latch.countDown();
              throw new RuntimeException("expected");
            }));

    // when
    actor.call(() -> processingStateMachine.readNextEvent());
    actorSchedulerRule.workUntilDone();

    // then
    latch.await();
    final InOrder inOrder =
        Mockito.inOrder(streamProcessor, eventProcessor, zeebeDb, zeebeDbTransaction);

    // process
    inOrder.verify(streamProcessor, times(1)).onEvent(any());
    inOrder.verify(zeebeDb, times(1)).transaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());

    // write event
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());

    // update state
    inOrder.verify(zeebeDbTransaction, times(1)).commit();

    // execute side effects
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  private class ControllableActor extends Actor {

    public ActorControl getActor() {
      return actor;
    }
  }
}
