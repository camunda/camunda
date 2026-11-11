/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.rdbms;

import io.camunda.zeebe.mailbox.api.leadership.PartitionLease;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/** Statements of the lease table, defined in {@code LeaseMapper.xml}. */
public interface LeaseMapper {

  int insertLease(
      @Param("partitionId") int partitionId,
      @Param("leaderId") String leaderId,
      @Param("zone") String zone);

  int takeOverLease(
      @Param("partitionId") int partitionId,
      @Param("leaderId") String leaderId,
      @Param("zone") String zone,
      @Param("observedTerm") long observedTerm,
      @Param("observedCounter") long observedCounter);

  int renewLeases(
      @Param("leaderId") String leaderId, @Param("terms") Collection<PartitionTerm> terms);

  /** The partitions that the leader holds in the given terms, as {@link #renewLeases} renews. */
  List<Integer> selectHeldPartitions(
      @Param("leaderId") String leaderId, @Param("terms") Collection<PartitionTerm> terms);

  int releaseLease(
      @Param("partitionId") int partitionId,
      @Param("leaderId") String leaderId,
      @Param("term") long term);

  @Nullable PartitionLease selectLease(@Param("partitionId") int partitionId);

  List<PartitionLease> selectLeases();

  /** The term in which a node leads a partition. */
  record PartitionTerm(int partitionId, long term) {}
}
