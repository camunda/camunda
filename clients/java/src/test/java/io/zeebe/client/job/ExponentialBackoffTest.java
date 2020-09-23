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
package io.zeebe.client.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.client.api.worker.BackoffSupplier;
import io.zeebe.client.impl.worker.ExponentialBackoff;
import io.zeebe.client.impl.worker.ExponentialBackoffBuilderImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.Test;

public final class ExponentialBackoffTest {
  @Test
  public void plotIt() {
    final ExponentialBackoff defaultSupplier =
        (ExponentialBackoff) BackoffSupplier.newBackoffBuilder().jitterFactor(0.15).build();

    final List<String> list = new ArrayList<>();
    long delay = 0L;
    for (int i = 0; i < 100; i++) {
      delay = defaultSupplier.supplyRetryDelay(delay);
      list.add("" + i + ".0, " + delay + ".0");
    }

    System.out.println(String.join("\n", list));
  }

  @Test
  public void shouldReturnDelayWithinBounds() {
    // given
    final long maxDelay = 1_000L;
    final long minDelay = 50L;
    final BackoffSupplier supplier =
        new ExponentialBackoffBuilderImpl()
            .maxDelay(maxDelay)
            .minDelay(minDelay)
            .backoffFactor(1.6)
            .jitterFactor(0)
            .build();
    final LongStream delayGenerator =
        LongStream.iterate(supplier.supplyRetryDelay(0), supplier::supplyRetryDelay);

    // when
    final ArrayList<Long> delays = collectLongStream(delayGenerator, 100);

    // then - as we used 0 for jitter factor, we can guarantee all are sorted
    final long previousDelay = -1L;
    assertThat(delays).startsWith(minDelay).endsWith(maxDelay).isNotEmpty();
    assertIsStrictlyIncreasing(maxDelay, delays, previousDelay);
  }

  @Test
  public void shouldBeRandomizedWithJitter() {
    // given
    final long maxDelay = 1_000L;
    final long minDelay = 50L;
    final double jitterFactor = 0.2;
    final BackoffSupplier supplier =
        new ExponentialBackoffBuilderImpl()
            .maxDelay(maxDelay)
            .minDelay(minDelay)
            .backoffFactor(1.5)
            .jitterFactor(jitterFactor)
            .build();
    final long lowerMaxBound = Math.round(maxDelay + maxDelay * -jitterFactor);
    final long upperMaxBound = Math.round(maxDelay + maxDelay * jitterFactor);
    final LongStream delayGenerator =
        LongStream.iterate(maxDelay, delay -> supplier.supplyRetryDelay(maxDelay));

    // when
    final ArrayList<Long> delays = collectLongStream(delayGenerator, 10);

    // then - note that we don't test how uniform the distribution is, just that we get different
    // values
    assertThat(delays)
        .isNotEmpty()
        .allSatisfy(delay -> assertThat(delay).isBetween(lowerMaxBound, upperMaxBound));
    assertThat(delays.stream().distinct()).hasSizeGreaterThan(1);
  }

  // ignore for the sake of readability
  private void assertIsStrictlyIncreasing(
      final long maxDelay, final ArrayList<Long> delays, long previousDelay) {
    for (final long delay : delays) {
      if (delay != maxDelay) {
        assertThat(delay).isGreaterThan(previousDelay);
      }

      previousDelay = delay;
    }
  }

  private ArrayList<Long> collectLongStream(final LongStream stream, final int maxCount) {
    return stream
        .limit(maxCount)
        .collect(
            ArrayList::new, ArrayList::add, (head, tail) -> new ArrayList<>(head).addAll(tail));
  }
}
