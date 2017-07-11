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
package io.zeebe.logstreams.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import io.zeebe.util.time.ClockUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TimeBasedSnapshotPolicyTest
{
    private Instant now;

    @Before
    public void init()
    {
        now = Instant.now();

        ClockUtil.setCurrentTime(now);
    }

    @After
    public void cleanUp()
    {
        ClockUtil.reset();
    }

    @Test
    public void shouldUseIntervalAsInitialDelay()
    {
        final TimeBasedSnapshotPolicy policy = new TimeBasedSnapshotPolicy(Duration.ofSeconds(10));

        assertThat(policy.apply(0)).isFalse();

        ClockUtil.setCurrentTime(now.plusSeconds(10));

        assertThat(policy.apply(0)).isTrue();
    }

    @Test
    public void shouldApplyPeriodicallyWithInterval()
    {
        final TimeBasedSnapshotPolicy policy = new TimeBasedSnapshotPolicy(Duration.ofSeconds(10));

        ClockUtil.setCurrentTime(now.plusSeconds(10));
        assertThat(policy.apply(0)).isTrue();

        ClockUtil.setCurrentTime(now.plusSeconds(15));
        assertThat(policy.apply(0)).isFalse();

        ClockUtil.setCurrentTime(now.plusSeconds(20));
        assertThat(policy.apply(0)).isTrue();

    }

}
