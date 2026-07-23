/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.config;

import io.camunda.analytics.lake.LakeConfig;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for hosting the lake ingest engine in this process (the one-backend deployment
 * shape), bound with the prefix {@code lake.serving.ingest}. Disabled by default: without {@code
 * lake.serving.ingest.enabled=true} this application stays a pure read layer and none of these
 * values are consulted — which is also why every field except the flag has a workable default
 * rather than failing fast the way {@link LakeServingProperties#warehouseDir()} does.
 *
 * <p>The warehouse directory is deliberately NOT repeated here: the hosted ingest always writes to
 * the same warehouse this application serves ({@code lake.serving.warehouse-dir}), because two
 * values would only ever be a misconfiguration. The defaults for everything else mirror {@code
 * LakePocApp}'s standalone system-property defaults, so a config that works for the standalone
 * runner works here unchanged.
 *
 * @param enabled hosts the ingest engine in-process when {@code true} (default {@code false})
 * @param contactPoint event-bridge gateway base URL
 * @param topic the records topic to consume
 * @param group consumer group id
 * @param stateDir the translator's RocksDB state directory. Optional: when unset it derives from
 *     the serving warehouse directory as a {@code <name>-state} sibling (e.g. {@code /data/lake}
 *     &rarr; {@code /data/lake-state}), mirroring the standalone runner's {@code ./data/lake} /
 *     {@code ./data/lake-state} convention — and, unlike that runner's cwd-relative default, always
 *     anchored to the explicitly configured warehouse path.
 * @param flushRows rows per partition buffered before a flush is cut
 * @param flushIntervalMs max age of buffered rows before a flush is cut
 * @param stateDumpIntervalMs cadence of the translator state dump
 * @param compactIntervalMs cadence of the lake compactor
 * @param uiPort the legacy PoC status UI's port (still served by the hosted engine)
 * @param bpmnDir optional directory of BPMN files to pre-seed definitions from
 * @param objectTombstoneRetentionMs how long closed-object tombstones are kept before the sweep
 *     removes them; {@code null} means {@link LakeConfig#DEFAULT_OBJECT_TOMBSTONE_RETENTION_MS}
 */
@ConfigurationProperties(prefix = "lake.serving.ingest")
public record LakeIngestProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("http://localhost:8080") String contactPoint,
    @DefaultValue("zeebe-records") String topic,
    @DefaultValue("lake-poc") String group,
    String stateDir,
    @DefaultValue("5000") int flushRows,
    @DefaultValue("30000") long flushIntervalMs,
    @DefaultValue("30000") long stateDumpIntervalMs,
    @DefaultValue("300000") long compactIntervalMs,
    @DefaultValue("8091") int uiPort,
    String bpmnDir,
    Long objectTombstoneRetentionMs) {

  /** Maps these properties onto the lake engine's own config, anchored to {@code warehouseDir}. */
  public LakeConfig toLakeConfig(final Path warehouseDir) {
    return new LakeConfig(
        contactPoint,
        topic,
        group,
        warehouseDir,
        resolveStateDir(warehouseDir),
        flushRows,
        flushIntervalMs,
        stateDumpIntervalMs,
        compactIntervalMs,
        uiPort,
        bpmnDir == null || bpmnDir.isBlank() ? null : Path.of(bpmnDir),
        objectTombstoneRetentionMs == null
            ? LakeConfig.DEFAULT_OBJECT_TOMBSTONE_RETENTION_MS
            : objectTombstoneRetentionMs);
  }

  /** See {@link #stateDir}'s derivation rule. */
  Path resolveStateDir(final Path warehouseDir) {
    if (stateDir != null && !stateDir.isBlank()) {
      return Path.of(stateDir);
    }
    final Path name = warehouseDir.getFileName();
    return name == null
        ? warehouseDir.resolve("lake-state")
        : warehouseDir.resolveSibling(name + "-state");
  }
}
