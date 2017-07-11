/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.util.actor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ActorSchedulerBuilderTest
{
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void shouldBuildSingleThreadSchedulerForSingleThread()
    {
        final ActorScheduler scheduler = ActorSchedulerBuilder.createDefaultScheduler("test");

        assertThat(scheduler).isInstanceOf(SingleThreadActorScheduler.class);
    }

    @Test
    public void shouldBuildDynamicThreadSchedulerForMultipleThreads()
    {
        final ActorScheduler scheduler = ActorSchedulerBuilder.createDefaultScheduler("test", 2);

        assertThat(scheduler).isInstanceOf(DynamicActorSchedulerImpl.class);
    }

    @Test
    public void shouldVerifyName()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("name must not be null");

        new ActorSchedulerBuilder()
            .name(null)
            .build();
    }

    @Test
    public void shouldVerifyThreadCount()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("thread count must be greater than 0");

        new ActorSchedulerBuilder()
            .threadCount(0)
            .build();
    }

    @Test
    public void shouldVerifyBaseIterationsPerActor()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("base iterations per actor must be greater than 0");

        new ActorSchedulerBuilder()
            .baseIterationsPerActor(0)
            .build();
    }

    @Test
    public void shouldVerifyDurationSampleCount()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("duration sample count must be greater than 0");

        new ActorSchedulerBuilder()
            .durationSampleCount(0)
            .build();
    }

    @Test
    public void shouldVerifyDurationSamplePeriodNotNull()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("duration sample period must not be null");

        new ActorSchedulerBuilder()
            .durationSamplePeriod(null)
            .build();
    }

    @Test
    public void shouldVerifyDurationSamplePeriodGreterThanZero()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("duration sample period must be greater than PT0S");

        new ActorSchedulerBuilder()
            .durationSamplePeriod(Duration.ofNanos(0))
            .build();
    }

    @Test
    public void shouldVerifyImbalanceThresholdLessThanOne()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("imbalance threshold must be less than or equal to 1.0");

        new ActorSchedulerBuilder()
            .imbalanceThreshold(1.5)
            .build();
    }

    @Test
    public void shouldVerifyImbalanceThresholdGreaterThanZero()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("imbalance threshold must be greater than or equal to 0.0");

        new ActorSchedulerBuilder()
            .imbalanceThreshold(-0.5)
            .build();
    }

    @Test
    public void shouldVerifyIdleStrategy()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("runner idle strategy must not be null");

        new ActorSchedulerBuilder()
            .runnerIdleStrategy(null)
            .build();
    }

    @Test
    public void shouldVerifyErrorHandler()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("runner error handler must not be null");

        new ActorSchedulerBuilder()
            .runnerErrorHander(null)
            .build();
    }

    @Test
    public void shouldVerifySchedulerInitialBackoffNotNull()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("scheduler initial backoff must not be null");

        new ActorSchedulerBuilder()
            .schedulerInitialBackoff(null)
            .build();
    }

    @Test
    public void shouldVerifySchedulerInitialBackoffGreterThanZero()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("scheduler initial backoff must be greater than PT0S");

        new ActorSchedulerBuilder()
            .schedulerInitialBackoff(Duration.ofNanos(0))
            .build();
    }

    @Test
    public void shouldVerifySchedulerMaxBackoffNotNull()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("scheduler max backoff must not be null");

        new ActorSchedulerBuilder()
            .schedulerMaxBackoff(null)
            .build();
    }

    @Test
    public void shouldVerifySchedulerMaxBackoffGreterThanInitialBackoff()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("scheduler max backoff must be greater than PT10S");

        new ActorSchedulerBuilder()
            .schedulerInitialBackoff(Duration.ofSeconds(10))
            .schedulerMaxBackoff(Duration.ofNanos(5))
            .build();
    }
}
