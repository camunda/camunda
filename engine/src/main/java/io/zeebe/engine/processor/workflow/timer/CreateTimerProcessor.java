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

import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.SideEffectProducer;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.TimerInstance;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.intent.TimerIntent;
import java.util.function.Consumer;

public class CreateTimerProcessor implements TypedRecordProcessor<TimerRecord> {

  private final DueDateTimerChecker timerChecker;

  private final WorkflowState workflowState;
  private final TimerInstance timerInstance = new TimerInstance();
  private final KeyGenerator keyGenerator;

  public CreateTimerProcessor(final ZeebeState zeebeState, final DueDateTimerChecker timerChecker) {
    this.timerChecker = timerChecker;
    this.workflowState = zeebeState.getWorkflowState();
    this.keyGenerator = zeebeState.getKeyGenerator();
  }

  @Override
  public void processRecord(
      final TypedRecord<TimerRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {

    final TimerRecord timer = record.getValue();

    final long timerKey = keyGenerator.nextKey();

    timerInstance.setElementInstanceKey(timer.getElementInstanceKey());
    timerInstance.setDueDate(timer.getDueDate());
    timerInstance.setKey(timerKey);
    timerInstance.setHandlerNodeId(timer.getHandlerNodeId());
    timerInstance.setRepetitions(timer.getRepetitions());
    timerInstance.setWorkflowKey(timer.getWorkflowKey());
    timerInstance.setWorkflowInstanceKey(timer.getWorkflowInstanceKey());

    sideEffect.accept(this::scheduleTimer);

    streamWriter.appendFollowUpEvent(timerKey, TimerIntent.CREATED, timer);

    workflowState.getTimerState().put(timerInstance);
  }

  private boolean scheduleTimer() {
    timerChecker.scheduleTimer(timerInstance);

    return true;
  }
}
