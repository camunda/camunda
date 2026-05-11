/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.ProcessDefinitionKeyNames;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessDefinitionMetrics {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinitionMetrics.class);

  private final MeterRegistry registry;
  private final AtomicLong totalUniqueProcessIds = new AtomicLong(0);

  // processDefinitionKey -> (bpmnProcessId, sizeBytes) — to know what to subtract on deletion
  private final Map<Long, KeyEntry> entriesByKey = new HashMap<>();
  // bpmnProcessId -> set of processDefinitionKeys with that ID
  private final Map<String, Set<Long>> keysByProcessId = new HashMap<>();
  // bpmnProcessId -> running total of bytes across all deployed versions
  private final Map<String, AtomicLong> totalSizeByProcessId = new HashMap<>();
  // bpmnProcessId -> registered Gauge (to allow removal when last version is deleted)
  private final Map<String, Gauge> sizeGaugesByProcessId = new HashMap<>();

  public ProcessDefinitionMetrics(final MeterRegistry registry, final ProcessState processState) {
    this.registry = Objects.requireNonNull(registry);

    final long startNanos = System.nanoTime();
    processState.forEachProcess(
        null,
        process -> {
          if (process.getState() == PersistedProcessState.ACTIVE) {
            final String bpmnProcessId = BufferUtil.bufferAsString(process.getBpmnProcessId());
            addEntry(process.getKey(), bpmnProcessId, process.getResource().capacity());
          }
          return true;
        });
    LOG.debug(
        "Initialized process definition metrics by scanning {} process definitions in {}",
        entriesByKey.size(),
        Duration.ofNanos(System.nanoTime() - startNanos));

    totalUniqueProcessIds.set(keysByProcessId.size());

    final var definitionsDoc = EngineMetricsDoc.DEPLOYED_PROCESS_DEFINITIONS;
    Gauge.builder(definitionsDoc.getName(), totalUniqueProcessIds, AtomicLong::get)
        .description(definitionsDoc.getDescription())
        .register(registry);
  }

  /**
   * Records the deployment of a new process definition version. Must be called from live event
   * processing only (not during replay), since the initial state is recovered via the constructor
   * scan.
   */
  public void processDefinitionDeployed(
      final long processDefinitionKey, final String bpmnProcessId, final int sizeBytes) {
    final var keys = addEntry(processDefinitionKey, bpmnProcessId, sizeBytes);
    if (keys.size() == 1) {
      totalUniqueProcessIds.incrementAndGet();
    }
  }

  /**
   * Records the deletion of a deployed process definition version. Must be called from live event
   * processing only (not during replay).
   */
  public void processDefinitionDeleted(final long processDefinitionKey) {
    final var entry = entriesByKey.remove(processDefinitionKey);
    if (entry == null) {
      return;
    }

    final var keys = keysByProcessId.get(entry.bpmnProcessId());
    if (keys == null) {
      return;
    }
    keys.remove(processDefinitionKey);

    if (keys.isEmpty()) {
      keysByProcessId.remove(entry.bpmnProcessId());
      totalSizeByProcessId.remove(entry.bpmnProcessId());
      final var gauge = sizeGaugesByProcessId.remove(entry.bpmnProcessId());
      if (gauge != null) {
        registry.remove(gauge);
      }
      totalUniqueProcessIds.decrementAndGet();
    } else {
      totalSizeByProcessId.get(entry.bpmnProcessId()).addAndGet(-entry.sizeBytes());
    }
  }

  private Set<Long> addEntry(
      final long processDefinitionKey, final String bpmnProcessId, final int sizeBytes) {
    entriesByKey.put(processDefinitionKey, new KeyEntry(bpmnProcessId, sizeBytes));
    final var keys = keysByProcessId.computeIfAbsent(bpmnProcessId, id -> new HashSet<>());
    keys.add(processDefinitionKey);

    final var totalSize =
        totalSizeByProcessId.computeIfAbsent(bpmnProcessId, id -> new AtomicLong());
    totalSize.addAndGet(sizeBytes);

    sizeGaugesByProcessId.computeIfAbsent(
        bpmnProcessId,
        id -> {
          final var sizeDoc = EngineMetricsDoc.PROCESS_DEFINITION_RESOURCE_SIZE;
          return Gauge.builder(sizeDoc.getName(), totalSize, AtomicLong::get)
              .description(sizeDoc.getDescription())
              .tag(ProcessDefinitionKeyNames.BPMN_PROCESS_ID.asString(), id)
              .register(registry);
        });
    return keys;
  }

  private record KeyEntry(String bpmnProcessId, int sizeBytes) {}
}
