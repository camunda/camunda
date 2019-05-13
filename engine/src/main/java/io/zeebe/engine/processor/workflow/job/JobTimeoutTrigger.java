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

import static io.zeebe.util.sched.clock.ActorClock.currentTimeMillis;

import io.zeebe.engine.processor.StreamProcessorLifecycleAware;
import io.zeebe.engine.processor.TypedCommandWriter;
import io.zeebe.engine.processor.TypedStreamProcessor;
import io.zeebe.engine.state.instance.JobState;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.util.sched.ScheduledTimer;
import java.time.Duration;

public class JobTimeoutTrigger implements StreamProcessorLifecycleAware {
  public static final Duration TIME_OUT_POLLING_INTERVAL = Duration.ofSeconds(30);
  private final JobState state;

  private ScheduledTimer timer;
  private TypedCommandWriter writer;

  public JobTimeoutTrigger(final JobState state) {
    this.state = state;
  }

  @Override
  public void onRecovered(final TypedStreamProcessor streamProcessor) {
    timer =
        streamProcessor
            .getActor()
            .runAtFixedRate(TIME_OUT_POLLING_INTERVAL, this::deactivateTimedOutJobs);
    writer = streamProcessor.getEnvironment().buildCommandWriter();
  }

  @Override
  public void onClose() {
    if (timer != null) {
      timer.cancel();
      timer = null;
    }
  }

  void deactivateTimedOutJobs() {
    final long now = currentTimeMillis();
    state.forEachTimedOutEntry(
        now,
        (key, record) -> {
          writer.appendFollowUpCommand(
              key, JobIntent.TIME_OUT, record, (m) -> m.valueType(ValueType.JOB));

          final boolean flushed = writer.flush() >= 0;
          if (!flushed) {
            writer.reset();
          }
          return flushed;
        });
  }
}
