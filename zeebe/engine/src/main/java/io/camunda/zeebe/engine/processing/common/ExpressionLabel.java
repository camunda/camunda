/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.common;

import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

/**
 * Describes the business role of an expression and, optionally, the specific element it belongs to,
 * so that {@link ExpressionProcessor} can build failure messages that identify what failed and what
 * it targets instead of the generic "the expression" wording.
 *
 * <p>For example, a label with {@code kind} {@code "condition expression"} and {@code target}
 * {@code "sequence flow 's2'"} turns a generic failure message such as:
 *
 * <pre>Expected result of the expression 'foo &gt; 10' to be 'BOOLEAN', but was 'NULL'.</pre>
 *
 * into:
 *
 * <pre>
 * Expected result of the condition expression 'foo &gt; 10' of sequence flow 's2' to be
 * 'BOOLEAN', but was 'NULL'.
 * </pre>
 *
 * <p>Use {@link #NONE} when no additional context should be added to the failure message, which
 * preserves the original generic wording. A label may have a {@code kind} without a {@code target}
 * (e.g. when the target is not relevant or not known at the call site).
 *
 * @param kind a short, human-readable description of what the expression represents (e.g. {@code
 *     "condition expression"}), or {@code null} if not labeled
 * @param target a short, human-readable description of the specific element the expression belongs
 *     to (e.g. {@code "sequence flow 's2'"}), or {@code null} if not applicable
 */
public record ExpressionLabel(String kind, String target) {

  /** A label that adds no additional context to the failure message. */
  public static final ExpressionLabel NONE = new ExpressionLabel(null, null);

  /**
   * Labels an expression as the condition expression of a sequence flow, e.g. for the gateway
   * failure message "Expected result of the condition expression '...' of sequence flow 's2' to be
   * 'BOOLEAN', but was '...'.".
   *
   * @param sequenceFlowId the id of the sequence flow the condition expression belongs to
   * @return a label identifying the expression as the given sequence flow's condition expression
   */
  public static ExpressionLabel conditionExpression(final DirectBuffer sequenceFlowId) {
    return new ExpressionLabel(
        "condition expression",
        "sequence flow '%s'".formatted(BufferUtil.bufferAsString(sequenceFlowId)));
  }

  /**
   * @return {@code true} if this label has a {@code kind} and should be used to add context to a
   *     failure message, {@code false} if it is {@link #NONE}
   */
  boolean hasKind() {
    return kind != null;
  }

  /**
   * @return the target description prefixed with a leading space (e.g. {@code " of sequence flow
   *     's2'"}), or an empty string if no target is present
   */
  String describeTarget() {
    return target == null ? "" : " of %s".formatted(target);
  }
}
