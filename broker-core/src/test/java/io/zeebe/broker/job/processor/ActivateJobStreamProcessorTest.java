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

import static io.zeebe.test.util.TestUtil.waitUntil;

import io.zeebe.broker.job.data.JobRecord;
import io.zeebe.broker.topic.StreamProcessorControl;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.junit.Rule;
import org.junit.Test;

public class ActivateJobStreamProcessorTest {

  @Rule public StreamProcessorRule rule = new StreamProcessorRule();

  /**
   * https://github.com/zeebe-io/zeebe/issues/926
   *
   * @throws InterruptedException
   */
  @Test
  public void shouldReceiveJobsWhenActivationStreamProcessorDoesReprocessing()
      throws InterruptedException {
    // given
    final DirectBuffer jobType = BufferUtil.wrapString("foo");

    final int numJobs = 999;
    for (int i = 0; i < numJobs; i++) {
      rule.writeEvent(JobIntent.CREATED, jobOfType(jobType));
    }

    final ActivateJobStreamProcessor processor1 = new ActivateJobStreamProcessor(jobType);
    final StreamProcessorControl streamProcessorControl =
        rule.runStreamProcessor(processor1::createStreamProcessor);

    final JobSubscription subscription = newSubscription(jobType);
    subscription.setCredits(numJobs);
    subscription.setSubscriberKey(0);
    processor1.addSubscription(subscription);

    // processor has processed entire log
    waitUntil(
        () -> rule.events().onlyJobRecords().withIntent(JobIntent.ACTIVATE).count() == numJobs);

    streamProcessorControl.close();

    // when restarting the stream processor so that it does reprocessing
    streamProcessorControl.purgeSnapshot();

    final long lastCreated = rule.writeEvent(JobIntent.CREATED, jobOfType(jobType));
    streamProcessorControl.blockAfterJobEvent(r -> r.getPosition() == lastCreated);

    streamProcessorControl.start();

    // and adding a subscription concurrently
    final JobSubscription subscription2 = newSubscription(jobType);
    subscription2.setCredits(numJobs + 1);
    subscription2.setSubscriberKey(1);
    processor1.addSubscription(subscription2);

    // then it should not activate the jobs a second time, but only activate the additional job
    waitUntil(
        () -> rule.events().onlyJobRecords().withIntent(JobIntent.ACTIVATE).count() == numJobs + 1);
  }

  private JobSubscription newSubscription(final DirectBuffer jobType) {
    return new JobSubscription(0, jobType, 1000L, BufferUtil.wrapString("foo"), 0);
  }

  private static JobRecord jobOfType(DirectBuffer type) {
    final JobRecord jobRecord = new JobRecord();
    jobRecord.setType(type);
    jobRecord.setRetries(3);
    return jobRecord;
  }
}
