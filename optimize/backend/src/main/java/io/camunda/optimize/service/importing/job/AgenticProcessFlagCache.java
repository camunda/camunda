/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * JVM-wide cache of process definition IDs already marked as agenticProcess=true. The flag is
 * per-version (one ES doc per {@code processDefinitionId}), so the cache is keyed by the unique
 * definition ID, not the non-unique BPMN key. Shared across all agent instance import partitions to
 * reduce redundant per-batch flag flips across partitions. Under concurrency, a flip attempt may be
 * issued more than once, but the underlying ES/OS update script is idempotent and the flag is
 * monotonic (forward-only), so this remains safe regardless of which partition flips first.
 *
 * <p><b>Memory:</b> O(distinct agentic definition IDs imported since JVM start). The cache is
 * forward-only and never evicts; bounded in practice by the total number of agentic definition
 * versions deployed against the cluster (typically tens to low thousands). Cleared on JVM restart.
 */
@Component
public final class AgenticProcessFlagCache {

  private final Set<String> flippedIds = ConcurrentHashMap.newKeySet();

  /**
   * Returns the subset of the given IDs that have not yet been flipped this JVM lifetime. Null or
   * empty input returns an empty set; null elements in the input are silently dropped.
   */
  public Set<String> filterUnflipped(final Collection<String> definitionIds) {
    if (definitionIds == null || definitionIds.isEmpty()) {
      return Set.of();
    }
    return definitionIds.stream()
        .filter(Objects::nonNull)
        .filter(id -> !flippedIds.contains(id))
        .collect(Collectors.toSet());
  }

  /**
   * Records the given IDs as flipped. Idempotent; safe to call with overlapping sets. Null or empty
   * input is a no-op; null elements in the input are silently dropped (the backing {@link
   * ConcurrentHashMap#newKeySet()} rejects nulls).
   */
  public void markFlipped(final Collection<String> definitionIds) {
    if (definitionIds == null || definitionIds.isEmpty()) {
      return;
    }
    definitionIds.stream().filter(Objects::nonNull).forEach(flippedIds::add);
  }
}
