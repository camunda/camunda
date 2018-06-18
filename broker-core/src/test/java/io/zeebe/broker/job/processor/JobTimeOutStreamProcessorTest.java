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
package io.zeebe.broker.job.processor;

import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.job.JobQueueManagerService;
import io.zeebe.broker.job.data.JobRecord;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.topic.StreamProcessorControl;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.clock.ControlledActorClock;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

public class JobTimeOutStreamProcessorTest {
  @Rule public StreamProcessorRule rule = new StreamProcessorRule();

  private JobRecord job() {
    final JobRecord event = new JobRecord();

    event.setType(BufferUtil.wrapString("foo"));

    return event;
  }

  @Test
  public void shouldTimeOutIfAfterDeadlineForTwoJobs() {
    // given
    final ControlledActorClock clock = rule.getClock();
    clock.pinCurrentTime();

    final JobRecord job = job().setDeadline(clock.getCurrentTimeInMillis() + 100);
    rule.writeEvent(1, JobIntent.ACTIVATED, job);
    final long position = rule.writeEvent(2, JobIntent.ACTIVATED, job);

    final StreamProcessorControl streamProcessorControl =
        rule.initStreamProcessor(e -> new JobTimeOutStreamProcessor().createStreamProcessor(e));
    streamProcessorControl.blockAfterEvent(e -> e.getPosition() == position);
    streamProcessorControl.start();

    // wait til stream processor has processed second event
    waitUntil(streamProcessorControl::isBlocked);

    // when
    clock.addTime(JobQueueManagerService.TIME_OUT_INTERVAL.plus(Duration.ofSeconds(1)));

    // then
    final List<TypedRecord<JobRecord>> expirationEvents =
        doRepeatedly(
                () ->
                    rule.events()
                        .onlyJobRecords()
                        .withIntent(JobIntent.TIME_OUT)
                        .collect(Collectors.toList()))
            .until(l -> l.size() == 2);

    assertThat(expirationEvents).extracting("key").containsExactlyInAnyOrder(1L, 2L);
  }
}
