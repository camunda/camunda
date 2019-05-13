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

import io.zeebe.engine.processor.CommandProcessor;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.state.instance.JobState;
import io.zeebe.engine.state.instance.JobState.State;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.JobIntent;

public class FailProcessor implements CommandProcessor<JobRecord> {
  public static final String NOT_ACTIVATED_JOB_MESSAGE =
      "Expected to fail activated job with key '%d', but it %s";
  private final JobState state;

  public FailProcessor(JobState state) {
    this.state = state;
  }

  @Override
  public void onCommand(TypedRecord<JobRecord> command, CommandControl<JobRecord> commandControl) {
    final long key = command.getKey();
    final JobState.State jobState = state.getState(key);

    if (jobState == State.ACTIVATED) {
      final JobRecord failedJob = state.getJob(key);
      failedJob.setRetries(command.getValue().getRetries());
      failedJob.setErrorMessage(command.getValue().getErrorMessage());
      state.fail(key, failedJob);

      commandControl.accept(JobIntent.FAILED, failedJob);
    } else if (jobState == State.ACTIVATABLE) {
      commandControl.reject(
          RejectionType.INVALID_STATE,
          String.format(NOT_ACTIVATED_JOB_MESSAGE, key, "must be activated first"));
    } else if (jobState == State.FAILED) {
      commandControl.reject(
          RejectionType.INVALID_STATE,
          String.format(NOT_ACTIVATED_JOB_MESSAGE, key, "is marked as failed"));
    } else {
      commandControl.reject(
          RejectionType.NOT_FOUND, String.format(NOT_ACTIVATED_JOB_MESSAGE, key, "does not exist"));
    }
  }
}
