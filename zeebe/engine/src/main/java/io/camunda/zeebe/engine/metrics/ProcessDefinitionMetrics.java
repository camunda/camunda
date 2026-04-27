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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class ProcessDefinitionMetrics {

  private final MeterRegistry registry;
  private final AtomicLong totalVersions = new AtomicLong(0);
  private final AtomicLong totalUniqueProcessIds = new AtomicLong(0);

  // processDefinitionKey -> version entry metadata and size
  private final Map<Long, VersionEntry> versionsByKey = new HashMap<>();
  // bpmnProcessId -> set of processDefinitionKeys with that ID (tracks which process IDs are live)
  private final Map<String, Set<Long>> keysByProcessId = new HashMap<>();
  // processDefinitionKey -> registered Gauge (to allow removal on deletion)
  private final Map<Long, Gauge> sizeGaugesByKey = new HashMap<>();

  public ProcessDefinitionMetrics(final MeterRegistry registry, final ProcessState processState) {
    this.registry = Objects.requireNonNull(registry);

    processState.forEachProcess(
        null,
        process -> {
          if (process.getState() == PersistedProcessState.ACTIVE) {
            final String bpmnProcessId = BufferUtil.bufferAsString(process.getBpmnProcessId());
            addEntry(
                process.getKey(),
                bpmnProcessId,
                process.getVersion(),
                process.getResource().capacity());
          }
          return true;
        });

    totalVersions.set(versionsByKey.size());
    totalUniqueProcessIds.set(keysByProcessId.size());

    final var definitionsDoc = EngineMetricsDoc.DEPLOYED_PROCESS_DEFINITIONS;
    Gauge.builder(definitionsDoc.getName(), totalUniqueProcessIds, AtomicLong::get)
        .description(definitionsDoc.getDescription())
        .register(registry);

    final var versionsDoc = EngineMetricsDoc.DEPLOYED_PROCESS_DEFINITION_VERSIONS;
    Gauge.builder(versionsDoc.getName(), totalVersions, AtomicLong::get)
        .description(versionsDoc.getDescription())
        .register(registry);
  }

  /**
   * Records the deployment of a new process definition version. Must be called from live event
   * processing only (not during replay), since the initial state is recovered via the constructor
   * scan.
   */
  public void processDefinitionDeployed(
      final long processDefinitionKey,
      final String bpmnProcessId,
      final int version,
      final int sizeBytes) {
    addEntry(processDefinitionKey, bpmnProcessId, version, sizeBytes);
    if (keysByProcessId.get(bpmnProcessId).size() == 1) {
      totalUniqueProcessIds.incrementAndGet();
    }
    totalVersions.incrementAndGet();
  }

  /**
   * Records the deletion of a deployed process definition version. Must be called from live event
   * processing only (not during replay).
   */
  public void processDefinitionDeleted(final long processDefinitionKey) {
    final var entry = versionsByKey.remove(processDefinitionKey);
    if (entry == null) {
      return;
    }

    final var gauge = sizeGaugesByKey.remove(processDefinitionKey);
    if (gauge != null) {
      registry.remove(gauge);
    }

    final var keys = keysByProcessId.get(entry.bpmnProcessId());
    if (keys != null) {
      keys.remove(processDefinitionKey);
      if (keys.isEmpty()) {
        keysByProcessId.remove(entry.bpmnProcessId());
        totalUniqueProcessIds.decrementAndGet();
      }
    }
    totalVersions.decrementAndGet();
  }

  private void addEntry(
      final long processDefinitionKey,
      final String bpmnProcessId,
      final int version,
      final int sizeBytes) {
    final var entry = new VersionEntry(bpmnProcessId, version, sizeBytes);
    versionsByKey.put(processDefinitionKey, entry);
    keysByProcessId.computeIfAbsent(bpmnProcessId, id -> new HashSet<>()).add(processDefinitionKey);

    final var sizeDoc = EngineMetricsDoc.PROCESS_DEFINITION_RESOURCE_SIZE;
    final var gauge =
        Gauge.builder(sizeDoc.getName(), entry, VersionEntry::sizeBytes)
            .description(sizeDoc.getDescription())
            .tag(ProcessDefinitionKeyNames.BPMN_PROCESS_ID.asString(), bpmnProcessId)
            .tag(ProcessDefinitionKeyNames.VERSION.asString(), String.valueOf(version))
            .register(registry);
    sizeGaugesByKey.put(processDefinitionKey, gauge);
  }

  private record VersionEntry(String bpmnProcessId, int version, int sizeBytes) {}
}
