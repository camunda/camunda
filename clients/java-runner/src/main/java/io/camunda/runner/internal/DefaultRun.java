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

import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.runner.Cluster;
import io.camunda.runner.Run;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Default {@link Run} implementation. */
public final class DefaultRun implements Run {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultRun.class);
  private static final Duration POLL_INTERVAL = Duration.ofMillis(500);

  private final String runId;
  private final String processId;
  private final long processDefinitionKey;
  private final Instant startedAt;
  private final List<Long> instances;
  private final Cluster cluster;
  private final WorkerRegistration workers;
  private final URI restAddress;
  private final Map<String, String> jobTypeToHandleKey;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final Thread shutdownHook;

  public DefaultRun(
      final String runId,
      final String processId,
      final long processDefinitionKey,
      final Instant startedAt,
      final List<Long> instances,
      final Cluster cluster,
      final WorkerRegistration workers,
      final URI restAddress,
      final Map<String, String> jobTypeToHandleKey) {
    this.runId = runId;
    this.processId = processId;
    this.processDefinitionKey = processDefinitionKey;
    this.startedAt = startedAt;
    this.instances = List.copyOf(instances);
    this.cluster = cluster;
    this.workers = workers;
    this.restAddress = restAddress;
    this.jobTypeToHandleKey = Map.copyOf(jobTypeToHandleKey);
    this.shutdownHook = new Thread(this::closeQuietly, "livebpmn-shutdown-" + runId);
    Runtime.getRuntime().addShutdownHook(shutdownHook);
    maybeOpenBrowser();
  }

  /**
   * Always logs the {@link #operateUrl()}. Optionally also opens it in the default browser when the
   * system property {@code livebpmn.openOperate=true} is set (default: log only). Headless
   * environments and platforms without a {@link Desktop.Action#BROWSE} action fall back to logging.
   *
   * <p>Only the SDK client is touched here — the cluster itself is not affected.
   */
  private void maybeOpenBrowser() {
    final String url = operateUrl();
    LOG.info("Operate: {}", url);
    if (!Boolean.parseBoolean(System.getProperty("livebpmn.openOperate", "false"))) {
      return;
    }
    try {
      if (!GraphicsEnvironment.isHeadless()
          && Desktop.isDesktopSupported()
          && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI.create(url));
      }
    } catch (final Exception e) {
      LOG.debug(
          "could not auto-open browser ({}): {}", e.getClass().getSimpleName(), e.getMessage());
    }
  }

  @Override
  public String runId() {
    return runId;
  }

  @Override
  public String processId() {
    return processId;
  }

  @Override
  public long processDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public Instant startedAt() {
    return startedAt;
  }

  @Override
  public List<Long> instances() {
    return instances;
  }

  @Override
  public Map<String, Long> workersHandled() {
    final Map<String, Long> byJobType = workers.handledSnapshot();
    final LinkedHashMap<String, Long> byElementId = new LinkedHashMap<>();
    byJobType.forEach(
        (jobType, count) -> {
          final String elementId = jobTypeToHandleKey.getOrDefault(jobType, jobType);
          byElementId.put(elementId, count);
        });
    return byElementId;
  }

  @Override
  public String operateUrl() {
    String base = restAddress.toString();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return base + "/operate/processes?process=" + processId;
  }

  @Override
  public void await(final Duration timeout) {
    final long deadline = System.nanoTime() + timeout.toNanos();
    final Set<Long> remaining = new HashSet<>(instances);
    while (!remaining.isEmpty()) {
      pollOnce(remaining);
      if (remaining.isEmpty()) {
        return;
      }
      if (System.nanoTime() >= deadline) {
        LOG.warn(
            "await timed out: {} instance(s) still active for runId={}", remaining.size(), runId);
        return;
      }
      LockSupport.parkNanos(POLL_INTERVAL.toNanos());
    }
  }

  private void pollOnce(final Set<Long> remaining) {
    try {
      final SearchResponse<ProcessInstance> response =
          cluster
              .client()
              .newProcessInstanceSearchRequest()
              .filter(f -> f.processInstanceKey(p -> p.in(List.copyOf(remaining))))
              .send()
              .join();
      for (final ProcessInstance pi : response.items()) {
        if (isTerminal(pi)) {
          remaining.remove(pi.getProcessInstanceKey());
        }
      }
    } catch (final Exception e) {
      // secondary store may be eventually consistent; tolerate transient errors.
      LOG.debug("process-instance poll failed: {}", e.getMessage());
    }
  }

  private static boolean isTerminal(final ProcessInstance pi) {
    final ProcessInstanceState state = pi.getState();
    if (state == ProcessInstanceState.COMPLETED || state == ProcessInstanceState.TERMINATED) {
      return true;
    }
    return Boolean.TRUE.equals(pi.getHasIncident());
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    LOG.info("shutdown hook closing workers for runId={}", runId);
    workers.close();
    if (cluster.ownsClient()) {
      try {
        cluster.close();
      } catch (final Exception e) {
        LOG.warn("error closing cluster", e);
      }
    }
    try {
      Runtime.getRuntime().removeShutdownHook(shutdownHook);
    } catch (final IllegalStateException ignored) {
      // JVM already shutting down
    }
  }

  private void closeQuietly() {
    try {
      close();
    } catch (final Exception ignored) {
      // shutdown path
    }
  }

  /** Used when the model has no bindings — empty handled map should still surface. */
  public static Map<String, Long> emptyHandled() {
    return Collections.emptyMap();
  }
}
