/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.client.api;

import java.util.Collection;
import java.util.Collections;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Merges listings that were paged independently by several sources, such as the partitions of a
 * physical tenant or the physical tenants of a cluster, into one page grouped by backup id.
 *
 * <p>Every source received the same {@code before} cursor and {@code limit} and answered with its
 * newest backups below the cursor. A backup id may only be reported once every source has been
 * enumerated past it. Otherwise a source that simply has not reached the id yet would be reported
 * as not holding the backup, and the backup as incomplete.
 */
public final class PagedListing {

  private PagedListing() {}

  /**
   * Returns the smallest backup id that every source is known to have enumerated past. Ids at or
   * above the bound can be reported. Ids below it are dropped from this page and returned again by
   * the next page, because a source that filled its page may still hold statuses for them.
   *
   * <p>A source is full when it returned exactly {@code limit} distinct ids, so its smallest id
   * bounds the page. A source that returned fewer ids is exhausted. A source that returned more ids
   * ignored the limit, which a broker before the paged protocol does, and is exhausted as well.
   *
   * @param distinctIdsPerSource the distinct backup ids each source returned
   * @param limit the page size that was requested from every source, or empty when the sources were
   *     asked for everything
   * @return the bound, or {@link Long#MIN_VALUE} when no source can hold more ids
   */
  public static long safeBound(
      final Collection<? extends Set<Long>> distinctIdsPerSource, final OptionalInt limit) {
    if (limit.isEmpty()) {
      return Long.MIN_VALUE;
    }
    return distinctIdsPerSource.stream()
        .filter(ids -> ids.size() == limit.getAsInt())
        .mapToLong(Collections::min)
        .max()
        .orElse(Long.MIN_VALUE);
  }
}
