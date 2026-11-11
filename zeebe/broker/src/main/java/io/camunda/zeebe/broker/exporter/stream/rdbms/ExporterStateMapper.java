/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream.rdbms;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/** Statements of the exporter state table, defined in {@code ExporterStateMapper.xml}. */
public interface ExporterStateMapper {

  @Nullable ExporterStateRow selectExporterState(
      @Param("partitionId") int partitionId, @Param("exporterId") String exporterId);

  List<ExporterStateRow> selectExporterStates(@Param("partitionId") int partitionId);

  int updateExporterState(
      @Param("partitionId") int partitionId, @Param("row") ExporterStateRow row);

  void insertExporterState(
      @Param("partitionId") int partitionId, @Param("row") ExporterStateRow row);

  void deleteExporterState(
      @Param("partitionId") int partitionId, @Param("exporterId") String exporterId);

  int countExporterStates(@Param("partitionId") int partitionId);

  /** The state of one exporter of the partition. */
  record ExporterStateRow(
      String exporterId, long position, long metadataVersion, byte[] metadata) {}
}
