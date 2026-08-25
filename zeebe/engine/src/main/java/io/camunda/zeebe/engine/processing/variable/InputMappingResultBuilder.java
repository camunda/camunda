/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.util.buffer.BufferUtil;
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
 * defined fall through instead of disappearing. A level an earlier mapping assigned whole shadows
 * that value totally and stops the fall-through for its whole subtree.
 */
@NullMarked
public final class InputMappingResultBuilder implements MappingResultBuilder {

  /**
   * Values are either a MsgPack {@link DirectBuffer} — a value assigned to that whole name — or a
   * {@link MappingLevels.Level} built by one or more dotted targets. Never a poisoned level: only
   * output mappings can produce one.
   *
   * <p>Insertion-ordered deliberately. This order becomes the order the VARIABLE records are
   * written in when the document is merged into the scope, and the key order inside the MsgPack
   * bytes of a stored nested value. Both must come out identical on every replica, so it cannot be
   * left to {@link java.util.HashMap}'s iteration order.
   */
  private final Map<String, Object> entries = new LinkedHashMap<>();

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

  /**
   * Puts a copy of the given MsgPack value at the nested target path. The value buffer is copied
   * because evaluation result buffers are transient and may be reused by the next evaluation.
   */
  @Override
  public void put(final List<String> targetPath, final DirectBuffer value) {
    Map<String, Object> current = entries;
    for (int i = 0; i < targetPath.size() - 1; i++) {
      current = getOrCreate(current, targetPath.get(i)).children();
    }
    current.put(targetPath.getLast(), BufferUtil.cloneBuffer(value));
  }

  /**
   * Returns the accumulated value of the given top-level variable, or {@code null} if no mapping
   * has produced it yet. A nested structure is serialized on lookup, layered over the value its
   * name resolves to in the scope chain, unless an earlier mapping assigned that name whole.
   */
  @Override
  public @Nullable DirectBuffer get(final String name) {
    final var entry = entries.get(name);
    if (entry == null) {
      return null;
    } else if (entry instanceof final DirectBuffer value) {
      return value;
    } else {
      final var level = (MappingLevels.Level) entry;
      final var shadowed = level.replacedExistingValue() ? null : scopeValueResolver.apply(name);
      return MappingLevels.serialize(level.children(), shadowed);
    }
  }

  /** Returns all accumulated results as a single MsgPack document (a map). */
  @Override
  public DirectBuffer toDocument() {
    return MappingLevels.serialize(entries, null);
  }

  /**
   * Returns the level at the given key, creating a fresh (never seeded) one if the current entry is
   * absent or a plain value. A plain value means an earlier mapping already assigned this whole
   * path, so the fresh level is marked as having replaced it and never falls through to it on read.
   */
  private MappingLevels.Level getOrCreate(final Map<String, Object> parent, final String key) {
    final var entry = parent.get(key);
    if (entry instanceof final MappingLevels.Level level) {
      return level;
    }
    final var fresh = MappingLevels.Level.fresh(entry != null);
    parent.put(key, fresh);
    return fresh;
  }
}
