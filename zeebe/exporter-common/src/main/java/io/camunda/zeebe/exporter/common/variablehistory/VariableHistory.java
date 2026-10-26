/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.variablehistory;

import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo.AuditLogActor;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableOperationType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * PoC for the variable history write load. Decides which variable changes the variable history
 * records, and who made them.
 */
@NullMarked
public final class VariableHistory {

  public static final String ENGINE_ACTOR_TYPE = "ENGINE";

  private VariableHistory() {}

  /**
   * Returns true when the variable history records this change. The variable key decides, so a
   * recorded variable keeps all its changes, e.g. {@code isRecorded(record, 33)} keeps about a
   * third of the variables.
   */
  public static boolean isRecorded(final Record<VariableRecordValue> record, final int percent) {
    if (percent <= 0) {
      return false;
    }
    final var intent = record.getIntent();
    if (intent != VariableIntent.CREATED && intent != VariableIntent.UPDATED) {
      return false;
    }
    return percent >= 100 || Math.floorMod(mix(record.getKey()), 100) < percent;
  }

  /** Returns the actor of the change: the engine for its own changes, else the requester. */
  public static Actor actorOf(final Record<VariableRecordValue> record) {
    if (record.getValue().getSource().getType() == VariableOperationType.ENGINE) {
      return new Actor(ENGINE_ACTOR_TYPE, null);
    }
    final var actor = AuditLogActor.of(record);
    return new Actor(actor.actorType().name(), actor.actorId());
  }

  // SplitMix64 finalizer: keys are sequential per partition, this spreads them over the buckets
  private static long mix(final long key) {
    long z = key;
    z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
    z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
    return z ^ (z >>> 31);
  }

  public record Actor(String type, @Nullable String id) {}
}
