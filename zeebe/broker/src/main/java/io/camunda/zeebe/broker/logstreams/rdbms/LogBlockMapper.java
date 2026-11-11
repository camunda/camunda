/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.rdbms;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/**
 * MyBatis mapper for the RDBMS log tables; the statements are in {@code
 * db/mybatis/log-storage/LogBlockMapper.xml}.
 */
public interface LogBlockMapper {

  /** Raises the partition's term to {@code term} if it is lower; returns the rows updated. */
  int raiseTerm(@Param("partitionId") int partitionId, @Param("term") long term);

  @Nullable Long selectTerm(@Param("partitionId") int partitionId);

  /** Like {@link #selectTerm}, but locks the partition's row until the transaction ends. */
  @Nullable Long selectTermForUpdate(@Param("partitionId") int partitionId);

  void insertTerm(@Param("partitionId") int partitionId, @Param("term") long term);

  void insertBlock(
      @Param("partitionId") int partitionId,
      @Param("lowPosition") long lowPosition,
      @Param("highPosition") long highPosition,
      @Param("term") long term,
      @Param("data") byte[] data);

  List<LogBlock> selectBlocksAtOrBefore(
      @Param("partitionId") int partitionId,
      @Param("position") long position,
      @Param("limit") int limit);

  List<LogBlock> selectBlocksAfter(
      @Param("partitionId") int partitionId,
      @Param("lowPosition") long lowPosition,
      @Param("limit") int limit);
}
