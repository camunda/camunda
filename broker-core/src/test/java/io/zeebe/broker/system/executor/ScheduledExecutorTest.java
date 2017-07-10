/**
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
package io.zeebe.broker.system.executor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import io.zeebe.test.util.agent.ControllableTaskScheduler;
import io.zeebe.util.time.ClockUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ScheduledExecutorTest
{
    private final Instant now = Instant.now();

    @Rule
    public ControllableTaskScheduler agentRunnerService = new ControllableTaskScheduler();

    private ScheduledExecutorImpl executor;

    @Before
    public void init()
    {
        executor = new ScheduledExecutorImpl(agentRunnerService);

        executor.start();

        ClockUtil.setCurrentTime(now);
    }

    @After
    public void cleanUp()
    {
        executor.stopAsync();

        ClockUtil.reset();
    }

    @Test
    public void shouldSchedule()
    {
        final AtomicInteger invocationsCmd1 = new AtomicInteger(0);
        final AtomicInteger invocationsCmd2 = new AtomicInteger(0);

        executor.schedule(() -> invocationsCmd1.incrementAndGet(), Duration.ofSeconds(15));
        executor.schedule(() -> invocationsCmd2.incrementAndGet(), Duration.ofSeconds(30));

        agentRunnerService.waitUntilDone();
        assertThat(invocationsCmd1.get()).isEqualTo(0);
        assertThat(invocationsCmd2.get()).isEqualTo(0);

        ClockUtil.setCurrentTime(now.plusSeconds(15));

        agentRunnerService.waitUntilDone();
        assertThat(invocationsCmd1.get()).isEqualTo(1);
        assertThat(invocationsCmd2.get()).isEqualTo(0);

        ClockUtil.setCurrentTime(now.plusSeconds(30));

        agentRunnerService.waitUntilDone();
        assertThat(invocationsCmd1.get()).isEqualTo(1);
        assertThat(invocationsCmd2.get()).isEqualTo(1);
    }

    @Test
    public void shouldScheduleAtFixedRate()
    {
        final AtomicInteger invocationsCmd1 = new AtomicInteger(0);
        final AtomicInteger invocationsCmd2 = new AtomicInteger(0);

        executor.scheduleAtFixedRate(() -> invocationsCmd1.incrementAndGet(), Duration.ofSeconds(15));
        executor.scheduleAtFixedRate(() -> invocationsCmd2.incrementAndGet(), Duration.ofSeconds(30));

        agentRunnerService.waitUntilDone();
        assertThat(invocationsCmd1.get()).isEqualTo(1);
        assertThat(invocationsCmd2.get()).isEqualTo(1);

        ClockUtil.setCurrentTime(now.plusSeconds(15));

        agentRunnerService.waitUntilDone();
        assertThat(invocationsCmd1.get()).isEqualTo(2);
        assertThat(invocationsCmd2.get()).isEqualTo(1);

        ClockUtil.setCurrentTime(now.plusSeconds(30));

        agentRunnerService.waitUntilDone();
        assertThat(invocationsCmd1.get()).isEqualTo(3);
        assertThat(invocationsCmd2.get()).isEqualTo(2);
    }

    @Test
    public void shouldScheduleAtFixedRateWithDelay()
    {
        final AtomicInteger invocationsCmd1 = new AtomicInteger(0);
        final AtomicInteger invocationsCmd2 = new AtomicInteger(0);

        executor.scheduleAtFixedRate(() -> invocationsCmd1.incrementAndGet(), Duration.ofSeconds(10), Duration.ofSeconds(30));
        executor.scheduleAtFixedRate(() -> invocationsCmd2.incrementAndGet(), Duration.ofSeconds(15), Duration.ofSeconds(30));

        agentRunnerService.waitUntilDone();
        assertThat(invocationsCmd1.get()).isEqualTo(0);
        assertThat(invocationsCmd2.get()).isEqualTo(0);

        ClockUtil.setCurrentTime(now.plusSeconds(10));

        agentRunnerService.waitUntilDone();
        assertThat(invocationsCmd1.get()).isEqualTo(1);
        assertThat(invocationsCmd2.get()).isEqualTo(0);

        ClockUtil.setCurrentTime(now.plusSeconds(15));

        agentRunnerService.waitUntilDone();
        assertThat(invocationsCmd1.get()).isEqualTo(1);
        assertThat(invocationsCmd2.get()).isEqualTo(1);

        ClockUtil.setCurrentTime(now.plusSeconds(40));

        agentRunnerService.waitUntilDone();
        assertThat(invocationsCmd1.get()).isEqualTo(2);
        assertThat(invocationsCmd2.get()).isEqualTo(1);

        ClockUtil.setCurrentTime(now.plusSeconds(45));

        agentRunnerService.waitUntilDone();
        assertThat(invocationsCmd1.get()).isEqualTo(2);
        assertThat(invocationsCmd2.get()).isEqualTo(2);
    }

    @Test
    public void shouldCancelScheduledCommand()
    {
        final AtomicInteger invocations = new AtomicInteger(0);

        final ScheduledCommand scheduledCommand = executor.scheduleAtFixedRate(() -> invocations.incrementAndGet(), Duration.ofSeconds(10));

        assertThat(scheduledCommand.isCancelled()).isFalse();

        agentRunnerService.waitUntilDone();
        assertThat(invocations.get()).isEqualTo(1);

        scheduledCommand.cancel();

        assertThat(scheduledCommand.isCancelled()).isTrue();

        ClockUtil.setCurrentTime(now.plusSeconds(10));

        agentRunnerService.waitUntilDone();
        assertThat(invocations.get()).isEqualTo(1);
    }

    @Test
    public void shouldCancelScheduledCommandOnFailure()
    {
        final AtomicInteger invocations = new AtomicInteger(0);

        final ScheduledCommand scheduledCommand = executor.scheduleAtFixedRate(() ->
        {
            invocations.incrementAndGet();
            throw new RuntimeException("expected failure");
        }, Duration.ofSeconds(10));

        assertThat(scheduledCommand.getDueDate()).isEqualTo(now.toEpochMilli());

        agentRunnerService.waitUntilDone();
        assertThat(invocations.get()).isEqualTo(1);

        ClockUtil.setCurrentTime(now.plusSeconds(10));

        agentRunnerService.waitUntilDone();
        assertThat(invocations.get()).isEqualTo(1);

        assertThat(scheduledCommand.getDueDate()).isEqualTo(now.toEpochMilli());
    }

}
