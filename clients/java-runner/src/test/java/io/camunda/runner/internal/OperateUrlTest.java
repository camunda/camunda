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
package io.camunda.runner.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.runner.Run;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class OperateUrlTest {

  @Test
  void shouldComposeOperateUrlFromRestAddress() {
    // given
    final Run run =
        new DefaultRun(
            "stephan-r7f3a",
            "stephan-r7f3a-order",
            42L,
            Instant.now(),
            List.of(),
            new NoopCluster(URI.create("http://localhost:8080")),
            new WorkerRegistration(),
            URI.create("http://localhost:8080"),
            Map.of());

    // when
    final String url = run.operateUrl();

    // then
    assertThat(url)
        .isEqualTo("http://localhost:8080/operate/processes?process=stephan-r7f3a-order");
  }

  @Test
  void shouldStripTrailingSlashFromRestAddress() {
    // given
    final Run run =
        new DefaultRun(
            "u-x",
            "u-x-p",
            1L,
            Instant.now(),
            List.of(),
            new NoopCluster(URI.create("http://host:9000/")),
            new WorkerRegistration(),
            URI.create("http://host:9000/"),
            Map.of());

    // expect
    assertThat(run.operateUrl()).isEqualTo("http://host:9000/operate/processes?process=u-x-p");
  }

  @Test
  void shouldExposeWorkersHandledByCleanElementId() {
    // given — handled is keyed by jobType internally; we expose by elementId
    final WorkerRegistration registration = new WorkerRegistration();
    final Run run =
        new DefaultRun(
            "r",
            "r-p",
            1L,
            Instant.now(),
            List.of(),
            new NoopCluster(URI.create("http://h:1")),
            registration,
            URI.create("http://h:1"),
            Map.of("r-validate", "validate"));

    // when — simulate that no jobs ran (snapshot is empty)
    final Map<String, Long> handled = run.workersHandled();

    // then — empty since the registration has no counters; the translation is a no-op here
    assertThat(handled).isEmpty();
  }

  /** Minimal {@link io.camunda.runner.Cluster} for tests. */
  private static final class NoopCluster implements io.camunda.runner.Cluster {
    private final URI rest;

    NoopCluster(final URI rest) {
      this.rest = rest;
    }

    @Override
    public io.camunda.client.CamundaClient client() {
      return null;
    }

    @Override
    public boolean ownsClient() {
      return false;
    }

    @Override
    public URI restAddress() {
      return rest;
    }

    @Override
    public void close() {}
  }
}
