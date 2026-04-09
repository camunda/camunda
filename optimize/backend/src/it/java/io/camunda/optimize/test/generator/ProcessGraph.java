/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import static io.camunda.zeebe.protocol.record.value.BpmnElementType.EVENT_BASED_GATEWAY;
import static io.camunda.zeebe.protocol.record.value.BpmnElementType.EXCLUSIVE_GATEWAY;
import static io.camunda.zeebe.protocol.record.value.BpmnElementType.SUB_PROCESS;

import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An immutable, pre-computed representation of one BPMN scope (process root or sub-process body).
 *
 * <p>Built once per process ID by {@link BpmnFlowParser#parse} and reused across all generated
 * instances. Each call to {@link #walk} performs a cheap in-memory random walk over the graph,
 * returning a concrete execution path as an ordered list of {@link FlowNode}s.
 *
 * <p>At {@code EXCLUSIVE_GATEWAY} and {@code EVENT_BASED_GATEWAY} nodes a single outgoing branch is
 * chosen at random, replacing what were previously hardcoded layout variants.
 *
 * <p>Sub-processes are expanded in-place: the sub-process node is emitted at its natural position,
 * immediately followed by its children (which carry the sub-process {@code index} as their {@code
 * parentIndex}). This satisfies the consecutive-child contract required by {@link
 * FlowNodeEmitter#flowNodeOps}.
 */
final class ProcessGraph {

  private final String startId;
  private final Map<String, BpmnElementType> types;
  private final Map<String, List<String>> successors;
  private final Map<String, ProcessGraph> subScopes;

  ProcessGraph(
      final String startId,
      final Map<String, BpmnElementType> types,
      final Map<String, List<String>> successors,
      final Map<String, ProcessGraph> subScopes) {
    this.startId = startId;
    this.types = types;
    this.successors = successors;
    this.subScopes = subScopes;
  }

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Performs a random walk from the start event to an end event and returns the visited nodes as an
   * ordered, flat list. Sub-process children appear consecutively after their parent node.
   *
   * <p>Only {@link Random} is consumed per call — all graph data is read-only and shared.
   */
  List<FlowNode> walk(final Random rng) {
    final WalkState walkState = WalkState.create(rng);
    walkFrom(startId, -1, walkState);
    return walkState.result();
  }

  // ── Walk helpers ──────────────────────────────────────────────────────────

  private void walkFrom(final String id, final int parentIndex, final WalkState walkState) {
    if (walkState.visited().contains(id) || !types.containsKey(id)) {
      return;
    }
    walkState.visited().add(id);

    final BpmnElementType type = types.get(id);
    final int index = walkState.counter().getAndIncrement();
    final FlowNode node = new FlowNode(id, type, index, parentIndex);
    walkState.result().add(node);

    expandSubProcess(id, type, index, walkState);

    final List<String> next = successors.getOrDefault(id, List.of());
    if (!next.isEmpty()) {
      final String chosen = chooseSuccessor(type, next, walkState.rng());
      walkFrom(chosen, parentIndex, walkState);
    }
  }

  /**
   * If {@code type} is {@link io.camunda.zeebe.protocol.record.value.BpmnElementType#SUB_PROCESS},
   * walks the sub-scope and appends its nodes into the shared walk state consecutively after the
   * parent. Does nothing for all other element types.
   */
  private void expandSubProcess(
      final String id,
      final BpmnElementType type,
      final int parentIndex,
      final WalkState walkState) {
    if (type != SUB_PROCESS) {
      return;
    }
    final ProcessGraph sub = subScopes.get(id);
    final WalkState subState = WalkState.forSubScope(walkState);
    sub.walkFrom(sub.startId, parentIndex, subState);
  }

  /**
   * Returns the next node ID to visit. Forking gateways pick a branch at random; all other elements
   * follow their single outgoing sequence flow.
   */
  private static String chooseSuccessor(
      final BpmnElementType type, final List<String> options, final Random rng) {
    return isForking(type) ? pickRandom(options, rng) : options.getFirst();
  }

  /** Returns a uniformly random element from {@code options}. */
  private static String pickRandom(final List<String> options, final Random rng) {
    final int randomIndex = rng.nextInt(options.size());
    return options.get(randomIndex);
  }

  private static boolean isForking(final BpmnElementType type) {
    return type == EXCLUSIVE_GATEWAY || type == EVENT_BASED_GATEWAY;
  }

  // ── Private parameter objects ─────────────────────────────────────────────

  /**
   * Mutable accumulator passed through the recursive walk.
   *
   * <p>The record holds references to mutable collections; the record itself is not replaced.
   * Sub-process scopes share the same {@code result} and {@code counter} but get a fresh {@code
   * visited} set so their own nodes are not considered already-visited.
   */
  private record WalkState(
      List<FlowNode> result, AtomicInteger counter, HashSet<String> visited, Random rng) {

    /** Creates a fresh walk state for a new top-level walk. */
    static WalkState create(final Random rng) {
      final List<FlowNode> result = new ArrayList<>();
      final AtomicInteger counter = new AtomicInteger();
      final HashSet<String> visited = new HashSet<>();
      return new WalkState(result, counter, visited, rng);
    }

    /**
     * Creates a sub-scope walk state that shares the parent's {@code result} list and {@code
     * counter} but starts with a fresh {@code visited} set.
     */
    static WalkState forSubScope(final WalkState parent) {
      return new WalkState(parent.result(), parent.counter(), new HashSet<>(), parent.rng());
    }
  }
}
