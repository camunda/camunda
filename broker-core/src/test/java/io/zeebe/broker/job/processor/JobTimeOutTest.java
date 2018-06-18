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

import static io.zeebe.protocol.intent.JobIntent.ACTIVATE;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;
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

public class JobTimeOutTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    @Test
    public void shouldNotTimeOutIfDeadlineNotExceeded() throws InterruptedException
    {
        // given
        brokerRule.getClock().pinCurrentTime();
        final Duration timeout = Duration.ofSeconds(60);
        final String jobType = "jobType";

        createJob(jobType);

        apiRule.openJobSubscription(apiRule.getDefaultPartitionId(), jobType, timeout.toMillis()).await();
        apiRule.subscribedEvents().findFirst().get(); // => job is activated

        // when
        brokerRule.getClock().addTime(timeout.minus(Duration.ofSeconds(1)));

        // then
        assertNoMoreJobsReceived();
    }

    @Test
    public void shouldNotTimeOutIfJobCompleted() throws InterruptedException
    {
        // given
        brokerRule.getClock().pinCurrentTime();
        final Duration timeout = Duration.ofSeconds(60);
        final String jobType = "jobType";

        createJob(jobType);

        apiRule.openJobSubscription(apiRule.getDefaultPartitionId(), jobType, timeout.toMillis()).await();
        final SubscribedRecord activatedJob = apiRule.subscribedEvents().findFirst().get(); // => job is activated

        completeJob(activatedJob);

        // when
        brokerRule.getClock().addTime(timeout.plus(Duration.ofSeconds(1)));

        // then
        assertNoMoreJobsReceived();
    }

    @Test
    public void shouldNotTimeOutIfJobFailed()
    {
        // given
        brokerRule.getClock().pinCurrentTime();
        final Duration timeout = Duration.ofSeconds(60);
        final String jobType = "jobType";

        createJob(jobType);

        apiRule.openJobSubscription(apiRule.getDefaultPartitionId(), jobType, timeout.toMillis()).await();
        final SubscribedRecord activatedJob = apiRule.subscribedEvents().findFirst().get(); // => job is activated

        final Map<String, Object> event = new HashMap<>(activatedJob.value());
        event.put("retries", 0);
        failJob(activatedJob.key(), event);

        // when
        brokerRule.getClock().addTime(timeout.plus(Duration.ofSeconds(1)));

        // then
        assertNoMoreJobsReceived();
    }

    @Test
    public void shouldTimeOutJob()
    {
        // given
        final String jobType = "foo";
        final long jobKey1 = createJob(jobType);

        final long timeout = 1000L;

        apiRule.openJobSubscription(
            apiRule.getDefaultPartitionId(),
            jobType,
            timeout);

        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 1);
        apiRule.moveMessageStreamToTail();

        // when expired
        doRepeatedly(() ->
        {
            brokerRule.getClock().addTime(JobQueueManagerService.TIME_OUT_INTERVAL);
        }).until(v -> apiRule.numSubscribedEventsAvailable() == 1);


        // then activated again
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
                ACTIVATE,
                JobIntent.ACTIVATED,
                JobIntent.TIME_OUT,
                JobIntent.TIMED_OUT,
                ACTIVATE,
                JobIntent.ACTIVATED);
    }

    @Test
    public void shouldSetCorrectSourcePositionAfterJobTimeOut()
    {
        // given
        final String jobType = "foo";
        createJob(jobType);

        final long timeout = 1000L;
        apiRule.openJobSubscription(apiRule.getDefaultPartitionId(), jobType, timeout);

        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 1);
        apiRule.moveMessageStreamToTail();

        // when expired
        doRepeatedly(() ->
        {
            brokerRule.getClock().addTime(JobQueueManagerService.TIME_OUT_INTERVAL);
        }).until(v -> apiRule.numSubscribedEventsAvailable() == 1);

        // then activated again
        final SubscribedRecord jobActiviated = apiRule.subscribedEvents().findAny().get();
        final TestTopicClient topicClient = apiRule.topic();
        final SubscribedRecord firstActivateCommand = topicClient
            .receiveRecords()
            .ofTypeJob()
            .withIntent(ACTIVATE)
            .findFirst()
            .get();
        assertThat(jobActiviated.sourceRecordPosition()).isNotEqualTo(firstActivateCommand.position());

        final SubscribedRecord secondActivateCommand = topicClient
            .receiveRecords()
            .ofTypeJob()
            .withIntent(ACTIVATE)
            .skipUntil(s -> s.position() > firstActivateCommand.position())
            .findFirst()
            .get();

        assertThat(jobActiviated.sourceRecordPosition()).isEqualTo(secondActivateCommand.position());
    }

    @Test
    public void shouldExpireMultipleActivatedJobsAtOnce()
    {
        // given
        final String jobType = "foo";
        final long jobKey1 = createJob(jobType);
        final long jobKey2 = createJob(jobType);

        final long timeout = 1000L;

        apiRule.openJobSubscription(
                apiRule.getDefaultPartitionId(),
                jobType,
                timeout);

        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 2); // both jobs activated
        apiRule.moveMessageStreamToTail();

        // when
        doRepeatedly(() ->
        {
            brokerRule.getClock().addTime(JobQueueManagerService.TIME_OUT_INTERVAL);
        }).until(v -> apiRule.numSubscribedEventsAvailable() == 2);

        // then
        final List<SubscribedRecord> expiredEvents = apiRule.topic()
                                                    .receiveRecords()
                                                    .ofTypeJob()
                                                    .limit(16)
                                                    .collect(Collectors.toList());

        assertThat(expiredEvents)
            .filteredOn(e -> e.intent() == JobIntent.ACTIVATED)
            .hasSize(4)
            .extracting(e -> e.key()).containsExactly(jobKey1, jobKey2, jobKey1, jobKey2);

        assertThat(expiredEvents)
            .filteredOn(e -> e.intent() == JobIntent.TIMED_OUT)
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

    private void completeJob(SubscribedRecord activatedJob)
    {
        apiRule.createCmdRequest()
            .type(ValueType.JOB, JobIntent.COMPLETE)
            .key(activatedJob.key())
            .command()
                .putAll(activatedJob.value())
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
