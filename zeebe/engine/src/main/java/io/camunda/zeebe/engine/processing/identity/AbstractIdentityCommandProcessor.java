/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.policy.PolicyUpdateBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.jspecify.annotations.NullMarked;

/**
 * A processor of an identity command (tenant, role, group, mapping rule or authorization). Besides
 * a client request, such a command can be part of a policy update, which applies its entries as
 * identity commands; see {@link PolicyUpdateBehavior}.
 *
 * <p>A distributed command never belongs to a policy update on the receiving partition, so {@link
 * #processDistributedCommand} is not affected.
 */
@NullMarked
public abstract class AbstractIdentityCommandProcessor<T extends UnifiedRecordValue>
    implements DistributedTypedRecordProcessor<T> {

  private final PolicyUpdateBehavior policyUpdateBehavior;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  protected AbstractIdentityCommandProcessor(
      final PolicyUpdateBehavior policyUpdateBehavior, final Writers writers) {
    this.policyUpdateBehavior = policyUpdateBehavior;
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  @Override
  public final void processNewCommand(final TypedRecord<T> command) {
    processNewIdentityCommand(command);
  }

  /** Processes a command that is not distributed yet. */
  protected abstract void processNewIdentityCommand(TypedRecord<T> command);
}
