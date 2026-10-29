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

  /**
   * Returns the value the history records. With {@code collectionItems}, an output collection
   * update records only the item of the slot it fills, e.g. {@code [1,2,null]} at slot 1 gives
   * {@code 2}.
   */
  public static String valueOf(
      final Record<VariableRecordValue> record, final boolean collectionItems) {
    final var value = record.getValue().getValue();
    final int slot = record.getValue().getSource().getCollectionIndex();
    if (!collectionItems || slot < 0) {
      return value;
    }
    final var item = itemAt(value, slot);
    return item != null ? item : value;
  }

  // returns the element at the index of a JSON array, or null if there is none
  private static @Nullable String itemAt(final String json, final int index) {
    final var array = json.strip();
    if (array.isEmpty() || array.charAt(0) != '[') {
      return null;
    }
    int depth = 0;
    int current = 0;
    int start = 1;
    boolean inString = false;
    for (int i = 1; i < array.length(); i++) {
      final char c = array.charAt(i);
      if (inString) {
        if (c == '\\') {
          i++;
        } else if (c == '"') {
          inString = false;
        }
      } else if (c == '"') {
        inString = true;
      } else if (depth > 0 && (c == ']' || c == '}')) {
        depth--;
      } else if (c == '[' || c == '{') {
        depth++;
      } else if (depth == 0 && (c == ',' || c == ']')) {
        if (current == index) {
          final var item = array.substring(start, i).strip();
          return item.isEmpty() ? null : item;
        }
        current++;
        start = i + 1;
      }
    }
    return null;
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
