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
package io.camunda.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

@Tag("integration")
final class LiveBpmnIT {

  @Test
  void shouldRunSimpleProcessAgainstTestcontainer() {
    // given
    assumeThat(dockerAvailable()).as("Docker daemon is available for Testcontainers").isTrue();

    final AtomicInteger count = new AtomicInteger();

    // when
    try (Cluster c = LiveBpmn.cluster().testcontainer()) {
      final Run run =
          LiveBpmn.createExecutableProcess("order")
              .startEvent()
              .serviceTask(
                  "validate",
                  (Job job) -> {
                    count.incrementAndGet();
                    return Map.of("ok", true);
                  })
              .endEvent()
              .run(3, c);
      run.await(Duration.ofMinutes(2));

      // then
      assertThat(count.get()).isEqualTo(3);
      assertThat(run.workersHandled()).hasSize(1);
      assertThat(run.workersHandled().values()).containsExactly(3L);
      run.close();
    }
  }

  private static boolean dockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (final Throwable t) {
      return false;
    }
  }
}
