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
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.jspecify.annotations.NullMarked;

/**
 * A processor of an identity command (tenant, role, group, mapping rule or authorization). Besides
 * a client request, such a command can be part of a policy update, which applies its entries as
 * identity commands; see {@link PolicyUpdateBehavior}. A policy update applies completely or fails:
 *
 * <ul>
 *   <li>A command of a policy update that already failed is rejected before {@link
 *       #processNewIdentityCommand} sees it.
 *   <li>{@link #rejectNewCommand} fails the policy update of the command.
 *   <li>{@link #rejectNewCommandAsAlreadyApplied} doesn't: the command asks for what is already the
 *       case, like deleting a role that doesn't exist, so the update can go on.
 *   <li>An unexpected error while processing the command fails the update.
 * </ul>
 *
 * <p>For a command that is not part of a policy update, both reject methods just reject the command
 * and respond to its request.
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
    final var rejection = policyUpdateBehavior.checkUpdateIsOpen(command);
    if (rejection.isPresent()) {
      rejectionWriter.appendRejection(command, rejection.get().type(), rejection.get().reason());
      return;
    }
    processNewIdentityCommand(command);
  }

  /**
   * Processes a command that is not distributed yet. Reject it with {@link #rejectNewCommand} or
   * {@link #rejectNewCommandAsAlreadyApplied}, so that a policy update reacts to the rejection.
   */
  protected abstract void processNewIdentityCommand(TypedRecord<T> command);

  /**
   * Rejects the command and responds to its request. If the command is part of a policy update, the
   * update fails, as the command cannot be applied.
   */
  protected final void rejectNewCommand(
      final TypedRecord<T> command, final RejectionType type, final String reason) {
    policyUpdateBehavior.failOpenUpdate(
        command, "was rejected with %s: %s".formatted(type, reason));
    reject(command, type, reason);
  }

  /**
   * Rejects the command, because what it asks for is already the case, and responds to its request.
   * If the command is part of a policy update, the update goes on; the rejection only warns that
   * the update expected another state.
   */
  protected final void rejectNewCommandAsAlreadyApplied(
      final TypedRecord<T> command, final RejectionType type, final String reason) {
    reject(command, type, reason);
  }

  @Override
  public final ProcessingError tryHandleError(final TypedRecord<T> command, final Throwable error) {
    // the engine rejects the command afterwards; this fails its policy update
    policyUpdateBehavior.failOpenUpdate(command, describe(error));
    return ProcessingError.UNEXPECTED_ERROR;
  }

  /**
   * When the command's results exceed the batch size, the engine's rejection must still fit next to
   * the update's failure, so the failure leaves out the exception's message, which quotes the
   * record that did not fit.
   */
  private static String describe(final Throwable error) {
    if (error instanceof ExceededBatchRecordSizeException) {
      return "exceeded the maximum batch size";
    }
    return "failed with an unexpected error: %s".formatted(error.getMessage());
  }

  private void reject(final TypedRecord<T> command, final RejectionType type, final String reason) {
    rejectionWriter.appendRejection(command, type, reason);
    responseWriter.writeRejectedResponseOnCommand(command, type, reason);
  }
}
