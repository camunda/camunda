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

import io.zeebe.engine.processor.TypedEventStreamProcessorBuilder;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.JobState;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobBatchIntent;
import io.zeebe.protocol.intent.JobIntent;

public class JobEventProcessors {
  public static void addJobProcessors(
      TypedEventStreamProcessorBuilder typedEventStreamProcessorBuilder, ZeebeState zeebeState) {
    final WorkflowState workflowState = zeebeState.getWorkflowState();
    final JobState jobState = zeebeState.getJobState();

    typedEventStreamProcessorBuilder
        .onEvent(ValueType.JOB, JobIntent.CREATED, new JobCreatedProcessor(workflowState))
        .onEvent(ValueType.JOB, JobIntent.COMPLETED, new JobCompletedEventProcessor(workflowState))
        .onCommand(ValueType.JOB, JobIntent.CREATE, new CreateProcessor(jobState))
        .onCommand(ValueType.JOB, JobIntent.COMPLETE, new CompleteProcessor(jobState))
        .onCommand(ValueType.JOB, JobIntent.FAIL, new FailProcessor(jobState))
        .onEvent(ValueType.JOB, JobIntent.FAILED, new JobFailedProcessor())
        .onCommand(ValueType.JOB, JobIntent.TIME_OUT, new TimeOutProcessor(jobState))
        .onCommand(ValueType.JOB, JobIntent.UPDATE_RETRIES, new UpdateRetriesProcessor(jobState))
        .onCommand(ValueType.JOB, JobIntent.CANCEL, new CancelProcessor(jobState))
        .onCommand(
            ValueType.JOB_BATCH,
            JobBatchIntent.ACTIVATE,
            new JobBatchActivateProcessor(
                jobState,
                workflowState.getElementInstanceState().getVariablesState(),
                zeebeState.getKeyGenerator()))
        .withListener(new JobTimeoutTrigger(jobState));
  }
}
