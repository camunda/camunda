/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.msgpack.spec.MsgPackCodes;
import io.camunda.zeebe.util.buffer.BufferUtil;
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
 * variable at every path level while accumulating — a level that is absent or holds a plain value
 * is seeded from the current scope value at that path. A non-context scope value poisons the level
 * to null, matching FEEL's {@code context merge(<non-context>, {...})} behavior. Reads are not
 * layered — the merge is already in the document.
 */
@NullMarked
public final class OutputMappingResultBuilder implements MappingResultBuilder {

  /**
   * A level whose scope value was not a context: it evaluates to null, matching FEEL's {@code
   * context merge(<non-context>, {...})}. Held as a pre-serialized msgpack nil so the shared
   * serializer needs no knowledge of it — it is written like any other value buffer. Compared by
   * identity, which is what distinguishes a poisoned level from a mapping that assigned null (a
   * distinct, cloned buffer).
   */
  private static final DirectBuffer POISONED = new UnsafeBuffer(new byte[] {MsgPackCodes.NIL});

  private final Map<String, Object> entries = new LinkedHashMap<>();

  /** Resolves a target-path prefix to the scope value to seed a level from. */
  private final Function<List<String>, @Nullable DirectBuffer> scopeValueResolver;

  /**
   * @param scopeValueResolver resolves a target-path prefix to the current scope value at that
   *     path, or {@code null} when there is none; invoked only when a level must be (re)created
   */
  public OutputMappingResultBuilder(
      final Function<List<String>, @Nullable DirectBuffer> scopeValueResolver) {
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
      final var next = getOrSeedNested(current, targetPath.subList(0, i + 1));
      if (next == null) {
        return; // level is poisoned: the mapped value is discarded, the level stays null
      }
      current = next.children();
    }
    current.put(targetPath.getLast(), BufferUtil.cloneBuffer(value));
  }

  /**
   * Returns the accumulated value of the given top-level variable, or {@code null} if no mapping
   * has produced it yet. A nested structure is serialized on lookup. A poisoned top-level entry
   * returns a NIL buffer (not Java {@code null}, which would let the caller fall back to the scope
   * lookup instead of seeing the poisoned null).
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
      return MappingLevels.serialize(level.children(), null);
    }
  }

  /** Returns all accumulated results as a single MsgPack document (a map). */
  @Override
  public DirectBuffer toDocument() {
    return MappingLevels.serialize(entries, null);
  }

  /**
   * Returns the level at the given path prefix, creating it if the current entry is absent or a
   * plain value. A newly created level is seeded with the top-level entries of the existing scope
   * value at that path (children stay opaque buffers); a non-context scope value poisons the level
   * instead. Returns {@code null} when the level is (or becomes) poisoned.
   */
  private MappingLevels.@Nullable Level getOrSeedNested(
      final Map<String, Object> parent, final List<String> pathPrefix) {
    final var key = pathPrefix.getLast();
    final var entry = parent.get(key);
    if (entry == POISONED) {
      return null;
    }
    if (entry instanceof final MappingLevels.Level level) {
      return level;
    }
    // Absent, or a plain value from an earlier mapping. A plain value means that mapping already
    // replaced whatever this path resolved to, so the re-created level must not fall through to it.
    final var replacedExistingValue = entry != null;
    final var scopeValue = scopeValueResolver.apply(pathPrefix);
    if (scopeValue == null || MappingLevels.isNil(scopeValue)) {
      final var fresh = MappingLevels.Level.fresh(replacedExistingValue);
      parent.put(key, fresh);
      return fresh;
    }
    if (!MsgPackCodes.isMap(scopeValue.getByte(0))) {
      parent.put(key, POISONED);
      return null;
    }
    final var seeded =
        new MappingLevels.Level(
            MappingLevels.deserializeTopLevel(scopeValue), replacedExistingValue);
    parent.put(key, seeded);
    return seeded;
  }
}
