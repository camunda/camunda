/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.analytics.examples.flink;

import java.io.Serializable;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The raw input record of the pipeline — one engine event off the source log.
 *
 * <p>This is deliberately identical (field for field) to the {@code SourceEvent} used in the Kafka
 * Streams and Spark reference twins, so the three implementations can be compared line by line.
 *
 * <p>Flink serializes records that cross an operator boundary (e.g. the {@code keyBy} shuffle). A
 * plain, public, {@link Serializable} POJO with a nullary constructor lets Flink use its efficient
 * POJO serializer rather than falling back to Kryo. In a real module you would generate this from
 * the protocol schema instead.
 *
 * @param type what kind of engine event this is
 * @param instanceKey the process-instance key — the natural key for Stage A
 * @param processId the process definition id — the grouping key for Stage B/C
 * @param tenantId the owning tenant
 * @param timestampMs engine event time in epoch millis; this is the pipeline's <em>event time</em>
 * @param elementId the BPMN element id, when the event is element-scoped (nullable)
 * @param varName variable name, only set for {@link EventType#VARIABLE} (nullable)
 * @param varValue variable value, only set for {@link EventType#VARIABLE} (nullable)
 * @param sourcePartition the source-log partition this event came from
 * @param sourceOffset the source-log offset within that partition (monotonic per partition)
 */
public record SourceEvent(
    EventType type,
    long instanceKey,
    String processId,
    String tenantId,
    long timestampMs,
    @Nullable String elementId,
    @Nullable String varName,
    @Nullable String varValue,
    int sourcePartition,
    long sourceOffset)
    implements Serializable {

  public SourceEvent {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(processId, "processId");
    Objects.requireNonNull(tenantId, "tenantId");
  }

  /** The lifecycle/side events we project over. */
  public enum EventType {
    ACTIVATED,
    COMPLETED,
    TERMINATED,
    VARIABLE,
    INCIDENT
  }
}
