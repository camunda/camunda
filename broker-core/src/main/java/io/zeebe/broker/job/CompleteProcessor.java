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
package io.zeebe.broker.job;

import io.zeebe.broker.job.JobState.State;
import io.zeebe.broker.logstreams.processor.CommandProcessor;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.JobIntent;

public class CompleteProcessor implements CommandProcessor<JobRecord> {
  private final JobState state;

  public CompleteProcessor(JobState state) {
    this.state = state;
  }

  @Override
  public void onCommand(TypedRecord<JobRecord> command, CommandControl<JobRecord> commandControl) {

    final long jobKey = command.getKey();

    if (state.exists(jobKey)) {
      if (!state.isInState(jobKey, State.FAILED)) {
        final JobRecord job = state.getJob(jobKey);
        job.setPayload(command.getValue().getPayload());

        state.delete(jobKey, job);
        commandControl.accept(JobIntent.COMPLETED, job);
      } else {
        commandControl.reject(
            RejectionType.NOT_APPLICABLE, "Job is failed and must be resolved first");
      }
    } else {
      commandControl.reject(RejectionType.NOT_APPLICABLE, "Job does not exist");
    }
  }
}
