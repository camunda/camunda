/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.el.ContextValue;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@link MappingResultBuilder} for input mappings: a mapping to a nested target descends into (or
 * creates) intermediate objects and never merges into the accumulated document; a mapping whose
 * target collides structurally with an earlier one (a value where an object is needed, or vice
 * versa) replaces the earlier entry — last-wins. A <em>read</em> of a name a nested target built is
 * layered over the value that name resolves to in the element's scope chain, so keys no mapping
 * defined fall through instead of disappearing. A context that replaced a value an earlier mapping
 * assigned to the whole name shadows it totally and stops the fall-through for its whole subtree.
 */
@NullMarked
public final class InputMappingResultBuilder extends MappingResultBuilder {

  /**
   * Insertion-ordered deliberately. This order becomes the order the VARIABLE records are written
   * in when the document is merged into the scope, and the key order inside the MsgPack bytes of a
   * stored nested value. Both must come out identical on every replica, so it cannot be left to
   * {@link java.util.HashMap}'s iteration order.
   */
  private final Map<String, Entry> entries = new LinkedHashMap<>();

  /**
   * Resolves a top-level variable name to the value the element's scope chain gives it — the value
   * a nested target partially shadows.
   */
  private final Function<String, @Nullable DirectBuffer> scopeValueResolver;

  /**
   * @param scopeValueResolver resolves a top-level variable name to the value the scope chain gives
   *     it, or {@code null} when there is none
   */
  public InputMappingResultBuilder(
      final Function<String, @Nullable DirectBuffer> scopeValueResolver) {
    this.scopeValueResolver = scopeValueResolver;
  }

  @Override
  public void put(final List<String> targetPath, final ContextValue value) {
    Map<String, Entry> current = entries;
    for (int i = 0; i < targetPath.size() - 1; i++) {
      current = descendInto(current, targetPath.get(i)).entries();
    }
    current.put(targetPath.getLast(), new Entry.Mapped(copyIfMsgPack(value)));
  }

  /**
   * Returns the accumulated value of the given top-level variable, or {@code null} if no mapping
   * has produced it yet. A partial context is resolved on lookup, layered over the value its name
   * resolves to in the scope chain, unless an earlier mapping assigned that name whole.
   */
  @Override
  public @Nullable ContextValue getVariable(final String name) {
    final var entry = entries.get(name);
    if (entry == null) {
      return null;
    } else if (entry instanceof Entry.Mapped(final var result)) {
      return result;
    } else {
      final var partial = (Entry.Partial) entry;
      final var scopeValue = partial.replacedEarlierValue() ? null : scopeValueResolver.apply(name);
      return resolve(partial.entries(), scopeValue);
    }
  }

  @Override
  protected ContextValue.Structure snapshot() {
    return resolve(entries, null);
  }

  /**
   * Descends into the partial context at the given key, creating a fresh (never seeded) one if the
   * current entry is absent or a mapped value. A mapped value means an earlier mapping already
   * assigned this whole path, so the fresh context is marked as having replaced it and never falls
   * through to it on read.
   */
  private Entry.Partial descendInto(final Map<String, Entry> parent, final String key) {
    final var entry = parent.get(key);
    if (entry instanceof final Entry.Partial partial) {
      return partial;
    }
    final var fresh = new Entry.Partial(new LinkedHashMap<>(), entry != null);
    parent.put(key, fresh);
    return fresh;
  }

  /**
   * Resolves what the accumulated entries mean right now: the names the mappings defined, layered
   * over the properties of the value they fall through to. A partial context that replaced a value
   * an earlier mapping assigned shadows that value totally and stops the fall-through for its whole
   * subtree. Passing {@code null} skips the layering entirely — used by {@link #snapshot()}, where
   * the accumulated document must hold only what was mapped.
   *
   * <p>The copy is deep, and it has to be: a later mapping may still write into a partial context,
   * and a value an earlier mapping already read must not see that write. It is iterative for the
   * same reason {@link MappingResultBuilder}'s writer is — a {@code zeebe:input} target path can
   * have an unbounded number of '.'-separated segments (ZeebeExpressionValidator's path pattern
   * doesn't cap it), and plain recursion here previously let a deeply-nested target throw an
   * uncaught StackOverflowError before NestingDepthValidator ever got a chance to reject the
   * document gracefully.
   *
   * @param accumulated what the mappings put under the name being resolved
   * @param valueFromState the whole variable this name resolves to in the element's scope chain,
   *     read from variable state, or {@code null} when there is nothing to fall through to. Being
   *     persisted state it is always MessagePack and carries no FEEL type — this is the boundary
   *     that type retention across mappings deliberately stops at.
   */
  private static ContextValue.Structure resolve(
      final Map<String, Entry> accumulated, final @Nullable DirectBuffer valueFromState) {
    final Map<String, ContextValue> resolved = new LinkedHashMap<>();
    final Deque<PendingContext> pending = new ArrayDeque<>();
    pending.push(new PendingContext(accumulated, valueFromState, resolved));

    while (!pending.isEmpty()) {
      final var next = pending.pop();
      final var scopeProperties = propertiesOf(next.scopeValue());
      // the scope's properties first: a name the mappings also defined then overwrites one in
      // place, keeping the position the scope gave it
      scopeProperties.forEach(
          (name, value) -> next.into().put(name, new ContextValue.MsgPack(value)));

      for (final var accumulatedEntry : next.mapped().entrySet()) {
        final var name = accumulatedEntry.getKey();
        switch (accumulatedEntry.getValue()) {
          case Entry.Mapped(final var result) -> next.into().put(name, result);
          case final Entry.Partial partial -> {
            final Map<String, ContextValue> nestedInto = new LinkedHashMap<>();
            next.into().put(name, new ContextValue.Structure(nestedInto));
            pending.push(
                new PendingContext(
                    partial.entries(),
                    partial.replacedEarlierValue() ? null : scopeProperties.get(name),
                    nestedInto));
          }
        }
      }
    }
    return new ContextValue.Structure(resolved);
  }

  /**
   * One entry of the accumulated tree. Every entry here came from a mapping — unlike output
   * mappings, input mappings never seed anything from the scope value.
   */
  private sealed interface Entry {

    /**
     * A mapping's result, assigned to this whole name. Stored as it came out of the evaluation and
     * never descended into, so it may itself be a context, a list or a scalar — {@code x.a <- {b:
     * 1}} leaves a {@code Mapped} holding a context.
     *
     * @param result the mapping's evaluation result
     */
    record Mapped(ContextValue result) implements Entry {}

    /**
     * A context holding only the keys mappings actually defined, built by one or more dotted
     * targets. Partial is the point: a read layers it over the value the name resolves to in the
     * scope chain, so keys no mapping defined fall through instead of disappearing.
     *
     * @param entries the context's own entries: a nested {@link Partial} or a {@link Mapped} result
     * @param replacedEarlierValue whether this context replaced a value an earlier mapping assigned
     *     rather than being created from nothing. Such a context never falls through on a read,
     *     because that earlier assignment already replaced whatever the scope gave the name, and
     *     re-creating the context is a fresh start (structural last-wins) rather than a merge into
     *     what it dropped.
     */
    record Partial(Map<String, Entry> entries, boolean replacedEarlierValue) implements Entry {}
  }

  /**
   * One context still to be resolved: what the mappings put there, the scope value it layers over
   * ({@code null} for none), and the map its resolved entries are written into.
   *
   * <p>{@code into} is installed in its parent before the context is queued, so a parent's name
   * order is fixed when a nested context is <em>discovered</em>, not when it is filled — which is
   * why draining the stack in any order still produces the same document. Each is queued once and
   * consumed once.
   *
   * @param mapped what the mappings accumulated under this context
   * @param scopeValue what this context layers over, or {@code null} to skip layering. At the root
   *     this is the whole variable read from state; below it, the property nested inside that
   *     variable, sliced out of its MessagePack rather than read again.
   * @param into the destination map, already installed in its parent
   */
  private record PendingContext(
      Map<String, Entry> mapped,
      @Nullable DirectBuffer scopeValue,
      Map<String, ContextValue> into) {}
}
