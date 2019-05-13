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
package io.zeebe.engine.processor.workflow.timer;

import io.zeebe.engine.processor.StreamProcessorLifecycleAware;
import io.zeebe.engine.processor.TypedStreamEnvironment;
import io.zeebe.engine.processor.TypedStreamProcessor;
import io.zeebe.engine.processor.TypedStreamWriterImpl;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.TimerInstance;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.ScheduledTimer;
import io.zeebe.util.sched.clock.ActorClock;
import java.time.Duration;

public class DueDateTimerChecker implements StreamProcessorLifecycleAware {

  private static final long TIMER_RESOLUTION = Duration.ofMillis(100).toMillis();

  private final TimerRecord timerRecord = new TimerRecord();

  private final WorkflowState workflowState;
  private ActorControl actor;
  private TypedStreamWriterImpl streamWriter;

  private ScheduledTimer scheduledTimer;
  private long nextDueDate = -1L;

  public DueDateTimerChecker(final WorkflowState workflowState) {
    this.workflowState = workflowState;
  }

  public void scheduleTimer(final TimerInstance timer) {

    // We schedule only one runnable for all timers.
    // - The runnable is scheduled when the first timer is scheduled.
    // - If a new timer is scheduled which should be triggered before the current runnable is
    // executed then the runnable is canceled and re-scheduled with the new duration.
    // - Otherwise, we don't need to cancel the runnable. It will be rescheduled when it is
    // executed.

    final Duration duration =
        Duration.ofMillis(timer.getDueDate() - ActorClock.currentTimeMillis());

    if (scheduledTimer == null) {
      scheduledTimer = actor.runDelayed(duration, this::triggerTimers);
      nextDueDate = timer.getDueDate();

    } else if (nextDueDate - timer.getDueDate() > TIMER_RESOLUTION) {
      scheduledTimer.cancel();

      scheduledTimer = actor.runDelayed(duration, this::triggerTimers);
      nextDueDate = timer.getDueDate();
    }
  }

  private void triggerTimers() {
    nextDueDate =
        workflowState
            .getTimerState()
            .findTimersWithDueDateBefore(ActorClock.currentTimeMillis(), this::triggerTimer);

    // reschedule the runnable if there are timers left

    if (nextDueDate > 0) {
      final Duration duration = Duration.ofMillis(nextDueDate - ActorClock.currentTimeMillis());
      scheduledTimer = actor.runDelayed(duration, this::triggerTimers);

    } else {
      scheduledTimer = null;
    }
  }

  private boolean triggerTimer(TimerInstance timer) {
    timerRecord.reset();
    timerRecord
        .setElementInstanceKey(timer.getElementInstanceKey())
        .setWorkflowInstanceKey(timer.getWorkflowInstanceKey())
        .setDueDate(timer.getDueDate())
        .setHandlerNodeId(timer.getHandlerNodeId())
        .setRepetitions(timer.getRepetitions())
        .setWorkflowKey(timer.getWorkflowKey());

    streamWriter.appendFollowUpCommand(timer.getKey(), TimerIntent.TRIGGER, timerRecord);

    return streamWriter.flush() > 0;
  }

  @Override
  public void onOpen(final TypedStreamProcessor streamProcessor) {
    this.actor = streamProcessor.getActor();

    final TypedStreamEnvironment env = streamProcessor.getEnvironment();
    streamWriter = new TypedStreamWriterImpl(env.getStream(), env.getEventRegistry());
  }

  @Override
  public void onRecovered(final TypedStreamProcessor streamProcessor) {
    // check if timers are due after restart
    triggerTimers();
  }
}
