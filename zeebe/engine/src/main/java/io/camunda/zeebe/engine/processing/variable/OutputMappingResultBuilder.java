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
import java.util.ArrayList;
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
 * {@link MappingResultBuilder} for output mappings.
 *
 * <p>{@link #getVariable(String)} answers purely from what mappings explicitly wrote so far in this
 * evaluation pass — a nested target is a plain accumulated structure, never merged with any
 * external value. This is what lets a later mapping's source expression read back an earlier,
 * still-partial nested target without seeing branches that were never mapped (see {@code
 * VariableOutputMappingTransformerTest#shouldNotLeakUntouchedSiblingIntoMergeTargetOrBackReference}).
 *
 * <p>{@link #toDocument()} builds a separate, throwaway tree by replaying the writes in the order
 * they happened, this time merging each nested target with the value already at that path in the
 * scope the result will be merged into: a level that is absent or holds a plain value is seeded
 * from that scope value; a scope value that is not a context poisons the level to null, matching
 * FEEL's {@code context merge(<non-context>, {...})} behavior. Replaying (rather than reusing the
 * live tree) is what keeps this merge from ever seeing a branch that was never written — see <a
 * href="https://github.com/camunda/camunda/issues/35251">#35251</a>.
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

  /**
   * What mappings explicitly wrote, structured by target path. Never merged with any external value
   * — see the class doc.
   */
  private final Map<String, Entry> entries = new LinkedHashMap<>();

  /**
   * Every {@link #put} call, insertion-ordered, replayed by {@link #toDocument()} against {@link
   * #mergeTargetResolver} so the emitted document merges with the scope it is written into instead
   * of whatever this builder's own live tree happens to hold.
   */
  private final List<Map.Entry<List<String>, ContextValue>> writes = new ArrayList<>();

  /**
   * Resolves a target-path prefix to the value at that path in the scope the result is merged into.
   */
  private final Function<List<String>, @Nullable DirectBuffer> mergeTargetResolver;

  /**
   * @param mergeTargetResolver resolves a target-path prefix to the value the scope the result is
   *     merged into gives it, or {@code null} when there is none; invoked only when a level must be
   *     (re)created while replaying writes for {@link #toDocument()}
   */
  public OutputMappingResultBuilder(
      final Function<List<String>, @Nullable DirectBuffer> mergeTargetResolver) {
    this.mergeTargetResolver = mergeTargetResolver;
  }

  @Override
  public void put(final List<String> targetPath, final ContextValue value) {
    final var copy = copyIfMsgPack(value);
    writes.add(Map.entry(targetPath, copy));

    Map<String, Entry> current = entries;
    for (int i = 0; i < targetPath.size() - 1; i++) {
      current = descendInto(current, targetPath.get(i)).entries();
    }
    current.put(targetPath.getLast(), new Entry.Value(copy));
  }

  /**
   * Returns the accumulated value of the given top-level variable, or {@code null} if no mapping
   * has produced it yet — {@code null} tells the caller to fall back to the scope lookup. Never
   * merged with any external value: a nested target is exactly what mappings wrote so far.
   */
  @Override
  public @Nullable ContextValue getVariable(final String name) {
    final var entry = entries.get(name);
    if (entry == null) {
      return null;
    } else if (entry instanceof Entry.Value(final var value)) {
      return value;
    } else {
      final var context = (Entry.Context) entry;
      return materialize(context.entries());
    }
  }

  /**
   * Descends into the nested context at the given key, creating a fresh (never seeded) one if the
   * current entry is absent or a plain value — an earlier mapping that assigned this whole path a
   * plain value is discarded structurally, last-wins, exactly like {@link
   * InputMappingResultBuilder}. Never consults any external scope: that only happens later, when
   * {@link #toDocument()} replays the writes.
   */
  private Entry.Context descendInto(final Map<String, Entry> parent, final String key) {
    final var entry = parent.get(key);
    if (entry instanceof final Entry.Context context) {
      return context;
    }
    final var fresh = new Entry.Context(new LinkedHashMap<>());
    parent.put(key, fresh);
    return fresh;
  }

  @Override
  protected ContextValue.Structure snapshot() {
    final Map<String, Entry> emitted = new LinkedHashMap<>();
    for (final var write : writes) {
      applyToReplay(emitted, write.getKey(), write.getValue());
    }
    return materialize(emitted);
  }

  /**
   * Replays one recorded write into {@code root}, merging nested targets with the merge-target
   * scope.
   */
  private void applyToReplay(
      final Map<String, Entry> root, final List<String> targetPath, final ContextValue value) {
    Map<String, Entry> current = root;
    for (int i = 0; i < targetPath.size() - 1; i++) {
      final var next = getOrSeedContext(current, targetPath.subList(0, i + 1));
      if (next == null) {
        return; // entry is poisoned: the mapped value is discarded, the entry stays null
      }
      current = next.entries();
    }
    current.put(targetPath.getLast(), new Entry.Value(value));
  }

  /**
   * Returns the context at the given path prefix (within the replay tree being built by {@link
   * #snapshot()}), creating it if the current entry is absent or a plain value. A newly created
   * context is seeded with the top-level entries of the merge-target scope value at that path (its
   * entries stay opaque {@link ContextValue.MsgPack} values); a scope value that is not a context
   * poisons the entry instead. Returns {@code null} when the entry is (or becomes) poisoned.
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
    // Absent, or a plain value from an earlier replayed write. Either way the context is
    // (re)created from scratch, seeded from the merge-target scope rather than whatever that
    // earlier write assigned.
    final var scopeValue = mergeTargetResolver.apply(pathPrefix);
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
   * Materializes a context (and its nested contexts) as an immutable snapshot. It is iterative for
   * the same reason {@link MappingResultBuilder}'s writer is — a {@code zeebe:output} target path
   * can have an unbounded number of '.'-separated segments (ZeebeExpressionValidator's path pattern
   * doesn't cap it), and plain recursion here previously let a deeply-nested target throw an
   * uncaught StackOverflowError before NestingDepthValidator ever got a chance to reject the
   * document gracefully.
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
   * One entry of an accumulated tree: either a value at a whole name, a nested context built by one
   * or more dotted targets, or (only in a replay tree — see {@link #getOrSeedContext}) an entry
   * whose merge-target scope value was not a context and so could not be merged into.
   */
  private sealed interface Entry {
    record Value(ContextValue value) implements Entry {}

    /**
     * @param entries the context's own entries: a nested {@link Context} or a {@link Value}
     */
    record Context(Map<String, Entry> entries) implements Entry {}

    /** An entry whose merge-target scope value was not a context — see {@link #NIL}. */
    record Poisoned() implements Entry {}
  }

  /**
   * One context still to be copied: the accumulated entries to read, and the map to write them
   * into.
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
