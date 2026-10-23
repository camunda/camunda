/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import io.camunda.zeebe.protocol.record.value.BpmnElementType;

/**
 * A single BPMN flow element within a {@link ProcessLayout}.
 *
 * <p>{@code index} is a stable per-element numeric identifier used to derive unique Zeebe element
 * instance keys (see {@code NODE_KEY_MULTIPLIER} in {@link ZeebeProcessDataGenerator}).
 *
 * <p>{@code parentIndex} links elements that live inside an embedded sub-process to their
 * containing sub-process element. A value of {@code -1} means the element is a direct child of the
 * process (top-level scope).
 */
record FlowNode(String id, BpmnElementType type, int index, int parentIndex) {

  /** Convenience constructor for top-level elements (parentIndex defaults to {@code -1}). */
  FlowNode(final String id, final BpmnElementType type, final int index) {
    this(id, type, index, -1);
  }
}
