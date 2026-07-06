/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import java.util.List;
import java.util.Map;

public class ArchiveBatch {

  private final List<Object> ids;
  private final String finishDate;
  private final Map<Integer, Long> totalPendingByPartition;

  public ArchiveBatch(final String finishDate, final List<Object> ids) {
    this(finishDate, ids, Map.of());
  }

  public ArchiveBatch(
      final String finishDate,
      final List<Object> ids,
      final Map<Integer, Long> totalPendingByPartition) {
    this.ids = ids;
    this.totalPendingByPartition = totalPendingByPartition;
    this.finishDate = finishDate;
  }

  public Map<Integer, Long> getTotalPendingByPartition() {
    return totalPendingByPartition;
  }

  public String getFinishDate() {
    return finishDate;
  }

  public List<Object> getIds() {
    return ids;
  }

  @Override
  public String toString() {
    return "ArchiveBatch{"
        + "totalPendingByPartition="
        + totalPendingByPartition
        + ", finishDate='"
        + finishDate
        + '\''
        + ", ids="
        + ids
        + '}';
  }
}
