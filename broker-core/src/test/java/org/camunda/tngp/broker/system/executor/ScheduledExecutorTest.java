/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.broker.system.executor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.camunda.tngp.test.util.agent.ControllableTaskScheduler;
import org.camunda.tngp.util.time.ClockUtil;
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
