/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled.api;

import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.List;

/**
 * Single output of one {@link ScheduledTask#run} call. Carries the appended local commands, the
 * inter-partition sends, and the scheduling {@link Decision} that tells the runtime when to fire
 * next.
 *
 * <p>Construct via {@link Builder} obtained from {@link TaskContext#result()}. Each builder
 * terminal ({@link Builder#idle}, {@link Builder#awaitDueAt}, {@link Builder#yieldNow}) returns the
 * finished {@code Result}; exactly one terminal must be called per run.
 */
public final class Result {

  private final List<AppendedCommand> appendedCommands;
  private final List<InterPartitionSend> interPartitionSends;
  private final Decision decision;

  public Result(
      final List<AppendedCommand> appendedCommands,
      final List<InterPartitionSend> interPartitionSends,
      final Decision decision) {
    this.appendedCommands = List.copyOf(appendedCommands);
    this.interPartitionSends = List.copyOf(interPartitionSends);
    this.decision = decision;
  }

  /** Local follow-up commands appended during the run, in order. */
  public List<AppendedCommand> appendedCommands() {
    return appendedCommands;
  }

  /** Inter-partition sends issued during the run, in order. */
  public List<InterPartitionSend> interPartitionSends() {
    return interPartitionSends;
  }

  /** Scheduling decision: when the runtime should fire the task next. */
  public Decision decision() {
    return decision;
  }

  /**
   * The fluent builder a {@link ScheduledTask} uses to record side effects and pick the next
   * scheduling decision. The cursor type {@code C} is the task's resume-state shape; tasks with no
   * continuation declare {@code Builder<Void>} and use the no-arg {@link #yieldNow()} variant.
   *
   * <h3>Usage rules</h3>
   *
   * <ul>
   *   <li>Exactly one terminal ({@link #idle}, {@link #awaitDueAt}, {@link #yieldNow}) must be
   *       called per run. They are mutually exclusive — they all answer "when next?".
   *   <li>{@link #yieldNow(Object)} releases the actor thread <em>and</em> declares "I have more
   *       work, resume me from this cursor"; the runtime reschedules immediately. It is therefore
   *       not combinable with {@link #awaitDueAt(long)} (which says "I'm done with what's currently
   *       due, wake me at this absolute time"). {@link #idle()} similarly says "done for now";
   *       combining either with yield would be contradictory.
   *   <li>{@link #append} returning {@code false} means the result batch is full; the task should
   *       stop iterating and call {@link #yieldNow}.
   * </ul>
   */
  public interface Builder<C> {

    /**
     * Appends a local command without an explicit record key.
     *
     * @return {@code true} if the record fit, {@code false} if the batch is full
     */
    boolean append(Intent intent, UnifiedRecordValue value);

    /**
     * Appends a local command with an explicit record key.
     *
     * @return {@code true} if the record fit, {@code false} if the batch is full
     */
    boolean append(long key, Intent intent, UnifiedRecordValue value);

    /** Sends a command to another partition. Best-effort, fire-and-forget. */
    void sendInterPartition(
        int receiverPartitionId,
        ValueType valueType,
        Intent intent,
        Long recordKey,
        UnifiedRecordValue value,
        AuthInfo authInfo);

    /** Convenience overload of {@link #sendInterPartition} without {@link AuthInfo}. */
    default void sendInterPartition(
        final int receiverPartitionId,
        final ValueType valueType,
        final Intent intent,
        final Long recordKey,
        final UnifiedRecordValue value) {
      sendInterPartition(receiverPartitionId, valueType, intent, recordKey, value, null);
    }

    // -------- Terminals --------

    /**
     * Done with this run; no specific next due-date in mind. The runtime reschedules at {@code now
     * + fallbackInterval} when configured; pure on-demand schedules sleep until externally
     * re-triggered. Clears any stored resume cursor on the runtime.
     */
    Result idle();

    /**
     * Done with what is currently due. The next entry is not due until {@code timestampMs}; the
     * runtime sleeps until then. Clears any stored resume cursor on the runtime.
     */
    Result awaitDueAt(long timestampMs);

    /**
     * The task has more work right now but is releasing the actor thread so other scheduled tasks
     * (and stream processing) get a turn before continuing. The runtime reschedules at {@code now +
     * minResolution} and saves {@code cursor}; the next run sees it via {@link
     * TaskContext#resumeCursor()}.
     */
    Result yieldNow(C cursor);

    /**
     * Shorthand for {@link #yieldNow(Object) yieldNow(null)}. Intended for tasks declared as {@code
     * ScheduledTask<Void>}; on a {@code <C>} task this stores {@code null} as the cursor, which is
     * usually a bug.
     */
    default Result yieldNow() {
      return yieldNow(null);
    }
  }

  /** What the task tells the runtime about when it should fire next. */
  public sealed interface Decision {

    /** No more due entries until this absolute timestamp (millis since epoch). */
    record AwaitDueAt(long timestampMs) implements Decision {}

    /**
     * The task yielded mid-iteration; the runtime reschedules immediately. {@code cursor} is the
     * resume state to hand back via {@link TaskContext#resumeCursor()}, or {@code null}.
     */
    record YieldNow(Object cursor) implements Decision {}

    /** Done; runtime falls back to its configured interval (or stays idle for on-demand). */
    record Idle() implements Decision {
      public static final Idle INSTANCE = new Idle();
    }
  }

  /** A local command appended during a run. */
  public record AppendedCommand(long key, Intent intent, UnifiedRecordValue value) {}

  /** An inter-partition send issued during a run. */
  public record InterPartitionSend(
      int receiverPartitionId,
      ValueType valueType,
      Intent intent,
      Long recordKey,
      UnifiedRecordValue value,
      AuthInfo authInfo) {}
}
