/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.changelog;

import io.camunda.eventbridge.streaming.Task;
import io.camunda.eventbridge.streaming.state.api.StateStoreProvider;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.util.function.Function;

/**
 * A partition's role lifecycle (event-bridge-streaming ADR 0009 decision 6 / consumer-groups ADR
 * 0006 decision 1): owns the partition's {@link StateStoreProvider} across role changes and drives
 * either a {@link ChangelogApplier} (STANDBY) or a {@link Task} (ACTIVE) against it, one at a time.
 * The store is opened once by the caller and outlives every role flip — promotion and demotion
 * never close and reopen it, only ever the driver on top of it.
 *
 * <p><b>Promotion</b> ({@link #promote()}): drains the changelog to its current end (applies
 * through the last marker), closes the applier — the store stays open — and constructs the fold
 * {@link Task} against that same store. The fold then calls its own {@link Task#restore()} exactly
 * as it would on a normal restart, reading whatever source offset the applier's last applied cut
 * persisted through the caller's own bookkeeping (the same column family/key a real active would
 * write) — the fold cannot tell a promotion from a restart, by construction. This is also the whole
 * story for an empty-assigned active with no prior standby: {@link #startAsStandby} from an empty
 * store, then {@link #promote()} — the identical cold-rebuild-then-fold path, no special case.
 *
 * <p><b>Demotion</b> ({@link #demote()}): the reverse. The active {@link Task} releases its own
 * driving resources via {@link Task#closeKeepingStores()} (not {@link Task#close()} — the store
 * must stay open), and a fresh {@link ChangelogApplier} starts tailing from whatever changelog
 * position is already durably persisted in that same store.
 *
 * <p><b>Follow-up not built here:</b> wiring a real production {@link Task} (e.g. the analytics
 * Stage 1/2 tasks) into this lifecycle requires two small, mechanical additions those tasks don't
 * have yet — a constructor variant that wraps an externally-owned {@link StateStoreProvider}
 * instead of opening its own, and a {@link Task#closeKeepingStores()} override that skips closing
 * it. Only fakes exercise this class today (see its test); the runtime-level wiring into {@code
 * SourceLoop}/ {@code StreamRuntime} that would dispatch a real rebalance role flip through this
 * controller is also not done in this pass.
 *
 * @param <CF> the caller's column-family enum
 * @param <R> the decoded record type the active {@link Task} consumes
 */
public final class PartitionRoleController<
        CF extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily, R>
    implements AutoCloseable {

  /** Which driver currently runs against the store. */
  public enum Role {
    ACTIVE,
    STANDBY
  }

  private final StateStoreProvider<CF> provider;
  private final Function<StateStoreProvider<CF>, Task<R>> taskFactory;
  private final Function<StateStoreProvider<CF>, ChangelogApplier<CF>> applierFactory;

  private Task<R> task;
  private ChangelogApplier<CF> applier;

  private PartitionRoleController(
      final StateStoreProvider<CF> provider,
      final Function<StateStoreProvider<CF>, Task<R>> taskFactory,
      final Function<StateStoreProvider<CF>, ChangelogApplier<CF>> applierFactory) {
    this.provider = provider;
    this.taskFactory = taskFactory;
    this.applierFactory = applierFactory;
  }

  /**
   * Starts in the STANDBY role against {@code provider} — an empty provider warms from the
   * changelog start, an intact one resumes from its persisted position (both are the applier's own
   * {@link ChangelogApplier} construction concern, not this controller's).
   */
  public static <CF extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily, R>
      PartitionRoleController<CF, R> startAsStandby(
          final StateStoreProvider<CF> provider,
          final Function<StateStoreProvider<CF>, Task<R>> taskFactory,
          final Function<StateStoreProvider<CF>, ChangelogApplier<CF>> applierFactory) {
    final var controller =
        new PartitionRoleController<CF, R>(provider, taskFactory, applierFactory);
    controller.applier = applierFactory.apply(provider);
    return controller;
  }

  /** Starts in the ACTIVE role against {@code provider} (the ordinary startup path). */
  public static <CF extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily, R>
      PartitionRoleController<CF, R> startAsActive(
          final StateStoreProvider<CF> provider,
          final Function<StateStoreProvider<CF>, Task<R>> taskFactory,
          final Function<StateStoreProvider<CF>, ChangelogApplier<CF>> applierFactory) {
    final var controller =
        new PartitionRoleController<CF, R>(provider, taskFactory, applierFactory);
    controller.task = taskFactory.apply(provider);
    controller.task.init();
    return controller;
  }

  public Role role() {
    return task != null ? Role.ACTIVE : Role.STANDBY;
  }

  /**
   * Polls the changelog once while in the STANDBY role; see {@link ChangelogApplier#pollOnce()}.
   */
  public int pollStandby() {
    requireStandby();
    return applier.pollOnce();
  }

  /** The standby's reported readiness; see {@link ChangelogApplier#readiness()}. */
  public long standbyReadiness() {
    requireStandby();
    return applier.readiness();
  }

  /** The active fold, for the runtime to drive with records/cuts. */
  public Task<R> activeTask() {
    requireActive();
    return task;
  }

  /**
   * Promotes STANDBY -&gt; ACTIVE. See the class javadoc for the exact sequence and why the fold
   * cannot distinguish this from a restart.
   *
   * @return the fold's {@link Task#restore()} baseline — the offset the runtime resumes the source
   *     from (source offset + 1), exactly as it would after any restart
   */
  public long promote() {
    requireStandby();
    applier.drainToEnd();
    applier.close();
    applier = null;
    task = taskFactory.apply(provider);
    task.init();
    return task.restore();
  }

  /** Demotes ACTIVE -&gt; STANDBY. See the class javadoc for the exact sequence. */
  public void demote() {
    requireActive();
    task.closeKeepingStores();
    task = null;
    applier = applierFactory.apply(provider);
  }

  /**
   * Tears down whichever driver is active and closes the store — a true revocation, not a role
   * flip.
   */
  @Override
  public void close() throws Exception {
    if (task != null) {
      task.close();
    }
    if (applier != null) {
      applier.close();
    }
    provider.close();
  }

  private void requireStandby() {
    if (applier == null) {
      throw new IllegalStateException("Partition is not in the STANDBY role");
    }
  }

  private void requireActive() {
    if (task == null) {
      throw new IllegalStateException("Partition is not in the ACTIVE role");
    }
  }
}
