/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/**
 * MyBatis mapper of the partition state table, see {@code
 * db/mybatis/engine-state/PartitionStateMapper.xml}. Every statement is scoped to one partition.
 */
interface PartitionStateMapper {

  // CAM_RU_PARTITION_STATE

  @Nullable PartitionStateRow selectPartitionState(
      @Param("partitionId") int partitionId, @Param("key") String key);

  void insertPartitionState(
      @Param("partitionId") int partitionId, @Param("row") PartitionStateRow row);

  void updatePartitionState(
      @Param("partitionId") int partitionId, @Param("row") PartitionStateRow row);

  void deletePartitionState(@Param("partitionId") int partitionId, @Param("key") String key);

  /** A value of the partition's state, found by its key. */
  record PartitionStateRow(String key, @Nullable String value) {}
}
