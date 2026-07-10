/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.processor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A wired {@link Processor} in the graph: it owns the processor and the edges to its children,
 * routing forwarded values on to them. Types line up within a node — a child of a {@code
 * Processor<In, Out>} consumes {@code Out} — so record flow is type-safe once the graph is built;
 * only the untyped {@code name -> node} wiring in the builder needs an unchecked connect.
 *
 * @param <In> what this node consumes
 * @param <Out> what this node forwards
 */
final class ProcessorNode<In, Out> {

  private final String name;
  private final Processor<In, Out> processor;
  private final Map<String, ProcessorNode<Out, ?>> children = new LinkedHashMap<>();

  ProcessorNode(final String name, final Processor<In, Out> processor) {
    this.name = name;
    this.processor = processor;
  }

  String name() {
    return name;
  }

  void addChild(final ProcessorNode<Out, ?> child) {
    children.put(child.name(), child);
  }

  void init(final ProcessorContext<Out> context) {
    processor.init(context);
  }

  /** Feeds one record into this node's processor. */
  void deliver(final In record) {
    processor.process(record);
  }

  /** Broadcasts a forwarded value to every child. */
  void forward(final Out value) {
    for (final ProcessorNode<Out, ?> child : children.values()) {
      child.deliver(value);
    }
  }

  /** Routes a forwarded value to a single named child. */
  void forward(final Out value, final String childName) {
    final ProcessorNode<Out, ?> child = children.get(childName);
    if (child == null) {
      throw new IllegalArgumentException(
          "Processor '" + name + "' has no child named '" + childName + "'");
    }
    child.deliver(value);
  }

  void flush() {
    processor.flush();
  }

  boolean needsCheckpoint() {
    return processor.needsCheckpoint();
  }

  void close() {
    processor.close();
  }
}
