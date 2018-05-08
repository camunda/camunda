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

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.job.JobQueueManagerService;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;

public class JobLockExpirationTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    @Test
    public void shouldNotExpireLockIfLockNotExceeded() throws InterruptedException
    {
        // given
        brokerRule.getClock().pinCurrentTime();
        final Duration lockTime = Duration.ofSeconds(60);
        final String jobType = "jobType";

        createJob(jobType);

        apiRule.openJobSubscription(apiRule.getDefaultPartitionId(), jobType, lockTime.toMillis()).await();
        apiRule.subscribedEvents().findFirst().get(); // => job is locked

        // when
        brokerRule.getClock().addTime(lockTime.minus(Duration.ofSeconds(1)));

        // then
        assertNoMoreJobsReceived();
    }

    @Test
    public void shouldNotExpireLockIfJobCompleted() throws InterruptedException
    {
        // given
        brokerRule.getClock().pinCurrentTime();
        final Duration lockTime = Duration.ofSeconds(60);
        final String jobType = "jobType";

        createJob(jobType);

        apiRule.openJobSubscription(apiRule.getDefaultPartitionId(), jobType, lockTime.toMillis()).await();
        final SubscribedRecord lockedJob = apiRule.subscribedEvents().findFirst().get(); // => job is locked

        completeJob(lockedJob);

        // when
        brokerRule.getClock().addTime(lockTime.plus(Duration.ofSeconds(1)));

        // then
        assertNoMoreJobsReceived();
    }

    @Test
    public void shouldNotExpireLockIfJobFailed()
    {
        // given
        brokerRule.getClock().pinCurrentTime();
        final Duration lockTime = Duration.ofSeconds(60);
        final String jobType = "jobType";

        createJob(jobType);

        apiRule.openJobSubscription(apiRule.getDefaultPartitionId(), jobType, lockTime.toMillis()).await();
        final SubscribedRecord lockedJob = apiRule.subscribedEvents().findFirst().get(); // => job is locked

        final Map<String, Object> event = new HashMap<>(lockedJob.value());
        event.put("retries", 0);
        failJob(lockedJob.key(), event);

        // when
        brokerRule.getClock().addTime(lockTime.plus(Duration.ofSeconds(1)));

        // then
        assertNoMoreJobsReceived();
    }

    @Test
    public void shouldExpireLockedJob()
    {
        // given
        final String jobType = "foo";
        final long jobKey1 = createJob(jobType);

        final long lockTime = 1000L;

        apiRule.openJobSubscription(
            apiRule.getDefaultPartitionId(),
            jobType,
            lockTime);

        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 1);
        apiRule.moveMessageStreamToTail();

        // when expired
        doRepeatedly(() ->
        {
            brokerRule.getClock().addTime(JobQueueManagerService.LOCK_EXPIRATION_INTERVAL);
        }).until(v -> apiRule.numSubscribedEventsAvailable() == 1);


        // then locked again
        final List<SubscribedRecord> events = apiRule.topic()
             .receiveRecords()
             .ofTypeJob()
             .limit(8)
             .collect(Collectors.toList());

        assertThat(events).extracting(e -> e.key()).contains(jobKey1);
        assertThat(events).extracting(e -> e.intent())
            .containsExactly(
                JobIntent.CREATE,
                JobIntent.CREATED,
                JobIntent.LOCK,
                JobIntent.LOCKED,
                JobIntent.EXPIRE_LOCK,
                JobIntent.LOCK_EXPIRED,
                JobIntent.LOCK,
                JobIntent.LOCKED);
    }

    @Test
    public void shouldExpireMultipleLockedJobsAtOnce()
    {
        // given
        final String jobType = "foo";
        final long jobKey1 = createJob(jobType);
        final long jobKey2 = createJob(jobType);

        final long lockTime = 1000L;

        apiRule.openJobSubscription(
                apiRule.getDefaultPartitionId(),
                jobType,
                lockTime);

        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 2); // both jobs locked
        apiRule.moveMessageStreamToTail();

        // when
        doRepeatedly(() ->
        {
            brokerRule.getClock().addTime(JobQueueManagerService.LOCK_EXPIRATION_INTERVAL);
        }).until(v -> apiRule.numSubscribedEventsAvailable() == 2);

        // then
        final List<SubscribedRecord> expiredEvents = apiRule.topic()
                                                    .receiveRecords()
                                                    .ofTypeJob()
                                                    .limit(16)
                                                    .collect(Collectors.toList());

        assertThat(expiredEvents)
            .filteredOn(e -> e.intent() == JobIntent.LOCKED)
            .hasSize(4)
            .extracting(e -> e.key()).containsExactly(jobKey1, jobKey2, jobKey1, jobKey2);

        assertThat(expiredEvents)
            .filteredOn(e -> e.intent() == JobIntent.LOCK_EXPIRED)
            .extracting(e -> e.key()).containsExactlyInAnyOrder(jobKey1, jobKey2);
    }

    private long createJob(String type)
    {
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
            .type(ValueType.JOB, JobIntent.CREATE)
            .command()
                .put("type", type)
                .put("retries", 3)
                .done()
            .sendAndAwait();

        return resp.key();
    }

    private void completeJob(SubscribedRecord lockedJob)
    {
        apiRule.createCmdRequest()
            .type(ValueType.JOB, JobIntent.COMPLETE)
            .key(lockedJob.key())
            .command()
                .putAll(lockedJob.value())
                .done()
            .sendAndAwait();
    }

    private void failJob(long key, Map<String, Object> event)
    {
        apiRule.createCmdRequest()
            .type(ValueType.JOB, JobIntent.FAIL)
            .key(key)
            .command()
                .putAll(event)
                .done()
            .sendAndAwait();
    }

    private void assertNoMoreJobsReceived()
    {
        try
        {
            Thread.sleep(1000L);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }

        assertThat(apiRule.numSubscribedEventsAvailable()).isEqualTo(0);
    }

}
