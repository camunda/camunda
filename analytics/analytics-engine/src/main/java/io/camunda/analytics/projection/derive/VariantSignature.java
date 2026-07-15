/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.derive;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The variant signature of one process instance: an order-insensitive commutative fold over its
 * distinct executed elements with <em>bucketed</em> loop counts — {@code signature ⊕=
 * hash64(elementId, bucket)} where the bucket collapses activation counts to {@code 1 / 2–3 / 4+}.
 *
 * <p>Why this shape:
 *
 * <ul>
 *   <li><b>Order-insensitive:</b> XOR is commutative and associative, so parallel branches (whose
 *       activation interleaving is nondeterministic) and replay reorderings within an instance fold
 *       to the same signature — every shipping instance is ONE variant regardless of whether
 *       dispatch or notify activated first.
 *   <li><b>Bucketed loop counts:</b> a retry loop that ran twice vs. three times is operationally
 *       the same story ("needed a few passes"), while 1 vs. 2 is a different path; buckets keep the
 *       variant space small without erasing the loop signal.
 *   <li><b>Deterministic:</b> FNV-1a over the element id's UTF-8 bytes plus a final avalanche mix —
 *       stable across JVMs, replays and partitions; no seed, no platform hash.
 * </ul>
 *
 * <p>The canonical element list is a display companion, not the identity: distinct element ids
 * sorted lexicographically, loop buckets annotated ({@code ×2-3} / {@code ×4+}), joined with {@code
 * ", "} and capped at {@value #MAX_ELEMENTS_LENGTH} chars with a documented truncation marker
 * ({@code … (+N more)}). Sorting makes it as order-insensitive as the hash.
 */
public final class VariantSignature {

  /** Cap for the canonical element list; beyond it the marker names the omitted count. */
  static final int MAX_ELEMENTS_LENGTH = 512;

  private static final long FNV_OFFSET = 0xcbf29ce484222325L;
  private static final long FNV_PRIME = 0x100000001b3L;

  private final List<ElementCount> elements = new ArrayList<>();

  /** One distinct executed element with its raw activation count. */
  private record ElementCount(String elementId, long count) {}

  /** The finished signature: the commutative hash and the canonical (bounded) element list. */
  public record Variant(long hash, String elements) {}

  /** Adds one distinct executed element; callers pass each element id at most once. */
  public VariantSignature add(final String elementId, final long activationCount) {
    elements.add(new ElementCount(elementId, activationCount));
    return this;
  }

  /** Folds the added elements, or returns {@code null} when none were added (no variant). */
  public Variant build() {
    if (elements.isEmpty()) {
      return null;
    }
    long signature = 0L;
    for (final ElementCount element : elements) {
      signature ^= hash64(element.elementId(), bucket(element.count()));
    }
    return new Variant(signature, canonicalElements());
  }

  /** Activation-count bucket: {@code 1 → 1}, {@code 2–3 → 2}, {@code 4+ → 3}. */
  static int bucket(final long activationCount) {
    if (activationCount <= 1) {
      return 1;
    }
    return activationCount <= 3 ? 2 : 3;
  }

  /** FNV-1a over the element id's UTF-8 bytes, the bucket folded in, then an avalanche mix. */
  static long hash64(final String elementId, final int bucket) {
    long hash = FNV_OFFSET;
    for (final byte b : elementId.getBytes(StandardCharsets.UTF_8)) {
      hash ^= b & 0xFFL;
      hash *= FNV_PRIME;
    }
    hash ^= bucket;
    hash *= FNV_PRIME;
    // Final avalanche (splitmix64 tail) so single-bit inputs spread over the word — XOR-combining
    // many raw FNV values would otherwise concentrate entropy in the low bits.
    hash ^= hash >>> 30;
    hash *= 0xbf58476d1ce4e5b9L;
    hash ^= hash >>> 27;
    hash *= 0x94d049bb133111ebL;
    hash ^= hash >>> 31;
    return hash;
  }

  private String canonicalElements() {
    elements.sort(Comparator.comparing(ElementCount::elementId));
    final StringBuilder out = new StringBuilder();
    int included = 0;
    for (final ElementCount element : elements) {
      final String rendered = render(element);
      final int separator = included == 0 ? 0 : 2;
      // Reserve room for a worst-case truncation marker so it always fits within the cap.
      if (out.length() + separator + rendered.length() > MAX_ELEMENTS_LENGTH - 16) {
        out.append(" … (+").append(elements.size() - included).append(" more)");
        return out.toString();
      }
      if (included > 0) {
        out.append(", ");
      }
      out.append(rendered);
      included++;
    }
    return out.toString();
  }

  private static String render(final ElementCount element) {
    return switch (bucket(element.count())) {
      case 2 -> element.elementId() + "×2-3";
      case 3 -> element.elementId() + "×4+";
      default -> element.elementId();
    };
  }
}
