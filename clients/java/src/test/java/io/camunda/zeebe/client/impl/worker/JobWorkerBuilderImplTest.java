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
package io.camunda.zeebe.client.impl.worker;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.worker.JobClient;
import java.io.Closeable;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobWorkerBuilderImplTest {

  private JobWorkerBuilderImpl jobWorkerBuilder;
  private ZeebeClientConfiguration zeebeClientConfiguration;
  private JobClient jobClient;
  private ScheduledExecutorService executorService;
  private List<Closeable> closeables;

  @BeforeEach
  void setUp() {
    zeebeClientConfiguration = mock();
    jobClient = mock();
    executorService = mock();
    closeables = Collections.emptyList();
    jobWorkerBuilder =
        new JobWorkerBuilderImpl(zeebeClientConfiguration, jobClient, executorService, closeables);
  }

  @Test
  void shouldThrowErrorIfTimeoutIsNegative() {
    // given
    // when
    assertThatThrownBy(
            () ->
                jobWorkerBuilder
                    .jobType("some-type")
                    .handler(mock())
                    .timeout(Duration.ofSeconds(5).negated())
                    .open())
        // then
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timeout must be not negative");
  }

  @Test
  void shouldThrowErrorIfTimeoutIsZero() {
    // given
    // when
    assertThatThrownBy(
            () ->
                jobWorkerBuilder.jobType("some-type").handler(mock()).timeout(Duration.ZERO).open())
        // then
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timeout must be not zero");
  }
}
