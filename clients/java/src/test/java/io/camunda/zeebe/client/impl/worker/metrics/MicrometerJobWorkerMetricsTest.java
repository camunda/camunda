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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Condition;
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
    Assertions.assertThat(meterRegistry).has(new HasMeter(Names.JOB_ACTIVATED, tags, 5));
  }

  @Test
  void shouldCountHandledJobs() {
    // when
    metrics.jobHandled(3);

    // then
    Assertions.assertThat(meterRegistry).has(new HasMeter(Names.JOB_HANDLED, tags, 3));
  }

  private static final class HasMeter extends Condition<MeterRegistry> {
    private final Names name;
    private final Iterable<Tag> tags;
    private final int count;

    private HasMeter(final Names name, final Iterable<Tag> tags, final int count) {
      super(
          String.format(
              "counter named '%s', with tags %s, and count '%d'", name.asString(), tags, count));
      this.name = name;
      this.tags = tags;
      this.count = count;
    }

    @Override
    public boolean matches(final MeterRegistry value) {
      final Counter counter = value.find(name.asString()).tags(tags).counter();
      if (counter == null) {
        return false;
      }

      return counter.count() == count;
    }
  }
}
