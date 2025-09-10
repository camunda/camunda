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
package io.camunda.zeebe.client.impl.worker.metrics;

import io.camunda.zeebe.client.api.worker.JobWorkerMetrics;
import io.camunda.zeebe.client.api.worker.metrics.MicrometerJobWorkerMetricsBuilder.Names;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Condition;
import org.assertj.core.condition.VerboseCondition;
import org.junit.jupiter.api.Test;

final class MicrometerJobWorkerMetricsTest {
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final Iterable<Tag> tags = Tags.of("foo", "bar");
  private final JobWorkerMetrics metrics =
      JobWorkerMetrics.micrometer().withMeterRegistry(meterRegistry).withTags(tags).build();

  @Test
  void shouldCountActivatedJobs() {
    // when
    metrics.jobActivated(5);

    // then
    Assertions.assertThat(meterRegistry).has(hasCounter(Names.JOB_ACTIVATED, tags));
    Assertions.assertThat(meterRegistry.counter(Names.JOB_ACTIVATED.asString(), tags))
        .has(hasCount(5));
  }

  @Test
  void shouldCountHandledJobs() {
    // when
    metrics.jobHandled(3);

    // then
    Assertions.assertThat(meterRegistry).has(hasCounter(Names.JOB_HANDLED, tags));
    Assertions.assertThat(meterRegistry.counter(Names.JOB_HANDLED.asString(), tags))
        .has(hasCount(3));
  }

  private Condition<MeterRegistry> hasCounter(final Names name, final Iterable<Tag> tags) {
    return VerboseCondition.verboseCondition(
        registry -> registry.find(name.asString()).tags(tags).counter() != null,
        String.format("a counter named '%s', with tags %s", name.asString(), tags),
        registry -> String.format(" but registered meters are %s", asString(registry.getMeters())));
  }

  private String asString(final List<Meter> meters) {
    return Arrays.toString(meters.stream().map(Meter::getId).toArray());
  }

  // unfortunately meters have no String representation, so when they fail it's not super helpful
  private Condition<Counter> hasCount(final int count) {
    return VerboseCondition.verboseCondition(
        counter -> counter.count() == count,
        String.format("a count of '%d'", count),
        counter -> String.format(" but actual count is '%f'", counter.count()));
  }
}
