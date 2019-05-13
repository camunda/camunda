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
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.JobIntent;

public class CancelProcessor implements CommandProcessor<JobRecord> {

  public static final String NO_JOB_FOUND_MESSAGE =
      "Expected to cancel job with key '%d', but no such job was found";
  private final JobState state;

  public CancelProcessor(JobState state) {
    this.state = state;
  }

  @Override
  public void onCommand(TypedRecord<JobRecord> command, CommandControl<JobRecord> commandControl) {
    final long jobKey = command.getKey();
    final JobRecord job = command.getValue();

    if (state.exists(jobKey)) {
      state.delete(jobKey, job);
      commandControl.accept(JobIntent.CANCELED, job);
    } else {
      commandControl.reject(RejectionType.NOT_FOUND, String.format(NO_JOB_FOUND_MESSAGE, jobKey));
    }
  }
}
