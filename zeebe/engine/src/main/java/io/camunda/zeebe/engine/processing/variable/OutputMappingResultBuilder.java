/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.el.ContextValue;
import io.camunda.zeebe.msgpack.spec.MsgPackCodes;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@link MappingResultBuilder} for output mappings: a nested target merges with the existing scope
 * variable at every path level while accumulating — a context that is absent or holds a plain value
 * is seeded from the current scope value at that path. A scope value that is not a context cannot
 * be merged into, so the entry is poisoned to null, matching FEEL's {@code context
 * merge(<non-context>, {...})} behavior. Reads are not layered — the merge is already in the
 * document.
 */
@NullMarked
public final class OutputMappingResultBuilder extends MappingResultBuilder {

  /**
   * The value a poisoned entry evaluates to: null, matching FEEL's {@code context
   * merge(<non-context>, {...})}. Pre-serialized as a msgpack nil so the shared writer needs no
   * knowledge of poisoning — once an {@link Entry.Poisoned} entry is turned into a {@link
   * ContextValue} for a read, it is written like any other MsgPack value.
   */
  private static final ContextValue NIL =
      new ContextValue.MsgPack(new UnsafeBuffer(new byte[] {MsgPackCodes.NIL}));

  private final Map<String, Entry> entries = new LinkedHashMap<>();

  /** Resolves a target-path prefix to the scope value to seed a context from. */
  private final Function<List<String>, @Nullable DirectBuffer> scopeValueResolver;

  /**
   * @param scopeValueResolver resolves a target-path prefix to the current scope value at that
   *     path, or {@code null} when there is none; invoked only when a context must be (re)created
   */
  public OutputMappingResultBuilder(
      final Function<List<String>, @Nullable DirectBuffer> scopeValueResolver) {
    this.scopeValueResolver = scopeValueResolver;
  }

  @Override
  public void put(final List<String> targetPath, final ContextValue value) {
    Map<String, Entry> current = entries;
    for (int i = 0; i < targetPath.size() - 1; i++) {
      final var next = getOrSeedContext(current, targetPath.subList(0, i + 1));
      if (next == null) {
        return; // entry is poisoned: the mapped value is discarded, the entry stays null
      }
      current = next.entries();
    }
    current.put(targetPath.getLast(), new Entry.Value(copyIfMsgPack(value)));
  }

  /**
   * Returns the accumulated value of the given top-level variable, or {@code null} if no mapping
   * has produced it yet. A nested structure is materialized on lookup — output reads are not
   * layered, the merge is already in the document. A poisoned top-level entry returns {@link #NIL}
   * (not Java {@code null}, which would let the caller fall back to the scope lookup instead of
   * seeing the poisoned null).
   */
  @Override
  public @Nullable ContextValue getVariable(final String name) {
    final var entry = entries.get(name);
    if (entry == null) {
      return null;
    } else if (entry instanceof Entry.Value(final var value)) {
      return value;
    } else if (entry instanceof Entry.Poisoned) {
      return NIL;
    } else {
      final var context = (Entry.Context) entry;
      return materialize(context.entries());
    }
  }

  @Override
  protected ContextValue.Structure snapshot() {
    return materialize(entries);
  }

  /**
   * Returns the context at the given path prefix, creating it if the current entry is absent or a
   * plain value. A newly created context is seeded with the top-level entries of the existing scope
   * value at that path (its entries stay opaque {@link ContextValue.MsgPack} values); a scope value
   * that is not a context poisons the entry instead. Returns {@code null} when the entry is (or
   * becomes) poisoned.
   */
  private Entry.@Nullable Context getOrSeedContext(
      final Map<String, Entry> parent, final List<String> pathPrefix) {
    final var key = pathPrefix.getLast();
    final var entry = parent.get(key);
    if (entry instanceof Entry.Poisoned) {
      return null;
    }
    if (entry instanceof final Entry.Context context) {
      return context;
    }
    // Absent, or a plain value from an earlier mapping. Either way the context is (re)created from
    // scratch, seeded from the SCOPE value rather than whatever that earlier mapping assigned.
    final var scopeValue = scopeValueResolver.apply(pathPrefix);
    if (scopeValue == null || isNil(scopeValue)) {
      final var fresh = new Entry.Context(new LinkedHashMap<>());
      parent.put(key, fresh);
      return fresh;
    }
    if (!MsgPackCodes.isMap(scopeValue.getByte(0))) {
      parent.put(key, new Entry.Poisoned());
      return null;
    }
    final var seeded = new Entry.Context(seedFrom(scopeValue));
    parent.put(key, seeded);
    return seeded;
  }

  /** The top level of a msgpack map as accumulated entries, for seeding a context. */
  private static Map<String, Entry> seedFrom(final DirectBuffer scopeValue) {
    final Map<String, Entry> seeded = new LinkedHashMap<>();
    propertiesOf(scopeValue)
        .forEach((key, value) -> seeded.put(key, new Entry.Value(new ContextValue.MsgPack(value))));
    return seeded;
  }

  private static boolean isNil(final DirectBuffer value) {
    return value.capacity() == 1 && value.getByte(0) == MsgPackCodes.NIL;
  }

  /**
   * Materializes a context (and its nested contexts) as an immutable snapshot. Not layered — the
   * merge with the scope value already happened while accumulating, so reading it back is a plain
   * copy. It is iterative for the same reason {@link MappingResultBuilder}'s writer is — a {@code
   * zeebe:output} target path can have an unbounded number of '.'-separated segments
   * (ZeebeExpressionValidator's path pattern doesn't cap it), and plain recursion here previously
   * let a deeply-nested target throw an uncaught StackOverflowError before NestingDepthValidator
   * ever got a chance to reject the document gracefully.
   */
  private static ContextValue.Structure materialize(final Map<String, Entry> rootEntries) {
    final Map<String, ContextValue> root = new LinkedHashMap<>();
    final Deque<ContextCopy> pending = new ArrayDeque<>();
    pending.push(new ContextCopy(rootEntries, root));

    while (!pending.isEmpty()) {
      final var copy = pending.pop();
      for (final var entry : copy.from().entrySet()) {
        final var key = entry.getKey();
        switch (entry.getValue()) {
          case final Entry.Context context -> {
            final Map<String, ContextValue> nested = new LinkedHashMap<>();
            copy.into().put(key, new ContextValue.Structure(nested));
            pending.push(new ContextCopy(context.entries(), nested));
          }
          case Entry.Value(final var value) -> copy.into().put(key, value);
          case Entry.Poisoned() -> copy.into().put(key, NIL);
        }
      }
    }
    return new ContextValue.Structure(root);
  }

  /**
   * One entry of the accumulated tree: either a value at a whole name, a nested context built by
   * one or more dotted targets and merged with any scope value it was seeded from, or an entry
   * whose scope value was not a context and so could not be merged into.
   */
  private sealed interface Entry {
    record Value(ContextValue value) implements Entry {}

    /**
     * No flag recording whether this context replaced a plain value: unlike input mappings, output
     * never falls through to a shadowed value on read, so nothing would ever read it.
     *
     * @param entries the context's own entries: a nested {@link Context} or a {@link Value}
     */
    record Context(Map<String, Entry> entries) implements Entry {}

    /** An entry whose scope value was not a context — see {@link #NIL}. */
    record Poisoned() implements Entry {}
  }

  /**
   * One context still to be copied: the accumulated entries to read, and the map to write them
   * into. Nothing is layered here — output merges the scope value in while accumulating, so a read
   * is a plain copy.
   *
   * <p>{@code into} is installed in its parent before the copy is queued, so a parent's key order
   * is fixed when a nested context is <em>discovered</em>, not when it is filled — which is why
   * draining the stack in any order still produces the same document. Each copy is queued once and
   * consumed once.
   *
   * @param from the accumulated entries to copy
   * @param into the destination map, already installed in its parent
   */
  private record ContextCopy(Map<String, Entry> from, Map<String, ContextValue> into) {}
}
