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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.zeebe.broker.job.JobSubscriptionManager;
import io.zeebe.broker.job.data.JobRecord;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.topic.StreamProcessorControl;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.util.buffer.BufferUtil;

public class JobInstanceStreamProcessorTest
{

    @Rule
    public StreamProcessorRule rule = new StreamProcessorRule();

    @Mock
    public JobSubscriptionManager subscriptionManager;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        when(subscriptionManager.increaseSubscriptionCreditsAsync(any())).thenReturn(true);
    }

    @Test
    public void shouldCompleteExpiredJob()
    {
        // given
        rule.getClock().pinCurrentTime();

        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeCommand(key, JobIntent.CREATE, job());
        waitForEventWithIntent(JobIntent.CREATED);

        final JobRecord lockedJob = lockedJob(nowPlus(Duration.ofSeconds(30)));
        rule.writeCommand(key, JobIntent.LOCK, lockedJob);
        waitForEventWithIntent(JobIntent.LOCKED);

        rule.writeCommand(key, JobIntent.EXPIRE_LOCK, lockedJob);
        waitForEventWithIntent(JobIntent.LOCK_EXPIRED);

        // when
        rule.writeCommand(key, JobIntent.COMPLETE, lockedJob);

        // then
        waitForEventWithIntent(JobIntent.COMPLETED);

        final List<TypedRecord<JobRecord>> jobEvents = rule.events().onlyJobRecords().collect(Collectors.toList());
        assertThat(jobEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                    tuple(RecordType.COMMAND, JobIntent.CREATE),
                    tuple(RecordType.EVENT, JobIntent.CREATED),
                    tuple(RecordType.COMMAND, JobIntent.LOCK),
                    tuple(RecordType.EVENT, JobIntent.LOCKED),
                    tuple(RecordType.COMMAND, JobIntent.EXPIRE_LOCK),
                    tuple(RecordType.EVENT, JobIntent.LOCK_EXPIRED),
                    tuple(RecordType.COMMAND, JobIntent.COMPLETE),
                    tuple(RecordType.EVENT, JobIntent.COMPLETED));
    }

    @Test
    public void shouldLockOnlyOnce()
    {
        // given
        rule.getClock().pinCurrentTime();

        final long key = 1;
        final StreamProcessorControl control = rule.runStreamProcessor(this::buildStreamProcessor);
        control.blockAfterJobEvent(e -> e.getMetadata().getIntent() == JobIntent.CREATED);

        rule.writeCommand(key, JobIntent.CREATE, job());
        waitForEventWithIntent(JobIntent.CREATED);

        // when
        rule.writeCommand(key, JobIntent.LOCK, lockedJob(nowPlus(Duration.ofSeconds(30))));
        rule.writeCommand(key, JobIntent.LOCK, lockedJob(nowPlus(Duration.ofSeconds(30))));
        control.unblock();

        // then
        waitForRejection(JobIntent.LOCK);

        final List<TypedRecord<JobRecord>> jobEvents = rule.events().onlyJobRecords().collect(Collectors.toList());
        assertThat(jobEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                    tuple(RecordType.COMMAND, JobIntent.CREATE),
                    tuple(RecordType.EVENT, JobIntent.CREATED),
                    tuple(RecordType.COMMAND, JobIntent.LOCK),
                    tuple(RecordType.COMMAND, JobIntent.LOCK),
                    tuple(RecordType.EVENT, JobIntent.LOCKED),
                    tuple(RecordType.COMMAND_REJECTION, JobIntent.LOCK));
    }

    @Test
    public void shouldRejectLockIfJobNotFound()
    {
        // given
        rule.getClock().pinCurrentTime();

        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        // when
        rule.writeCommand(key, JobIntent.LOCK, lockedJob(nowPlus(Duration.ofSeconds(30))));

        // then
        waitForRejection(JobIntent.LOCK);

        final List<TypedRecord<JobRecord>> jobEvents = rule.events().onlyJobRecords().collect(Collectors.toList());
        assertThat(jobEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                    tuple(RecordType.COMMAND, JobIntent.LOCK),
                    tuple(RecordType.COMMAND_REJECTION, JobIntent.LOCK));
    }

    @Test
    public void shouldExpireLockOnlyOnce()
    {
        // given
        final long key = 1;
        final StreamProcessorControl control = rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeCommand(key, JobIntent.CREATE, job());
        waitForEventWithIntent(JobIntent.CREATED);

        control.blockAfterJobEvent(e -> e.getMetadata().getIntent() == JobIntent.LOCKED);

        final JobRecord lockedJob = lockedJob(nowPlus(Duration.ofSeconds(30)));
        rule.writeCommand(key, JobIntent.LOCK, lockedJob);
        waitForEventWithIntent(JobIntent.LOCKED);

        // when
        rule.writeCommand(key, JobIntent.EXPIRE_LOCK, lockedJob);
        rule.writeCommand(key, JobIntent.EXPIRE_LOCK, lockedJob);
        control.unblock();

        // then
        waitForRejection(JobIntent.EXPIRE_LOCK);

        final List<TypedRecord<JobRecord>> jobEvents = rule.events().onlyJobRecords().collect(Collectors.toList());
        assertThat(jobEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                    tuple(RecordType.COMMAND, JobIntent.CREATE),
                    tuple(RecordType.EVENT, JobIntent.CREATED),
                    tuple(RecordType.COMMAND, JobIntent.LOCK),
                    tuple(RecordType.EVENT, JobIntent.LOCKED),
                    tuple(RecordType.COMMAND, JobIntent.EXPIRE_LOCK),
                    tuple(RecordType.COMMAND, JobIntent.EXPIRE_LOCK),
                    tuple(RecordType.EVENT, JobIntent.LOCK_EXPIRED),
                    tuple(RecordType.COMMAND_REJECTION, JobIntent.EXPIRE_LOCK));
    }

    private Instant nowPlus(Duration duration)
    {
        return rule.getClock().getCurrentTime().plus(duration);
    }

    @Test
    public void shouldRejectExpireCommandIfJobCreated()
    {
        // given
        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeCommand(key, JobIntent.CREATE, job());
        waitForEventWithIntent(JobIntent.CREATED);

        // when
        rule.writeCommand(key, JobIntent.EXPIRE_LOCK, job());

        // then
        waitForRejection(JobIntent.EXPIRE_LOCK);

        final List<TypedRecord<JobRecord>> jobEvents = rule.events().onlyJobRecords().collect(Collectors.toList());
        assertThat(jobEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                    tuple(RecordType.COMMAND, JobIntent.CREATE),
                    tuple(RecordType.EVENT, JobIntent.CREATED),
                    tuple(RecordType.COMMAND, JobIntent.EXPIRE_LOCK),
                    tuple(RecordType.COMMAND_REJECTION, JobIntent.EXPIRE_LOCK));
    }

    /**
     *
     */
    @Test
    public void shouldRejectExpireCommandIfJobCompleted()
    {
        // given
        final long key = 1;
        final StreamProcessorControl control = rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeCommand(key, JobIntent.CREATE, job());
        waitForEventWithIntent(JobIntent.CREATED);

        control.blockAfterJobEvent(e -> e.getMetadata().getIntent() == JobIntent.LOCKED);

        final JobRecord lockedJob = lockedJob(nowPlus(Duration.ofSeconds(30)));
        rule.writeCommand(key, JobIntent.LOCK, lockedJob);
        waitForEventWithIntent(JobIntent.LOCKED);

        // when
        rule.writeCommand(key, JobIntent.COMPLETE, lockedJob);
        rule.writeCommand(key, JobIntent.EXPIRE_LOCK, lockedJob);
        control.unblock();

        // then
        waitForRejection(JobIntent.EXPIRE_LOCK);

        final List<TypedRecord<JobRecord>> jobEvents = rule.events().onlyJobRecords().collect(Collectors.toList());
        assertThat(jobEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                    tuple(RecordType.COMMAND, JobIntent.CREATE),
                    tuple(RecordType.EVENT, JobIntent.CREATED),
                    tuple(RecordType.COMMAND, JobIntent.LOCK),
                    tuple(RecordType.EVENT, JobIntent.LOCKED),
                    tuple(RecordType.COMMAND, JobIntent.COMPLETE),
                    tuple(RecordType.COMMAND, JobIntent.EXPIRE_LOCK),
                    tuple(RecordType.EVENT, JobIntent.COMPLETED),
                    tuple(RecordType.COMMAND_REJECTION, JobIntent.EXPIRE_LOCK));
    }


    @Test
    public void shouldRejectExpireCommandIfJobFailed()
    {
        // given
        final long key = 1;
        final StreamProcessorControl control = rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeCommand(key, JobIntent.CREATE, job());
        waitForEventWithIntent(JobIntent.CREATED);

        control.blockAfterJobEvent(e -> e.getMetadata().getIntent() == JobIntent.LOCKED);

        final JobRecord lockedJob = lockedJob(nowPlus(Duration.ofSeconds(30)));
        rule.writeCommand(key, JobIntent.LOCK, lockedJob);
        waitForEventWithIntent(JobIntent.LOCKED);

        // when
        rule.writeCommand(key, JobIntent.FAIL, lockedJob);
        rule.writeCommand(key, JobIntent.EXPIRE_LOCK, lockedJob);
        control.unblock();

        // then
        waitForRejection(JobIntent.EXPIRE_LOCK);

        final List<TypedRecord<JobRecord>> jobEvents = rule.events().onlyJobRecords().collect(Collectors.toList());
        assertThat(jobEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                    tuple(RecordType.COMMAND, JobIntent.CREATE),
                    tuple(RecordType.EVENT, JobIntent.CREATED),
                    tuple(RecordType.COMMAND, JobIntent.LOCK),
                    tuple(RecordType.EVENT, JobIntent.LOCKED),
                    tuple(RecordType.COMMAND, JobIntent.FAIL),
                    tuple(RecordType.COMMAND, JobIntent.EXPIRE_LOCK),
                    tuple(RecordType.EVENT, JobIntent.FAILED),
                    tuple(RecordType.COMMAND_REJECTION, JobIntent.EXPIRE_LOCK));
    }

    @Test
    public void shouldRejectExpireCommandIfJobNotFound()
    {
        // given
        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        // when
        rule.writeCommand(key, JobIntent.EXPIRE_LOCK, lockedJob(nowPlus(Duration.ofSeconds(30))));

        // then
        waitForRejection(JobIntent.EXPIRE_LOCK);

        final List<TypedRecord<JobRecord>> jobEvents = rule.events().onlyJobRecords().collect(Collectors.toList());
        assertThat(jobEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                    tuple(RecordType.COMMAND, JobIntent.EXPIRE_LOCK),
                    tuple(RecordType.COMMAND_REJECTION, JobIntent.EXPIRE_LOCK));
    }

    @Test
    public void shouldCancelCreatedJob()
    {
        // given
        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeCommand(key, JobIntent.CREATE, job());
        waitForEventWithIntent(JobIntent.CREATED);

        // when
        rule.writeCommand(key, JobIntent.CANCEL, job());

        // then
        waitForEventWithIntent(JobIntent.CANCELED);

        final List<TypedRecord<JobRecord>> jobEvents = rule.events().onlyJobRecords().collect(Collectors.toList());
        assertThat(jobEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                tuple(RecordType.COMMAND, JobIntent.CREATE),
                tuple(RecordType.EVENT, JobIntent.CREATED),
                tuple(RecordType.COMMAND, JobIntent.CANCEL),
                tuple(RecordType.EVENT, JobIntent.CANCELED));
    }

    @Test
    public void shouldCancelLockedJob()
    {
        // given
        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeCommand(key, JobIntent.CREATE, job());
        waitForEventWithIntent(JobIntent.CREATED);

        final JobRecord lockedJob = lockedJob(nowPlus(Duration.ofSeconds(30)));
        rule.writeCommand(key, JobIntent.LOCK, lockedJob);
        waitForEventWithIntent(JobIntent.LOCKED);

        // when
        rule.writeCommand(key, JobIntent.CANCEL, lockedJob);

        // then
        waitForEventWithIntent(JobIntent.CANCELED);

        final List<TypedRecord<JobRecord>> jobEvents = rule.events().onlyJobRecords().collect(Collectors.toList());
        assertThat(jobEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                tuple(RecordType.COMMAND, JobIntent.CREATE),
                tuple(RecordType.EVENT, JobIntent.CREATED),
                tuple(RecordType.COMMAND, JobIntent.LOCK),
                tuple(RecordType.EVENT, JobIntent.LOCKED),
                tuple(RecordType.COMMAND, JobIntent.CANCEL),
                tuple(RecordType.EVENT, JobIntent.CANCELED));
    }

    @Test
    public void shouldCancelFailedJob()
    {
        // given
        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeCommand(key, JobIntent.CREATE, job());
        waitForEventWithIntent(JobIntent.CREATED);

        final JobRecord lockedJob = lockedJob(nowPlus(Duration.ofSeconds(30)));
        rule.writeCommand(key, JobIntent.LOCK, lockedJob);
        waitForEventWithIntent(JobIntent.LOCKED);

        rule.writeCommand(key, JobIntent.FAIL, lockedJob);
        waitForEventWithIntent(JobIntent.FAILED);

        // when
        rule.writeCommand(key, JobIntent.CANCEL, lockedJob);

        // then
        waitForEventWithIntent(JobIntent.CANCELED);

        final List<TypedRecord<JobRecord>> jobEvents = rule.events().onlyJobRecords().collect(Collectors.toList());
        assertThat(jobEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                tuple(RecordType.COMMAND, JobIntent.CREATE),
                tuple(RecordType.EVENT, JobIntent.CREATED),
                tuple(RecordType.COMMAND, JobIntent.LOCK),
                tuple(RecordType.EVENT, JobIntent.LOCKED),
                tuple(RecordType.COMMAND, JobIntent.FAIL),
                tuple(RecordType.EVENT, JobIntent.FAILED),
                tuple(RecordType.COMMAND, JobIntent.CANCEL),
                tuple(RecordType.EVENT, JobIntent.CANCELED));
    }


    @Test
    public void shouldRejectCancelIfJobCompleted()
    {
        // given
        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeCommand(key, JobIntent.CREATE, job());
        waitForEventWithIntent(JobIntent.CREATED);

        final JobRecord lockedJob = lockedJob(nowPlus(Duration.ofSeconds(30)));
        rule.writeCommand(key, JobIntent.LOCK, lockedJob);
        waitForEventWithIntent(JobIntent.LOCKED);

        rule.writeCommand(key, JobIntent.COMPLETE, lockedJob);
        waitForEventWithIntent(JobIntent.COMPLETED);

        // when
        rule.writeCommand(key, JobIntent.CANCEL, lockedJob);

        // then
        waitForRejection(JobIntent.CANCEL);

        final List<TypedRecord<JobRecord>> jobEvents = rule.events().onlyJobRecords().collect(Collectors.toList());
        assertThat(jobEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                tuple(RecordType.COMMAND, JobIntent.CREATE),
                tuple(RecordType.EVENT, JobIntent.CREATED),
                tuple(RecordType.COMMAND, JobIntent.LOCK),
                tuple(RecordType.EVENT, JobIntent.LOCKED),
                tuple(RecordType.COMMAND, JobIntent.COMPLETE),
                tuple(RecordType.EVENT, JobIntent.COMPLETED),
                tuple(RecordType.COMMAND, JobIntent.CANCEL),
                tuple(RecordType.COMMAND_REJECTION, JobIntent.CANCEL));
    }

    private void waitForEventWithIntent(Intent intent)
    {
        waitUntil(() -> rule.events().onlyJobRecords().onlyEvents().withIntent(intent).findFirst().isPresent());
    }

    public void waitForRejection(Intent commandIntent)
    {
        waitUntil(() -> rule.events().onlyJobRecords().onlyRejections().withIntent(commandIntent).findFirst().isPresent());
    }

    private JobRecord job()
    {
        final JobRecord event = new JobRecord();

        event.setType(BufferUtil.wrapString("foo"));

        return event;
    }

    private JobRecord lockedJob(Instant lockTime)
    {
        final JobRecord event = new JobRecord();

        event.setType(BufferUtil.wrapString("foo"));
        event.setLockOwner(BufferUtil.wrapString("bar"));
        event.setLockTime(lockTime.toEpochMilli());

        return event;
    }

    private StreamProcessor buildStreamProcessor(TypedStreamEnvironment env)
    {
        return new JobInstanceStreamProcessor(subscriptionManager).createStreamProcessor(env);
    }


}
