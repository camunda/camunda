/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartProcessInstanceDedupState;
import io.camunda.zeebe.engine.state.mutable.MutableMultiInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.time.Duration;
import java.time.InstantSource;
import java.util.function.Supplier;

/**
 * Extends {@link ProcessInstanceElementTerminatedV2Applier} with the cross-partition message-start
 * dedup tombstone transition on root-PI termination. V2 is preserved unchanged to keep replay of
 * pre-V3 streams deterministic; V3 is registered alongside V2 and is the version used for new
 * events. See {@link ProcessInstanceElementCompletedV3Applier} for the implementation-note caveat
 * on the composition pattern.
 */
final class ProcessInstanceElementTerminatedV3Applier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final ProcessInstanceElementTerminatedV2Applier v2;
  private final MutableMessageStartProcessInstanceDedupState dedupState;
  private final InstantSource clock;
  private final Supplier<Duration> tombstoneWindow;

  ProcessInstanceElementTerminatedV3Applier(
      final MutableElementInstanceState elementInstanceState,
      final MutableEventScopeInstanceState eventScopeInstanceState,
      final MutableMultiInstanceState multiInstanceState,
      final BufferedStartMessageEventStateApplier bufferedStartMessageEventStateApplier,
      final MutableMessageStartProcessInstanceDedupState dedupState,
      final InstantSource clock,
      final Supplier<Duration> tombstoneWindow) {
    v2 =
        new ProcessInstanceElementTerminatedV2Applier(
            elementInstanceState,
            eventScopeInstanceState,
            multiInstanceState,
            bufferedStartMessageEventStateApplier);
    this.dedupState = dedupState;
    this.clock = clock;
    this.tombstoneWindow = tombstoneWindow;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceRecord value) {
    v2.applyState(key, value);
    tombstoneCrossPartitionDedupEntryIfRootProcess(value);
  }

  private void tombstoneCrossPartitionDedupEntryIfRootProcess(final ProcessInstanceRecord value) {
    if (value.getBpmnElementType() != BpmnElementType.PROCESS) {
      return;
    }
    if (value.hasParentProcessInstance()) {
      return;
    }
    final long deadline = clock.millis() + tombstoneWindow.get().toMillis();
    dedupState.tombstoneByProcessInstanceKey(value.getProcessInstanceKey(), deadline);
  }
}
