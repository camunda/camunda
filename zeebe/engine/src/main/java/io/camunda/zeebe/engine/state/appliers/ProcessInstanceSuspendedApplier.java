/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;

/**
 * Stub applier for {@link ProcessInstanceIntent#SUSPENDED}.
 *
 * <p>TODO(#57517): mark the process instance as suspended once {@code SuspensionState} exists.
 *
 * <p>TODO(#57518): this applier's real implementation (including buffered-command handling) lands
 * here.
 */
final class ProcessInstanceSuspendedApplier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  @Override
  public void applyState(final long key, final ProcessInstanceRecord value) {}
}
